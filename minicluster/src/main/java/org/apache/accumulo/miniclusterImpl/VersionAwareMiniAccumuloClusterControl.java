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
package org.apache.accumulo.miniclusterImpl;

import java.io.IOException;
import java.util.Map;

import org.apache.accumulo.minicluster.ServerType;
import org.apache.accumulo.minicluster.upgrade.AccumuloVersionRegistry;
import org.apache.accumulo.minicluster.upgrade.AccumuloVersionRegistry.AccumuloDistribution;
import org.apache.accumulo.minicluster.upgrade.ProcessBasedClusterControl;
import org.apache.accumulo.minicluster.upgrade.ProcessExecutor.ProcessInfo;
import org.apache.accumulo.minicluster.upgrade.ServerProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Version-aware cluster control that uses ServerProcessManager for process spawning. This extends
 * the standard MiniAccumuloClusterControl to add version-specific classpath support while reusing
 * the existing start/stop/admin infrastructure.
 *
 * <p>
 * Key differences from base class: - Uses ServerProcessManager.startServer() instead of
 * cluster._exec() - Spawns processes with version-specific classpaths - Tracks process versions for
 * upgrade operations - Integrates with ProcessBasedClusterControl for upgrade orchestration
 *
 * @since 2.1.2
 */
public class VersionAwareMiniAccumuloClusterControl extends MiniAccumuloClusterControl {
  private static final Logger log =
      LoggerFactory.getLogger(VersionAwareMiniAccumuloClusterControl.class);

  private final ServerProcessManager serverProcessManager;
  private final AccumuloVersionRegistry versionRegistry;
  private final ProcessBasedClusterControl upgradeControl;

  /**
   * Creates a new version-aware cluster control.
   *
   * @param cluster The mini cluster implementation
   * @param serverProcessManager Server process manager for version-specific spawning
   * @param versionRegistry Version registry for distribution management
   * @param upgradeControl Upgrade control for tracking process versions
   */
  public VersionAwareMiniAccumuloClusterControl(MiniAccumuloClusterImpl cluster,
      ServerProcessManager serverProcessManager, AccumuloVersionRegistry versionRegistry,
      ProcessBasedClusterControl upgradeControl) {
    super(cluster);
    this.serverProcessManager = serverProcessManager;
    this.versionRegistry = versionRegistry;
    this.upgradeControl = upgradeControl;
  }

