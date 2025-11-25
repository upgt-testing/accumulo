/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.minicluster.upgrade;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.accumulo.minicluster.ServerType;
import org.apache.accumulo.minicluster.upgrade.AccumuloVersionRegistry.AccumuloDistribution;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloConfigImpl;
import org.apache.accumulo.start.Main;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Executes Accumulo server processes with version-specific classpaths. This class adapts the
 * process execution logic from MiniAccumuloClusterImpl to support running different Accumulo
 * versions by building custom classpaths from specific distributions.
 *
 * @since 2.1.2
 */
public class ProcessExecutor {
  private static final Logger log = LoggerFactory.getLogger(ProcessExecutor.class);

  private final MiniAccumuloConfigImpl config;
  private final AccumuloVersionRegistry versionRegistry;
  private final List<Process> cleanup = new ArrayList<>();

  /**
   * Creates a new ProcessExecutor.
   *
   * @param config The cluster configuration
   * @param versionRegistry The version registry for building classpaths
   */
  public ProcessExecutor(MiniAccumuloConfigImpl config, AccumuloVersionRegistry versionRegistry) {
    this.config = config;
    this.versionRegistry = versionRegistry;
  }

  /**
   * Executes a server process with a specific Accumulo distribution.
   *
   * @param clazz The main class to execute
   * @param serverType The type of server being started
   * @param distribution The Accumulo distribution to use
   * @param extraJvmOpts Additional JVM options
   * @param args Command-line arguments
   * @return ProcessInfo containing the process and output file
   */
  @SuppressFBWarnings(value = {"COMMAND_INJECTION", "PATH_TRAVERSAL_IN"},
      justification = "mini runs in the same security context as user providing the args")
  public ProcessInfo exec(Class<?> clazz, ServerType serverType, AccumuloDistribution distribution,
      List<String> extraJvmOpts, String... args) throws IOException {
    String javaHome = System.getProperty("java.home");
    String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

    // Build version-specific classpath
    String classpath = buildVersionSpecificClasspath(distribution, serverType);

    Stream<String> basicArgs = Stream.of(javaBin, "-Dproc=" + clazz.getSimpleName());
    Stream<String> jvmArgs = extraJvmOpts != null ? extraJvmOpts.stream() : Stream.<String>empty();
    Stream<String> propsArgs = config.getSystemProperties().entrySet().stream()
        .map(e -> String.format("-D%s=%s", e.getKey(), e.getValue()));

    // @formatter:off
    Stream<String> hardcodedArgs = Stream.of(
        "-Dapple.awt.UIElement=true",
        "-Djava.net.preferIPv4Stack=true",
        "-XX:+PerfDisableSharedMem",
        "-XX:+AlwaysPreTouch",
        Main.class.getName(), clazz.getName());
    // @formatter:on

    // Concatenate all the args sources into a single list of args
    List<String> argList = Stream.of(basicArgs, jvmArgs, propsArgs, hardcodedArgs, Stream.of(args))
        .flatMap(Function.identity()).collect(toList());

    ProcessBuilder builder = new ProcessBuilder(argList);

    // Set version-specific classpath
    builder.environment().put("CLASSPATH", classpath);
    builder.environment().put("ACCUMULO_HOME", distribution.getAccumuloHome().getAbsolutePath());
    builder.environment().put("ACCUMULO_LOG_DIR", config.getLogDir().getAbsolutePath());
    builder.environment().put("ACCUMULO_CLIENT_CONF_PATH",
        config.getClientConfFile().getAbsolutePath());

    String ldLibraryPath = Joiner.on(File.pathSeparator).join(config.getNativeLibPaths());
    builder.environment().put("LD_LIBRARY_PATH", ldLibraryPath);
    builder.environment().put("DYLD_LIBRARY_PATH", ldLibraryPath);

    // Forward environment variables
    String env = System.getenv("HADOOP_HOME");
    if (env != null) {
      builder.environment().put("HADOOP_HOME", env);
    }
    env = System.getenv("ZOOKEEPER_HOME");
    if (env != null) {
      builder.environment().put("ZOOKEEPER_HOME", env);
    }

    builder.environment().put("ACCUMULO_CONF_DIR", config.getConfDir().getAbsolutePath());
    if (config.getHadoopConfDir() != null) {
      builder.environment().put("HADOOP_CONF_DIR", config.getHadoopConfDir().getAbsolutePath());
    }

    log.debug("Starting process with distribution {} for {}: class={}, args={}",
        distribution.getVersion(), serverType, clazz.getSimpleName(), argList);

    int hashcode = builder.hashCode();

    File stdOut = new File(config.getLogDir(), clazz.getSimpleName() + "_" + hashcode + ".out");
    File stdErr = new File(config.getLogDir(), clazz.getSimpleName() + "_" + hashcode + ".err");

    Process process = builder.redirectError(stdErr).redirectOutput(stdOut).start();

    cleanup.add(process);

    log.info("Started {} process (PID: {}) with distribution {}", serverType, process.pid(),
        distribution.getVersion());

    return new ProcessInfo(process, stdOut);
  }

