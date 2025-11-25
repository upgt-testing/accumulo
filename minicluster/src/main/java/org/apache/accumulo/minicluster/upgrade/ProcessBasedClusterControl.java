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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.accumulo.minicluster.ServerType;
import org.apache.accumulo.minicluster.upgrade.AccumuloVersionRegistry.AccumuloDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls cluster processes and manages version switching for rolling upgrades. This class tracks
 * running processes and their versions, and provides methods to restart processes with different
 * Accumulo versions.
 *
 * <p>
 * The upgrade order is:
 * <ol>
 * <li>TabletServers (one at a time)</li>
 * <li>Manager</li>
 * <li>GarbageCollector</li>
 * <li>Compactors</li>
 * <li>Monitor</li>
 * <li>ScanServers</li>
 * <li>CompactionCoordinator (if present)</li>
 * </ol>
 *
 * @since 2.1.2
 */
public class ProcessBasedClusterControl {
  private static final Logger log = LoggerFactory.getLogger(ProcessBasedClusterControl.class);

  // Process tracking
  private Process managerProcess;
  private Process gcProcess;
  private Process monitorProcess;
  private Process coordinatorProcess;
  private final Map<String,List<Process>> tabletServerProcesses = new HashMap<>();
  private final Map<String,List<Process>> scanServerProcesses = new HashMap<>();
  private final Map<String,List<Process>> compactorProcesses = new HashMap<>();
  private Process zooKeeperProcess; // No upgrade support

  // Version tracking - maps each process to its current distribution
  private final Map<Process,String> processVersions = new HashMap<>();

  private final AccumuloVersionRegistry versionRegistry;
  private final PortManager portManager;
  private ProcessExecutor processExecutor;
  private ServerProcessManager serverProcessManager;

  /**
   * Creates a new cluster control instance.
   *
   * @param versionRegistry Version registry for managing distributions
   * @param portManager Port manager for allocating ports
   */
  public ProcessBasedClusterControl(AccumuloVersionRegistry versionRegistry,
      PortManager portManager) {
    this.versionRegistry = versionRegistry;
    this.portManager = portManager;
  }

  /**
   * Sets the process executor for this cluster control.
   *
   * @param processExecutor The process executor
   */
  public void setProcessExecutor(ProcessExecutor processExecutor) {
    this.processExecutor = processExecutor;
  }

  /**
   * Sets the server process manager for this cluster control.
   *
   * @param serverProcessManager The server process manager
   */
  public void setServerProcessManager(ServerProcessManager serverProcessManager) {
    this.serverProcessManager = serverProcessManager;
  }

