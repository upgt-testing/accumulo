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
package org.apache.accumulo.restarttest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.util.HostAndPort;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.apache.accumulo.minicluster.ServerType;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloConfigImpl;
import org.apache.accumulo.miniclusterImpl.ProcessReference;
import org.apache.accumulo.server.ServerContext;
import org.apache.accumulo.server.util.Admin;
import org.restarttest.core.ClusterAdapter;
import org.restarttest.core.RestartMode;
import org.restarttest.health.CompositeHealthCheck;
import org.restarttest.health.HealthCheck;
import org.restarttest.state.StateCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.accumulo.restarttest.health.AccumuloClusterBalancedCheck;
import org.apache.accumulo.restarttest.health.AccumuloManagerActiveCheck;
import org.apache.accumulo.restarttest.health.AccumuloScanServersRegisteredCheck;
import org.apache.accumulo.restarttest.health.AccumuloTabletServersRegisteredCheck;
import org.apache.accumulo.restarttest.health.AccumuloTabletsAssignedCheck;

/**
 * Cluster adapter for Apache Accumulo's MiniAccumuloCluster.
 * Enables restart testing for Accumulo deployments.
 */
public class AccumuloClusterAdapter implements ClusterAdapter<MiniAccumuloCluster> {

  private static final Logger log = LoggerFactory.getLogger(AccumuloClusterAdapter.class);

  private final AccumuloStateCapture stateCapture;
  private final CompositeHealthCheck<MiniAccumuloCluster> healthCheck;

  public AccumuloClusterAdapter() {
    this.stateCapture = new AccumuloStateCapture();
    this.healthCheck = new CompositeHealthCheck<>("accumulo-health");

    // Add health checks
    // this.healthCheck.addCheck(new AccumuloManagerActiveCheck());
    // this.healthCheck.addCheck(new AccumuloTabletServersRegisteredCheck());
    // this.healthCheck.addCheck(new AccumuloTabletsAssignedCheck());
    // this.healthCheck.addCheck(new AccumuloClusterBalancedCheck());
    // this.healthCheck.addCheck(new AccumuloScanServersRegisteredCheck());
  }

  @Override
  public Class<MiniAccumuloCluster> getClusterType() {
    return MiniAccumuloCluster.class;
  }

  @Override
  public void restartNode(MiniAccumuloCluster cluster, String nodeRole,
                         int nodeIndex, RestartMode mode) throws Exception {
    ServerType serverType = normalizeRole(nodeRole);

    if (serverType == null) { // "all" role
      restartAllNodes(cluster, nodeRole, mode);
      return;
    }

    // Get process at index
    MiniAccumuloClusterImpl impl = getImpl(cluster);
    Collection<ProcessReference> processes = impl.getProcesses().get(serverType);
    if (processes == null || processes.isEmpty()) {
      throw new IllegalArgumentException("No processes found for role: " + nodeRole);
    }

    List<ProcessReference> processList = new ArrayList<>(processes);

    if (nodeIndex >= processList.size()) {
      throw new IllegalArgumentException(
          "Invalid index " + nodeIndex + " for role " + nodeRole +
          " (available: 0-" + (processList.size() - 1) + ")");
    }

    ProcessReference proc = processList.get(nodeIndex);

    log.info("Restarting {} at index {} with mode {}", nodeRole, nodeIndex, mode);

    // Kill based on mode
    switch (mode) {
      case GRACEFUL:
        gracefulShutdownProcess(impl, serverType, nodeIndex, proc);
        break;
      case CRASH:
        killProcessForcibly(proc);
        break;
      case DELAYED_CRASH:
        killProcessForcibly(proc);
        Thread.sleep(500); // Allow partial state propagation
        break;
      default:
        throw new IllegalArgumentException("Unknown restart mode: " + mode);
    }

    // Start replacement process
    log.info("Starting replacement {} process", serverType);
    impl.getClusterControl().start(serverType, null);

    // Wait for cluster to stabilize
    Thread.sleep(100);
  }

  @Override
  public void restartAllNodes(MiniAccumuloCluster cluster, String nodeRole,
                             RestartMode mode) throws Exception {
    ServerType serverType = normalizeRole(nodeRole);

    if (serverType == null) { // "all" role
      log.info("Restarting all server types with mode {}", mode);
      restartAllServerTypes(cluster, mode);
      return;
    }

    // Get all processes of this type
    MiniAccumuloClusterImpl impl = getImpl(cluster);
    Collection<ProcessReference> processes = impl.getProcesses().get(serverType);
    if (processes == null || processes.isEmpty()) {
      log.warn("No processes found for role: {}", nodeRole);
      return;
    }

    List<ProcessReference> processList = new ArrayList<>(processes);
    int count = processList.size();

    log.info("Restarting all {} {} processes with mode {}", count, nodeRole, mode);

    // Restart each process (always restart index 0 since list shrinks after each kill)
    for (int i = 0; i < count; i++) {
      restartNode(cluster, nodeRole, 0, mode);
    }
  }

