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

import org.apache.accumulo.minicluster.upgrade.AccumuloVersionRegistry;
import org.apache.accumulo.minicluster.upgrade.PortManager;
import org.apache.accumulo.minicluster.upgrade.ProcessBasedClusterControl;
import org.apache.accumulo.minicluster.upgrade.ProcessExecutor;
import org.apache.accumulo.minicluster.upgrade.ServerProcessManager;

/**
 * Extension of MiniAccumuloClusterImpl that supports version-aware server startup. This class
 * overrides the cluster control to use ServerProcessManager for spawning processes with
 * version-specific classpaths.
 *
 * @since 2.1.2
 */
public class ProcessBasedMiniAccumuloClusterImpl extends MiniAccumuloClusterImpl {

  private final VersionAwareMiniAccumuloClusterControl versionAwareControl;
  private final ProcessBasedClusterControl upgradeControl;

  /**
   * Creates a new process-based mini cluster implementation.
   *
   * @param config Cluster configuration
   * @param versionRegistry Version registry for distribution management
   * @param portManager Port manager for port allocation
   * @throws IOException if initialization fails
   */
  public ProcessBasedMiniAccumuloClusterImpl(MiniAccumuloConfigImpl config,
      AccumuloVersionRegistry versionRegistry, PortManager portManager) throws IOException {
    super(config);

    // Initialize upgrade infrastructure
    this.upgradeControl = new ProcessBasedClusterControl(versionRegistry, portManager);
    ProcessExecutor processExecutor = new ProcessExecutor(config, versionRegistry);
    ServerProcessManager serverProcessManager =
        new ServerProcessManager(config, processExecutor, versionRegistry, portManager);

    // Wire components together
    this.upgradeControl.setProcessExecutor(processExecutor);
    this.upgradeControl.setServerProcessManager(serverProcessManager);

    // Create version-aware control
    this.versionAwareControl = new VersionAwareMiniAccumuloClusterControl(this,
        serverProcessManager, versionRegistry, upgradeControl);
  }

  /**
   * Returns the version-aware cluster control that uses ServerProcessManager for process spawning.
   * This override replaces the standard control with one that supports version-specific classpaths.
   */
  @Override
  public MiniAccumuloClusterControl getClusterControl() {
    return versionAwareControl;
  }

  /**
   * Returns the upgrade control for performing rolling upgrades.
   */
  public ProcessBasedClusterControl getUpgradeControl() {
    return upgradeControl;
  }
}
