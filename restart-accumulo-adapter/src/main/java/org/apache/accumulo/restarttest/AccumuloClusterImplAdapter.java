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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.minicluster.ServerType;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloConfigImpl;
import org.apache.accumulo.miniclusterImpl.ProcessReference;
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
 * Cluster adapter for Apache Accumulo's MiniAccumuloClusterImpl.
 * This adapter handles the implementation class directly, which is what
 * most test classes return from their getCluster() method.
 */
public class AccumuloClusterImplAdapter implements ClusterAdapter<MiniAccumuloClusterImpl> {

  private static final Logger log = LoggerFactory.getLogger(AccumuloClusterImplAdapter.class);

  private final AccumuloStateCaptureImpl stateCapture;
  private final CompositeHealthCheck<MiniAccumuloClusterImpl> healthCheck;

  public AccumuloClusterImplAdapter() {
    this.stateCapture = new AccumuloStateCaptureImpl();
    this.healthCheck = new CompositeHealthCheck<>("accumulo-health");

    // Add health checks
    // this.healthCheck.addCheck(new AccumuloManagerActiveCheck());
    // this.healthCheck.addCheck(new AccumuloTabletServersRegisteredCheck());
    // this.healthCheck.addCheck(new AccumuloTabletsAssignedCheck());
    // this.healthCheck.addCheck(new AccumuloClusterBalancedCheck());
    // this.healthCheck.addCheck(new AccumuloScanServersRegisteredCheck());
  }

  @Override
  public Class<MiniAccumuloClusterImpl> getClusterType() {
    return MiniAccumuloClusterImpl.class;
  }

  @Override
  public void restartNode(MiniAccumuloClusterImpl cluster, String nodeRole,
                         int nodeIndex, RestartMode mode) throws Exception {
    ServerType serverType = normalizeRole(nodeRole);

    if (serverType == null) { // "all" role
      restartAllNodes(cluster, nodeRole, mode);
      return;
    }

    // Get process at index
    Collection<ProcessReference> processes = cluster.getProcesses().get(serverType);
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
        cluster.killProcess(serverType, proc);
        break;
      case CRASH:
        killProcessForcibly(cluster, serverType, proc);
        break;
      case DELAYED_CRASH:
        killProcessForcibly(cluster, serverType, proc);
        Thread.sleep(500); // Allow partial state propagation
        break;
      default:
        throw new IllegalArgumentException("Unknown restart mode: " + mode);
    }

    // Start replacement process
    log.info("Starting replacement {} process", serverType);
    cluster.getClusterControl().start(serverType, null);

    // Wait for cluster to stabilize
    Thread.sleep(100);
  }

  @Override
  public void restartAllNodes(MiniAccumuloClusterImpl cluster, String nodeRole,
                             RestartMode mode) throws Exception {
    ServerType serverType = normalizeRole(nodeRole);

    if (serverType == null) { // "all" role
      log.info("Restarting all server types with mode {}", mode);
      restartAllServerTypes(cluster, mode);
      return;
    }

    // Get all processes of this type
    Collection<ProcessReference> processes = cluster.getProcesses().get(serverType);
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
  public void waitActive(MiniAccumuloClusterImpl cluster) throws Exception {
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
  public StateCapture<MiniAccumuloClusterImpl> getStateCapture() {
    return stateCapture;
  }

  @Override
  public HealthCheck<MiniAccumuloClusterImpl> getHealthCheck() {
    return healthCheck;
  }

  @Override
  public int getNodeCount(MiniAccumuloClusterImpl cluster, String nodeRole) throws Exception {
    ServerType serverType = normalizeRole(nodeRole);

    if (serverType == null) { // "all" role
      return getAllNodeCount(cluster);
    }

    Collection<ProcessReference> processes = cluster.getProcesses().get(serverType);
    return processes != null ? processes.size() : 0;
  }

  // Helper methods

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
   */
  private void killProcessForcibly(MiniAccumuloClusterImpl cluster, ServerType type,
                                   ProcessReference proc) throws Exception {
    // Get the actual Process from the reference
    // Since ProcessReference is opaque, we use killProcess but could enhance
    // for more aggressive kill if needed
    cluster.killProcess(type, proc);
  }

  /**
   * Restart all server types.
   */
  private void restartAllServerTypes(MiniAccumuloClusterImpl cluster, RestartMode mode)
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
  private int getAllNodeCount(MiniAccumuloClusterImpl cluster) throws Exception {
    int total = 0;
    for (ServerType type : ServerType.values()) {
      Collection<ProcessReference> processes = cluster.getProcesses().get(type);
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
  private void waitForTabletServers(MiniAccumuloClusterImpl cluster, AccumuloClient client,
                                    long timeoutMs) throws Exception {
    int expected = cluster.getConfig().getNumTservers();
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