  @Override
  public void waitActive(MiniAccumuloCluster cluster) throws Exception {
    log.info("Waiting for cluster to become active");

    // Create a client to check cluster state
    AccumuloClient client = cluster.createAccumuloClient("root",
        new PasswordToken(cluster.getConfig().getRootPassword()));

    try {
      // Wait for manager to be available
      waitForManager(client, 60000);

      // Wait for expected tablet servers to register
      waitForTabletServers(cluster, client, 60000);

      // Wait for cluster to balance
      log.info("Waiting for cluster to balance");
      client.instanceOperations().waitForBalance();

      log.info("Cluster is now active");
    } finally {
      client.close();
    }
  }

  @Override
  public StateCapture<MiniAccumuloCluster> getStateCapture() {
    return stateCapture;
  }

  @Override
  public HealthCheck<MiniAccumuloCluster> getHealthCheck() {
    return healthCheck;
  }

  @Override
  public int getNodeCount(MiniAccumuloCluster cluster, String nodeRole) throws Exception {
    ServerType serverType = normalizeRole(nodeRole);

    if (serverType == null) { // "all" role
      return getAllNodeCount(cluster);
    }

    MiniAccumuloClusterImpl impl = getImpl(cluster);
    Collection<ProcessReference> processes = impl.getProcesses().get(serverType);
    return processes != null ? processes.size() : 0;
  }

  // Helper methods

  /**
   * Get the MiniAccumuloClusterImpl from MiniAccumuloCluster using reflection.
   */
  private MiniAccumuloClusterImpl getImpl(MiniAccumuloCluster cluster) throws Exception {
    try {
      Field implField = MiniAccumuloCluster.class.getDeclaredField("impl");
      implField.setAccessible(true);
      return (MiniAccumuloClusterImpl) implField.get(cluster);
    } catch (Exception e) {
      throw new Exception("Failed to access MiniAccumuloCluster implementation", e);
    }
  }

  /**
   * Get the MiniAccumuloConfigImpl from MiniAccumuloCluster.
   */
  private MiniAccumuloConfigImpl getConfigImpl(MiniAccumuloCluster cluster) throws Exception {
    return getImpl(cluster).getConfig();
  }

  /**
   * Normalize generic role names to Accumulo ServerType.
   */
  private ServerType normalizeRole(String role) {
    String lower = role.toLowerCase().replace("_", "").replace("-", "");

    switch (lower) {
      case "master":
      case "manager":
        return ServerType.MANAGER;
      case "worker":
      case "tserver":
      case "tabletserver":
        return ServerType.TABLET_SERVER;
      case "gc":
      case "garbagecollector":
        return ServerType.GARBAGE_COLLECTOR;
      case "scanserver":
        return ServerType.SCAN_SERVER;
      case "compactor":
        return ServerType.COMPACTOR;
      case "all":
        return null; // Special case
      default:
        throw new IllegalArgumentException(
            "Unknown role: " + role +
            ". Supported: manager, tablet_server, garbage_collector, scan_server, compactor, all");
    }
  }

  /**
   * Kill process forcibly for crash simulation.
   * Uses SIGKILL to immediately terminate the process without allowing cleanup.
   */
  private void killProcessForcibly(ProcessReference proc) throws Exception {
    Process process = proc.getProcess();
    log.info("Forcibly killing process (SIGKILL): {}", process);
    process.destroyForcibly();
    // Wait briefly for process to be killed
    boolean terminated = process.waitFor(10, TimeUnit.SECONDS);
    if (!terminated) {
      log.warn("Process did not terminate within 10 seconds after destroyForcibly");
    }
  }

