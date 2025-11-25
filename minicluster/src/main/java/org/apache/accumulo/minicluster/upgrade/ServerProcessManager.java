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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.accumulo.minicluster.ServerType;
import org.apache.accumulo.minicluster.upgrade.AccumuloVersionRegistry.AccumuloDistribution;
import org.apache.accumulo.minicluster.upgrade.ProcessExecutor.ProcessInfo;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloConfigImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages server processes with version-specific configurations. This class provides a bridge
 * between the ProcessExecutor (which handles version-specific process execution) and the actual
 * server lifecycle management.
 *
 * <p>
 * Key responsibilities: - Starting servers with specific Accumulo distributions - Tracking running
 * processes and their versions - Managing server configuration and arguments - Coordinating with
 * PortManager for consistent addressing
 *
 * @since 2.1.2
 */
public class ServerProcessManager {
  private static final Logger log = LoggerFactory.getLogger(ServerProcessManager.class);

  private final MiniAccumuloConfigImpl config;
  private final ProcessExecutor processExecutor;
  private final AccumuloVersionRegistry versionRegistry;
  private final PortManager portManager;

  // Track server metadata
  private final Map<String,ServerProcessInfo> serverProcesses = new HashMap<>();

  /**
   * Creates a new server process manager.
   *
   * @param config Cluster configuration
   * @param processExecutor Process executor for version-specific execution
   * @param versionRegistry Version registry for distributions
   * @param portManager Port manager for allocating ports
   */
  public ServerProcessManager(MiniAccumuloConfigImpl config, ProcessExecutor processExecutor,
      AccumuloVersionRegistry versionRegistry, PortManager portManager) {
    this.config = config;
    this.processExecutor = processExecutor;
    this.versionRegistry = versionRegistry;
    this.portManager = portManager;
  }

  /**
   * Starts a server with the specified distribution.
   *
   * @param serverType Type of server to start
   * @param distribution Accumulo distribution to use
   * @param serverIndex Index of this server instance (for TabletServers, ScanServers, etc.)
   * @param configOverrides Configuration overrides for this server
   * @return ProcessInfo for the started server
   */
  public ProcessInfo startServer(ServerType serverType, AccumuloDistribution distribution,
      int serverIndex, Map<String,String> configOverrides) throws IOException {

    String serverId = buildServerId(serverType, serverIndex);
    log.info("Starting {} (index {}) with distribution {}", serverType, serverIndex,
        distribution.getVersion());

    // Allocate ports for this server (or restore if restarting)
    Map<String,Integer> ports = allocatePortsForServer(serverId, serverType);

    // Build JVM options
    List<String> jvmOpts = buildJvmOptions(serverType, configOverrides);

    // Build command-line arguments
    String[] args = buildServerArgs(serverType, serverIndex, configOverrides);

    // Determine server main class
    Class<?> serverClass = getServerClass(serverType);

    // Start the process
    ProcessInfo processInfo =
        processExecutor.exec(serverClass, serverType, distribution, jvmOpts, args);

    // Track the process
    ServerProcessInfo spi = new ServerProcessInfo(processInfo, distribution.getVersion(),
        serverType, serverIndex, ports);
    serverProcesses.put(serverId, spi);

    log.info("Started {} (index {}) with PID {}", serverType, serverIndex,
        processInfo.getProcess().pid());

    return processInfo;
  }

  /**
   * Stops a server process.
   *
   * @param serverType Type of server
   * @param serverIndex Index of server instance
   */
  public void stopServer(ServerType serverType, int serverIndex)
      throws IOException, InterruptedException {
    String serverId = buildServerId(serverType, serverIndex);
    ServerProcessInfo spi = serverProcesses.get(serverId);

    if (spi != null && spi.processInfo.getProcess().isAlive()) {
      log.info("Stopping {} (index {})", serverType, serverIndex);
      processExecutor.stopProcess(spi.processInfo.getProcess());
      // Don't remove from map - we want to preserve version/port info for restarts
    }
  }

  /**
   * Restarts a server with a new distribution (for version upgrades).
   *
   * @param serverType Type of server
   * @param serverIndex Index of server instance
   * @param newDistribution New Accumulo distribution
   */
  public ProcessInfo restartServerWithNewVersion(ServerType serverType, int serverIndex,
      AccumuloDistribution newDistribution) throws IOException, InterruptedException {

    log.info("Restarting {} (index {}) with new distribution {}", serverType, serverIndex,
        newDistribution.getVersion());

    // Stop the current process
    stopServer(serverType, serverIndex);

    // Start with new distribution (ports will be restored automatically)
    return startServer(serverType, newDistribution, serverIndex, Collections.emptyMap());
  }