  /**
   * Builds a version-specific classpath for the given distribution and server type.
   *
   * @param distribution The Accumulo distribution
   * @param serverType The server type
   * @return Classpath string
   */
  private String buildVersionSpecificClasspath(AccumuloDistribution distribution,
      ServerType serverType) {
    StringBuilder classpathBuilder = new StringBuilder();

    // 1. Configuration directory (from current cluster config)
    classpathBuilder.append(config.getConfDir().getAbsolutePath());

    // 2. Hadoop configuration directory if present
    if (config.getHadoopConfDir() != null) {
      classpathBuilder.append(File.pathSeparator)
          .append(config.getHadoopConfDir().getAbsolutePath());
    }

    // 3. Version-specific Accumulo jars from the distribution
    List<String> distributionClasspath = versionRegistry.buildClasspath(distribution, serverType);
    for (String entry : distributionClasspath) {
      classpathBuilder.append(File.pathSeparator).append(entry);
    }

    // 4. Additional classpath items or current JVM classpath
    // Note: We still need the current JVM classpath for shared dependencies like Hadoop, ZK, etc.
    if (config.getClasspathItems() == null) {
      String javaClassPath = System.getProperty("java.class.path");
      if (javaClassPath != null) {
        classpathBuilder.append(File.pathSeparator).append(javaClassPath);
      }
    } else {
      for (String s : config.getClasspathItems()) {
        classpathBuilder.append(File.pathSeparator).append(s);
      }
    }

    return classpathBuilder.toString();
  }

  /**
   * Stops a process gracefully, then forcibly if needed.
   *
   * @param process The process to stop
   */
  public void stopProcess(Process process) throws InterruptedException {
    if (process == null || !process.isAlive()) {
      return;
    }

    log.info("Stopping process (PID: {})", process.pid());

    // Try graceful shutdown first
    process.destroy();

    // Wait up to 30 seconds for graceful shutdown
    boolean exited = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

    if (!exited) {
      log.warn("Process did not exit gracefully, forcing shutdown");
      process.destroyForcibly();
      process.waitFor();
    }

    cleanup.remove(process);
    log.info("Process stopped");
  }

  /**
   * Stops all processes managed by this executor.
   */
  public void stopAllProcesses() throws InterruptedException {
    List<Process> processesToStop = new ArrayList<>(cleanup);
    for (Process process : processesToStop) {
      stopProcess(process);
    }
  }

  /**
   * Returns the list of cleanup processes.
   */
  public List<Process> getCleanupProcesses() {
    return new ArrayList<>(cleanup);
  }

  /**
   * Holds information about a started process.
   */
  public static class ProcessInfo {
    private final Process process;
    private final File stdOut;

    public ProcessInfo(Process process, File stdOut) {
      this.process = process;
      this.stdOut = stdOut;
    }

    public Process getProcess() {
      return process;
    }

    public File getStdOut() {
      return stdOut;
    }

    public String readStdOut() {
      try (InputStream in = new FileInputStream(stdOut)) {
        return IOUtils.toString(in, UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