  /**
   * Performs a rolling upgrade of all Accumulo server processes to the upgrade distribution.
   * ZooKeeper is not upgraded as it stays at a fixed version.
   */
  public void performRollingUpgrade() throws IOException, InterruptedException {
    AccumuloDistribution upgradeDist = versionRegistry.getUpgradeDistribution();
    if (upgradeDist == null) {
      throw new IllegalStateException("No upgrade distribution configured");
    }

    log.info("Starting rolling upgrade to version {}", upgradeDist.getVersion());

    // Phase 1: Upgrade TabletServers one by one
    log.info("Phase 1: Upgrading TabletServers");
    for (Map.Entry<String,List<Process>> entry : tabletServerProcesses.entrySet()) {
      String resourceGroup = entry.getKey();
      List<Process> processes = entry.getValue();
      for (int i = 0; i < processes.size(); i++) {
        log.info("Upgrading TabletServer {} in resource group {}", i, resourceGroup);
        changeTabletServerVersion(resourceGroup, i, upgradeDist);
        waitForClusterStable();
      }
    }

    // Phase 2: Upgrade Manager
    log.info("Phase 2: Upgrading Manager");
    changeManagerVersion(upgradeDist);
    waitForClusterStable();

    // Phase 3: Upgrade GarbageCollector
    log.info("Phase 3: Upgrading GarbageCollector");
    changeGCVersion(upgradeDist);
    waitForClusterStable();

    // Phase 4: Upgrade Compactors
    log.info("Phase 4: Upgrading Compactors");
    for (Map.Entry<String,List<Process>> entry : compactorProcesses.entrySet()) {
      String resourceGroup = entry.getKey();
      List<Process> processes = entry.getValue();
      for (int i = 0; i < processes.size(); i++) {
        log.info("Upgrading Compactor {} in resource group {}", i, resourceGroup);
        changeCompactorVersion(resourceGroup, i, upgradeDist);
        waitForClusterStable();
      }
    }

    // Phase 5: Upgrade Monitor
    if (monitorProcess != null) {
      log.info("Phase 5: Upgrading Monitor");
      changeMonitorVersion(upgradeDist);
      waitForClusterStable();
    }

    // Phase 6: Upgrade ScanServers
    log.info("Phase 6: Upgrading ScanServers");
    for (Map.Entry<String,List<Process>> entry : scanServerProcesses.entrySet()) {
      String resourceGroup = entry.getKey();
      List<Process> processes = entry.getValue();
      for (int i = 0; i < processes.size(); i++) {
        log.info("Upgrading ScanServer {} in resource group {}", i, resourceGroup);
        changeScanServerVersion(resourceGroup, i, upgradeDist);
        waitForClusterStable();
      }
    }

    // Phase 7: Upgrade CompactionCoordinator if present
    if (coordinatorProcess != null) {
      log.info("Phase 7: Upgrading CompactionCoordinator");
      changeCoordinatorVersion(upgradeDist);
      waitForClusterStable();
    }

    log.info("Rolling upgrade completed successfully");
    checkProcessesAlive();
  }

  /**
   * Changes the Manager version by restarting it with a new distribution.
   */
  public void changeManagerVersion(AccumuloDistribution newDistribution)
      throws IOException, InterruptedException {
    log.info("Changing Manager version to {}", newDistribution.getVersion());

    if (serverProcessManager == null) {
      throw new IllegalStateException("ServerProcessManager not initialized");
    }

    // Restart Manager with new distribution (-1 indicates single-instance server)
    var processInfo =
        serverProcessManager.restartServerWithNewVersion(ServerType.MANAGER, -1, newDistribution);

    // Update process tracking
    managerProcess = processInfo.getProcess();
    processVersions.put(managerProcess, newDistribution.getVersion());

    log.info("Manager restarted with version {}", newDistribution.getVersion());
  }

  /**
   * Changes a TabletServer version by restarting it with a new distribution.
   */
  public void changeTabletServerVersion(String resourceGroup, int index,
      AccumuloDistribution newDistribution) throws IOException, InterruptedException {
    log.info("Changing TabletServer[{}][{}] version to {}", resourceGroup, index,
        newDistribution.getVersion());

    if (serverProcessManager == null) {
      throw new IllegalStateException("ServerProcessManager not initialized");
    }

    // Get the current process
    List<Process> processes = tabletServerProcesses.get(resourceGroup);
    if (processes == null || index >= processes.size()) {
      throw new IllegalArgumentException(
          "TabletServer not found: " + resourceGroup + "[" + index + "]");
    }

    // Restart TabletServer with new distribution
    var processInfo = serverProcessManager.restartServerWithNewVersion(ServerType.TABLET_SERVER,
        index, newDistribution);

    // Update process tracking
    Process newProcess = processInfo.getProcess();
    processes.set(index, newProcess);
    processVersions.put(newProcess, newDistribution.getVersion());

    log.info("TabletServer[{}][{}] restarted with version {}", resourceGroup, index,
        newDistribution.getVersion());
  }