  /**
   * Allocates or restores ports for a server.
   */
  private Map<String,Integer> allocatePortsForServer(String serverId, ServerType serverType)
      throws IOException {
    Map<String,Integer> ports = new HashMap<>();

    // Different server types need different ports
    switch (serverType) {
      case MANAGER:
      case MASTER:
        ports.put("clientPort", portManager.allocatePort(serverId, "clientPort"));
        ports.put("thriftPort", portManager.allocatePort(serverId, "thriftPort"));
        break;
      case TABLET_SERVER:
        ports.put("clientPort", portManager.allocatePort(serverId, "clientPort"));
        ports.put("thriftPort", portManager.allocatePort(serverId, "thriftPort"));
        break;
      case SCAN_SERVER:
        ports.put("clientPort", portManager.allocatePort(serverId, "clientPort"));
        break;
      case GARBAGE_COLLECTOR:
      case MONITOR:
      case COMPACTOR:
      case COMPACTION_COORDINATOR:
        ports.put("port", portManager.allocatePort(serverId, "port"));
        break;
      default:
        // No special ports needed
        break;
    }

    return ports;
  }

  /**
   * Builds a unique server ID for tracking.
   */
  private String buildServerId(ServerType serverType, int serverIndex) {
    if (serverIndex < 0) {
      // Single-instance servers (Manager, GC, Monitor, etc.)
      return serverType.name().toLowerCase();
    } else {
      // Multi-instance servers (TabletServer, ScanServer, Compactor)
      return serverType.name().toLowerCase() + "." + serverIndex;
    }
  }

  /**
   * Returns the main class for a server type.
   */
  private Class<?> getServerClass(ServerType serverType) {
    // Note: These classes are loaded from the target distribution's classpath
    // The class references here are just for metadata - actual execution uses
    // the version-specific classes from ProcessExecutor
    switch (serverType) {
      case MANAGER:
      case MASTER:
        return org.apache.accumulo.manager.Manager.class;
      case TABLET_SERVER:
        return org.apache.accumulo.tserver.TabletServer.class;
      case GARBAGE_COLLECTOR:
        return org.apache.accumulo.gc.SimpleGarbageCollector.class;
      case SCAN_SERVER:
        return org.apache.accumulo.tserver.ScanServer.class;
      case COMPACTOR:
        return org.apache.accumulo.compactor.Compactor.class;
      case COMPACTION_COORDINATOR:
        return org.apache.accumulo.coordinator.CompactionCoordinator.class;
      case MONITOR:
        return org.apache.accumulo.monitor.Monitor.class;
      case ZOOKEEPER:
        return org.apache.zookeeper.server.ZooKeeperServerMain.class;
      default:
        throw new IllegalArgumentException("Unknown server type: " + serverType);
    }
  }

  /**
   * Builds JVM options for a server.
   */
  private List<String> buildJvmOptions(ServerType serverType, Map<String,String> configOverrides) {
    List<String> jvmOpts = new ArrayList<>();

    // Add memory settings
    jvmOpts.add("-Xmx" + config.getDefaultMemory());

    // Add config overrides as system properties
    for (Map.Entry<String,String> entry : configOverrides.entrySet()) {
      jvmOpts.add("-D" + entry.getKey() + "=" + entry.getValue());
    }

    return jvmOpts;
  }

  /**
   * Builds command-line arguments for a server.
   */
  private String[] buildServerArgs(ServerType serverType, int serverIndex,
      Map<String,String> configOverrides) {
    List<String> args = new ArrayList<>();

    // Add server-specific arguments
    switch (serverType) {
      case COMPACTOR:
        // Compactors need queue name
        if (configOverrides.containsKey("QUEUE_NAME")) {
          args.add("-q");
          args.add(configOverrides.get("QUEUE_NAME"));
        }
        break;
      case ZOOKEEPER:
        // ZooKeeper needs config file
        // Note: Need access to zoo cfg file - may need to pass as parameter
        // args.add(config.getZooKeeperDir() + "/zoo.cfg");
        break;
      default:
        // Most servers don't need special args
        break;
    }

    return args.toArray(new String[0]);
  }

  /**
   * Returns information about a running server.
   */
  public ServerProcessInfo getServerInfo(ServerType serverType, int serverIndex) {
    String serverId = buildServerId(serverType, serverIndex);
    return serverProcesses.get(serverId);
  }

  /**
   * Returns all server processes.
   */
  public Map<String,ServerProcessInfo> getAllServers() {
    return new HashMap<>(serverProcesses);
  }

  /**
   * Holds metadata about a server process.
   */
  public static class ServerProcessInfo {
    private final ProcessInfo processInfo;
    private final String version;
    private final ServerType serverType;
    private final int serverIndex;
    private final Map<String,Integer> ports;

    public ServerProcessInfo(ProcessInfo processInfo, String version, ServerType serverType,
        int serverIndex, Map<String,Integer> ports) {
      this.processInfo = processInfo;
      this.version = version;
      this.serverType = serverType;
      this.serverIndex = serverIndex;
      this.ports = new HashMap<>(ports);
    }

    public ProcessInfo getProcessInfo() {
      return processInfo;
    }

    public String getVersion() {
      return version;
    }

    public ServerType getServerType() {
      return serverType;
    }

    public int getServerIndex() {
      return serverIndex;
    }

    public Map<String,Integer> getPorts() {
      return new HashMap<>(ports);
    }

    public Process getProcess() {
      return processInfo.getProcess();
    }

    public File getStdOut() {
      return processInfo.getStdOut();
    }
  }
}