  /**
   * Starts servers using version-specific classpaths via ServerProcessManager. Overrides the base
   * implementation to use ServerProcessManager instead of cluster._exec().
   */
  @Override
  @SuppressWarnings("removal")
  public synchronized void start(ServerType server, Map<String,String> configOverrides, int limit)
      throws IOException {
    if (limit <= 0) {
      return;
    }

    AccumuloDistribution dist = versionRegistry.getStartDistribution();
    if (dist == null) {
      throw new IllegalStateException(
          "No start distribution configured. Set accumulo.start.home or use Builder.accumuloStartDistribution()");
    }

    String version = dist.getVersion();

    switch (server) {
      case TABLET_SERVER:
        synchronized (tabletServerProcesses) {
          int count = 0;
          for (int i = tabletServerProcesses.size();
              count < limit && i < cluster.getConfig().getNumTservers(); i++, ++count) {
            log.info("Starting TabletServer {} with distribution {}", i, version);
            ProcessInfo pi = serverProcessManager.startServer(server, dist, i, configOverrides);
            tabletServerProcesses.add(pi.getProcess());

            // Track in upgrade control
            upgradeControl.getTabletServerProcesses().computeIfAbsent("default",
                k -> new java.util.ArrayList<>());
            if (upgradeControl.getTabletServerProcesses().get("default").size() <= i) {
              upgradeControl.getTabletServerProcesses().get("default").add(pi.getProcess());
            } else {
              upgradeControl.getTabletServerProcesses().get("default").set(i, pi.getProcess());
            }
            upgradeControl.setManagerProcess(pi.getProcess(), version); // Track version
          }
        }
        break;

      case MASTER:
      case MANAGER:
        if (managerProcess == null) {
          log.info("Starting Manager with distribution {}", version);
          ProcessInfo pi = serverProcessManager.startServer(server, dist, -1, configOverrides);
          managerProcess = pi.getProcess();

          // Track in upgrade control
          upgradeControl.setManagerProcess(pi.getProcess(), version);
        }
        break;

      case ZOOKEEPER:
        // ZooKeeper doesn't support version switching - use base implementation
        if (zooKeeperProcess == null) {
          log.info("Starting ZooKeeper (no version switching)");
          zooKeeperProcess = cluster._exec(org.apache.zookeeper.server.ZooKeeperServerMain.class,
              server, configOverrides, cluster.getZooCfgFile().getAbsolutePath()).getProcess();
        }
        break;

      case GARBAGE_COLLECTOR:
        if (gcProcess == null) {
          log.info("Starting GarbageCollector with distribution {}", version);
          ProcessInfo pi = serverProcessManager.startServer(server, dist, -1, configOverrides);
          gcProcess = pi.getProcess();

          // Track in upgrade control
          upgradeControl.setGcProcess(pi.getProcess(), version);
        }
        break;

      case MONITOR:
        if (monitor == null) {
          log.info("Starting Monitor with distribution {}", version);
          ProcessInfo pi = serverProcessManager.startServer(server, dist, -1, configOverrides);
          monitor = pi.getProcess();

          // Track in upgrade control
          upgradeControl.setMonitorProcess(pi.getProcess(), version);
        }
        break;

      case SCAN_SERVER:
        synchronized (scanServerProcesses) {
          int count = 0;
          for (int i = scanServerProcesses.size();
              count < limit && i < cluster.getConfig().getNumScanServers(); i++, ++count) {
            log.info("Starting ScanServer {} with distribution {}", i, version);
            ProcessInfo pi = serverProcessManager.startServer(server, dist, i, configOverrides);
            scanServerProcesses.add(pi.getProcess());

            // Track in upgrade control
            upgradeControl.getScanServerProcesses().computeIfAbsent("default",
                k -> new java.util.ArrayList<>());
            if (upgradeControl.getScanServerProcesses().get("default").size() <= i) {
              upgradeControl.getScanServerProcesses().get("default").add(pi.getProcess());
            }
          }
        }
        break;

      case COMPACTION_COORDINATOR:
        if (coordinatorProcess == null) {
          log.info("Starting CompactionCoordinator with distribution {}", version);
          ProcessInfo pi = serverProcessManager.startServer(server, dist, -1, configOverrides);
          coordinatorProcess = pi.getProcess();

          // Track in upgrade control
          upgradeControl.setCoordinatorProcess(pi.getProcess(), version);
        }
        break;

      case COMPACTOR:
        synchronized (compactorProcesses) {
          int count = 0;
          for (int i = compactorProcesses.size();
              count < limit && i < cluster.getConfig().getNumCompactors(); i++, ++count) {
            log.info("Starting Compactor {} with distribution {}", i, version);
            ProcessInfo pi = serverProcessManager.startServer(server, dist, i, configOverrides);
            compactorProcesses.add(pi.getProcess());

            // Track in upgrade control
            String queueName = configOverrides.getOrDefault("QUEUE_NAME", "default");
            upgradeControl.getCompactorProcesses().computeIfAbsent(queueName,
                k -> new java.util.ArrayList<>());
            if (upgradeControl.getCompactorProcesses().get(queueName).size() <= i) {
              upgradeControl.getCompactorProcesses().get(queueName).add(pi.getProcess());
            }
          }
        }
        break;

      default:
        throw new UnsupportedOperationException("Cannot start process for " + server);
    }
  }
}