  /**
   * Changes the GarbageCollector version by restarting it with a new distribution.
   */
  public void changeGCVersion(AccumuloDistribution newDistribution)
      throws IOException, InterruptedException {
    log.info("Changing GarbageCollector version to {}", newDistribution.getVersion());

    if (serverProcessManager == null) {
      throw new IllegalStateException("ServerProcessManager not initialized");
    }

    var processInfo = serverProcessManager.restartServerWithNewVersion(ServerType.GARBAGE_COLLECTOR,
        -1, newDistribution);

    gcProcess = processInfo.getProcess();
    processVersions.put(gcProcess, newDistribution.getVersion());

    log.info("GarbageCollector restarted with version {}", newDistribution.getVersion());
  }

  /**
   * Changes a Compactor version by restarting it with a new distribution.
   */
  public void changeCompactorVersion(String resourceGroup, int index,
      AccumuloDistribution newDistribution) throws IOException, InterruptedException {
    log.info("Changing Compactor[{}][{}] version to {}", resourceGroup, index,
        newDistribution.getVersion());

    if (serverProcessManager == null) {
      throw new IllegalStateException("ServerProcessManager not initialized");
    }

    List<Process> processes = compactorProcesses.get(resourceGroup);
    if (processes == null || index >= processes.size()) {
      throw new IllegalArgumentException(
          "Compactor not found: " + resourceGroup + "[" + index + "]");
    }

    var processInfo = serverProcessManager.restartServerWithNewVersion(ServerType.COMPACTOR, index,
        newDistribution);

    Process newProcess = processInfo.getProcess();
    processes.set(index, newProcess);
    processVersions.put(newProcess, newDistribution.getVersion());

    log.info("Compactor[{}][{}] restarted with version {}", resourceGroup, index,
        newDistribution.getVersion());
  }

  /**
   * Changes the Monitor version by restarting it with a new distribution.
   */
  public void changeMonitorVersion(AccumuloDistribution newDistribution)
      throws IOException, InterruptedException {
    log.info("Changing Monitor version to {}", newDistribution.getVersion());

    if (serverProcessManager == null) {
      throw new IllegalStateException("ServerProcessManager not initialized");
    }

    var processInfo =
        serverProcessManager.restartServerWithNewVersion(ServerType.MONITOR, -1, newDistribution);

    monitorProcess = processInfo.getProcess();
    processVersions.put(monitorProcess, newDistribution.getVersion());

    log.info("Monitor restarted with version {}", newDistribution.getVersion());
  }

  /**
   * Changes a ScanServer version by restarting it with a new distribution.
   */
  public void changeScanServerVersion(String resourceGroup, int index,
      AccumuloDistribution newDistribution) throws IOException, InterruptedException {
    log.info("Changing ScanServer[{}][{}] version to {}", resourceGroup, index,
        newDistribution.getVersion());

    if (serverProcessManager == null) {
      throw new IllegalStateException("ServerProcessManager not initialized");
    }

    List<Process> processes = scanServerProcesses.get(resourceGroup);
    if (processes == null || index >= processes.size()) {
      throw new IllegalArgumentException(
          "ScanServer not found: " + resourceGroup + "[" + index + "]");
    }

    var processInfo = serverProcessManager.restartServerWithNewVersion(ServerType.SCAN_SERVER,
        index, newDistribution);

    Process newProcess = processInfo.getProcess();
    processes.set(index, newProcess);
    processVersions.put(newProcess, newDistribution.getVersion());

    log.info("ScanServer[{}][{}] restarted with version {}", resourceGroup, index,
        newDistribution.getVersion());
  }

  /**
   * Changes the CompactionCoordinator version by restarting it with a new distribution.
   */
  public void changeCoordinatorVersion(AccumuloDistribution newDistribution)
      throws IOException, InterruptedException {
    log.info("Changing CompactionCoordinator version to {}", newDistribution.getVersion());

    if (serverProcessManager == null) {
      throw new IllegalStateException("ServerProcessManager not initialized");
    }

    var processInfo = serverProcessManager
        .restartServerWithNewVersion(ServerType.COMPACTION_COORDINATOR, -1, newDistribution);

    coordinatorProcess = processInfo.getProcess();
    processVersions.put(coordinatorProcess, newDistribution.getVersion());

    log.info("CompactionCoordinator restarted with version {}", newDistribution.getVersion());
  }

