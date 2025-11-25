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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.accumulo.minicluster.ServerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Accumulo distribution versions and builds version-specific classpaths for server
 * processes. This enables running different Accumulo versions within the same mini cluster for
 * upgrade testing.
 *
 * @since 2.1.2
 */
public class AccumuloVersionRegistry {
  private static final Logger log = LoggerFactory.getLogger(AccumuloVersionRegistry.class);

  private AccumuloDistribution startDistribution;
  private AccumuloDistribution upgradeDistribution;
  private final Map<String,AccumuloDistribution> distributions = new HashMap<>();

  /**
   * Registers the starting Accumulo distribution.
   *
   * @param accumuloHome Path to the Accumulo installation directory
   */
  public void registerStartDistribution(String accumuloHome) throws IOException {
    log.info("Registering start distribution: {}", accumuloHome);
    this.startDistribution = loadDistribution(accumuloHome);
    distributions.put("start", startDistribution);
  }

  /**
   * Registers the upgrade target Accumulo distribution.
   *
   * @param accumuloHome Path to the Accumulo installation directory
   */
  public void registerUpgradeDistribution(String accumuloHome) throws IOException {
    log.info("Registering upgrade distribution: {}", accumuloHome);
    this.upgradeDistribution = loadDistribution(accumuloHome);
    distributions.put("upgrade", upgradeDistribution);
  }

  /**
   * Returns the starting distribution.
   */
  public AccumuloDistribution getStartDistribution() {
    return startDistribution;
  }

  /**
   * Returns the upgrade distribution.
   */
  public AccumuloDistribution getUpgradeDistribution() {
    return upgradeDistribution;
  }

  /**
   * Builds a classpath for a specific server type using the specified distribution.
   *
   * @param distribution The Accumulo distribution to use
   * @param serverType The type of server process
   * @return List of classpath entries
   */
  public List<String> buildClasspath(AccumuloDistribution distribution, ServerType serverType) {
    List<String> classpath = new ArrayList<>();

    // Add Accumulo core jars from lib/
    classpath.addAll(distribution.getCoreJarPaths());

    // Add Accumulo extensions from lib/ext/ if present
    classpath.addAll(distribution.getExtensionJarPaths());

    log.debug("Built classpath for {} with {} entries", serverType, classpath.size());
    return classpath;
  }

  /**
   * Loads an Accumulo distribution from the specified home directory.
   *
   * @param accumuloHome Path to Accumulo installation
   * @return Loaded distribution
   */
  private AccumuloDistribution loadDistribution(String accumuloHome) throws IOException {
    File homeDir = new File(accumuloHome);
    if (!homeDir.exists() || !homeDir.isDirectory()) {
      throw new IOException("Accumulo home does not exist or is not a directory: " + accumuloHome);
    }

    // Detect version from build.properties if available
    String version = detectVersion(homeDir);

    // Find core jars in lib/
    File libDir = new File(homeDir, "lib");
    List<String> coreJars = new ArrayList<>();
    if (libDir.exists() && libDir.isDirectory()) {
      File[] jarFiles = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
      if (jarFiles != null) {
        for (File jar : jarFiles) {
          coreJars.add(jar.getAbsolutePath());
        }
      }
    }

    if (coreJars.isEmpty()) {
      log.warn("No JAR files found in {}/lib - distribution may be incomplete", accumuloHome);
    }

    // Find extension jars in lib/ext/ if present
    File libExtDir = new File(homeDir, "lib/ext");
    List<String> extJars = new ArrayList<>();
    if (libExtDir.exists() && libExtDir.isDirectory()) {
      File[] jarFiles = libExtDir.listFiles((dir, name) -> name.endsWith(".jar"));
      if (jarFiles != null) {
        for (File jar : jarFiles) {
          extJars.add(jar.getAbsolutePath());
        }
      }
    }

    log.info("Loaded distribution {} from {} with {} core jars and {} extension jars", version,
        accumuloHome, coreJars.size(), extJars.size());

    return new AccumuloDistribution(version, homeDir, coreJars, extJars);
  }

  /**
   * Attempts to detect the Accumulo version from build.properties.
   *
   * @param accumuloHome The Accumulo home directory
   * @return Version string or "unknown"
   */
  private String detectVersion(File accumuloHome) {
    // Try to read version from build.properties in conf/
    File buildProps = new File(new File(accumuloHome, "conf"), "build.properties");
    if (buildProps.exists()) {
      try (FileInputStream fis = new FileInputStream(buildProps)) {
        Properties props = new Properties();
        props.load(fis);
        String version = props.getProperty("version");
        if (version != null) {
          return version;
        }
      } catch (IOException e) {
        log.debug("Could not read build.properties", e);
      }
    }

    // Fallback: try to parse from directory name
    String dirName = accumuloHome.getName();
    if (dirName.startsWith("accumulo-")) {
      return dirName.substring("accumulo-".length());
    }

    return "unknown";
  }

  /**
   * Represents an Accumulo distribution with its version and JAR files.
   */
  public static class AccumuloDistribution {
    private final String version;
    private final File accumuloHome;
    private final List<String> coreJarPaths;
    private final List<String> extensionJarPaths;

    public AccumuloDistribution(String version, File accumuloHome, List<String> coreJarPaths,
        List<String> extensionJarPaths) {
      this.version = version;
      this.accumuloHome = accumuloHome;
      this.coreJarPaths = new ArrayList<>(coreJarPaths);
      this.extensionJarPaths = new ArrayList<>(extensionJarPaths);
    }

    public String getVersion() {
      return version;
    }

    public File getAccumuloHome() {
      return accumuloHome;
    }

    public List<String> getCoreJarPaths() {
      return new ArrayList<>(coreJarPaths);
    }

    public List<String> getExtensionJarPaths() {
      return new ArrayList<>(extensionJarPaths);
    }

    @Override
    public String toString() {
      return "AccumuloDistribution{version=" + version + ", home=" + accumuloHome + "}";
    }
  }
}