  /**
   * Perform graceful shutdown of a process using Accumulo's native graceful shutdown mechanism.
   * This signals the server via RPC to stop accepting new work, complete current tasks,
   * flush data, and cleanly release resources before terminating.
   */
  private void gracefulShutdownProcess(MiniAccumuloClusterImpl cluster, ServerType serverType,
                                       int nodeIndex, ProcessReference proc) throws Exception {
    try {
      // Get server address for the process at the given index
      HostAndPort serverAddress = getServerAddress(cluster, serverType, nodeIndex);

      if (serverAddress != null) {
        ServerContext context = cluster.getServerContext();
        log.info("Signaling graceful shutdown to {} at {}", serverType, serverAddress);
        Admin.signalGracefulShutdown(context, serverAddress);

        // Wait for the process to terminate gracefully
        Process process = proc.getProcess();
        boolean terminated = process.waitFor(60, TimeUnit.SECONDS);
        if (!terminated) {
          log.warn("Process did not terminate gracefully within 60 seconds, forcing shutdown");
          process.destroyForcibly();
          process.waitFor(10, TimeUnit.SECONDS);
        } else {
          log.info("Process terminated gracefully");
        }
      } else {
        log.warn("Could not determine server address for {} at index {}, falling back to SIGTERM",
            serverType, nodeIndex);
        // Fallback: use regular process termination (SIGTERM with timeout)
        Process process = proc.getProcess();
        process.destroy();
        boolean terminated = process.waitFor(30, TimeUnit.SECONDS);
        if (!terminated) {
          log.warn("Process did not terminate within 30 seconds, forcing shutdown");
          process.destroyForcibly();
        }
      }
    } catch (Exception e) {
      log.warn("Error during graceful shutdown, falling back to SIGTERM: {}", e.getMessage());
      Process process = proc.getProcess();
      process.destroy();
      process.waitFor(30, TimeUnit.SECONDS);
    }
  }

  /**
   * Get the address (host:port) of a server at the given index.
   * Returns null if the address cannot be determined.
   */
  private HostAndPort getServerAddress(MiniAccumuloClusterImpl cluster, ServerType serverType,
                                       int nodeIndex) throws Exception {
    try (AccumuloClient client = cluster.createAccumuloClient("root",
        new PasswordToken(cluster.getConfig().getRootPassword()))) {

      List<String> addresses;
      switch (serverType) {
        case MANAGER:
          addresses = client.instanceOperations().getManagerLocations();
          break;
        case TABLET_SERVER:
          addresses = client.instanceOperations().getTabletServers();
          break;
        case GARBAGE_COLLECTOR:
          // GC doesn't expose address directly, return null to use fallback
          log.debug("GC address lookup not supported, using fallback shutdown");
          return null;
        case SCAN_SERVER:
          Set<String> scanServers = client.instanceOperations().getScanServers();
          addresses = new ArrayList<>(scanServers);
          break;
        case COMPACTOR:
          Set<String> compactors = client.instanceOperations().getCompactors();
          addresses = new ArrayList<>(compactors);
          break;
        default:
          log.debug("Unknown server type {}, using fallback shutdown", serverType);
          return null;
      }

      if (addresses == null || nodeIndex >= addresses.size()) {
        log.debug("No address found for {} at index {}", serverType, nodeIndex);
        return null;
      }

      String addressStr = addresses.get(nodeIndex);
      return HostAndPort.fromString(addressStr);
    }
  }

  /**
   * Restart all server types.
   */
  private void restartAllServerTypes(MiniAccumuloCluster cluster, RestartMode mode)
      throws Exception {
    // Restart in order: workers first, then master
    restartAllNodes(cluster, "tablet_server", mode);
    restartAllNodes(cluster, "garbage_collector", mode);
    restartAllNodes(cluster, "scan_server", mode);
    restartAllNodes(cluster, "compactor", mode);
    restartAllNodes(cluster, "manager", mode);
  }

  /**
   * Get total count of all nodes.
   */
  private int getAllNodeCount(MiniAccumuloCluster cluster) throws Exception {
    MiniAccumuloClusterImpl impl = getImpl(cluster);
    int total = 0;
    for (ServerType type : ServerType.values()) {
      Collection<ProcessReference> processes = impl.getProcesses().get(type);
      if (processes != null) {
        total += processes.size();
      }
    }
    return total;
  }

  /**
   * Wait for manager to be available.
   */
  private void waitForManager(AccumuloClient client, long timeoutMs) throws Exception {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeoutMs) {
      try {
        List<String> managers = client.instanceOperations().getManagerLocations();
        if (!managers.isEmpty()) {
          log.info("Manager is available at: {}", managers);
          return;
        }
      } catch (Exception e) {
        // Manager not ready yet
      }
      Thread.sleep(100);
    }
    throw new Exception("Timeout waiting for manager to become available");
  }

  /**
   * Wait for expected tablet servers to register.
   */
  private void waitForTabletServers(MiniAccumuloCluster cluster, AccumuloClient client,
                                    long timeoutMs) throws Exception {
    int expected = getConfigImpl(cluster).getNumTservers();
    log.info("Waiting for {} tablet servers to register", expected);

    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeoutMs) {
      try {
        List<String> registered = client.instanceOperations().getTabletServers();
        if (registered.size() >= expected) {
          log.info("{} tablet servers registered: {}", registered.size(), registered);
          return;
        }
        log.debug("Waiting for tablet servers: {}/{}", registered.size(), expected);
      } catch (Exception e) {
        // Not ready yet
      }
      Thread.sleep(100);
    }
    throw new Exception("Timeout waiting for tablet servers to register");
  }
}