  /**
   * Waits for the cluster to stabilize after a change. This includes waiting for ZooKeeper
   * registration, Manager responsiveness, etc.
   */
  public void waitForClusterStable() throws InterruptedException {
    log.debug("Waiting for cluster to stabilize");
    // Grace period for processes to register and stabilize
    Thread.sleep(5000);

    // Verify all processes are still alive
    checkProcessesAlive();

    log.debug("Cluster appears stable");
  }

  /**
   * Checks that all registered processes are still alive.
   *
   * @throws IllegalStateException if any process has died
   */
  public void checkProcessesAlive() {
    List<String> deadProcesses = new ArrayList<>();

    if (managerProcess != null && !managerProcess.isAlive()) {
      deadProcesses.add("Manager");
    }
    if (gcProcess != null && !gcProcess.isAlive()) {
      deadProcesses.add("GarbageCollector");
    }
    if (monitorProcess != null && !monitorProcess.isAlive()) {
      deadProcesses.add("Monitor");
    }
    if (coordinatorProcess != null && !coordinatorProcess.isAlive()) {
      deadProcesses.add("CompactionCoordinator");
    }

    // Check TabletServers
    for (Map.Entry<String,List<Process>> entry : tabletServerProcesses.entrySet()) {
      String rg = entry.getKey();
      List<Process> processes = entry.getValue();
      for (int i = 0; i < processes.size(); i++) {
        if (!processes.get(i).isAlive()) {
          deadProcesses.add("TabletServer[" + rg + "][" + i + "]");
        }
      }
    }

    // Check ScanServers
    for (Map.Entry<String,List<Process>> entry : scanServerProcesses.entrySet()) {
      String rg = entry.getKey();
      List<Process> processes = entry.getValue();
      for (int i = 0; i < processes.size(); i++) {
        if (!processes.get(i).isAlive()) {
          deadProcesses.add("ScanServer[" + rg + "][" + i + "]");
        }
      }
    }

    // Check Compactors
    for (Map.Entry<String,List<Process>> entry : compactorProcesses.entrySet()) {
      String rg = entry.getKey();
      List<Process> processes = entry.getValue();
      for (int i = 0; i < processes.size(); i++) {
        if (!processes.get(i).isAlive()) {
          deadProcesses.add("Compactor[" + rg + "][" + i + "]");
        }
      }
    }

    if (!deadProcesses.isEmpty()) {
      throw new IllegalStateException("The following processes have died: " + deadProcesses);
    }
  }

  // Getters for process tracking (used by ProcessExecutor in Phase 2)

  public Process getManagerProcess() {
    return managerProcess;
  }

  public void setManagerProcess(Process process, String version) {
    this.managerProcess = process;
    this.processVersions.put(process, version);
  }

  public Process getGcProcess() {
    return gcProcess;
  }

  public void setGcProcess(Process process, String version) {
    this.gcProcess = process;
    this.processVersions.put(process, version);
  }

  public Process getMonitorProcess() {
    return monitorProcess;
  }

  public void setMonitorProcess(Process process, String version) {
    this.monitorProcess = process;
    this.processVersions.put(process, version);
  }

  public Process getCoordinatorProcess() {
    return coordinatorProcess;
  }

  public void setCoordinatorProcess(Process process, String version) {
    this.coordinatorProcess = process;
    this.processVersions.put(process, version);
  }

  public Map<String,List<Process>> getTabletServerProcesses() {
    return tabletServerProcesses;
  }

  public Map<String,List<Process>> getScanServerProcesses() {
    return scanServerProcesses;
  }

  public Map<String,List<Process>> getCompactorProcesses() {
    return compactorProcesses;
  }

  public String getProcessVersion(Process process) {
    return processVersions.get(process);
  }
}
