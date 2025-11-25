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
package org.apache.accumulo.minicluster;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.AuthenticationToken;
import org.apache.accumulo.minicluster.upgrade.AccumuloVersionRegistry;
import org.apache.accumulo.minicluster.upgrade.PortManager;
import org.apache.accumulo.minicluster.upgrade.ProcessBasedClusterControl;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloConfigImpl;
import org.apache.accumulo.miniclusterImpl.ProcessBasedMiniAccumuloClusterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A process-based MiniAccumuloCluster that supports version switching and rolling upgrades. This
 * cluster allows running different Accumulo versions for different server components, enabling
 * upgrade testing and compatibility verification.
 *
 * <p>
 * Usage example:
 *
 * <pre>
 * ProcessBasedMiniAccumuloCluster cluster =
 *     new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "password").numTabletServers(3)
 *         .accumuloStartDistribution("/path/to/accumulo-2.1.x")
 *         .accumuloUpgradeDistribution("/path/to/accumulo-3.0.x").build();
 * cluster.start();
 * // ... perform operations ...
 * cluster.upgrade(); // Rolling upgrade
 * cluster.stop();
 * </pre>
 *
 * @since 2.1.2
 */
public class ProcessBasedMiniAccumuloCluster implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(ProcessBasedMiniAccumuloCluster.class);

  private final MiniAccumuloConfigImpl config;
  private final AccumuloVersionRegistry versionRegistry;
  private final PortManager portManager;
  private ProcessBasedMiniAccumuloClusterImpl baseCluster;
  private ProcessBasedClusterControl clusterControl;
  private boolean started = false;

  private ProcessBasedMiniAccumuloCluster(Builder builder) throws IOException {
    this.config = builder.config;

    // Initialize version registry
    this.versionRegistry = new AccumuloVersionRegistry();
    if (builder.startDistribution != null) {
      versionRegistry.registerStartDistribution(builder.startDistribution);
    }
    if (builder.upgradeDistribution != null) {
      versionRegistry.registerUpgradeDistribution(builder.upgradeDistribution);
    }

    // Initialize port manager
    this.portManager = new PortManager(config.getDir());

    // Initialize process-based cluster implementation with version support
    this.baseCluster =
        new ProcessBasedMiniAccumuloClusterImpl(config, versionRegistry, portManager);

    // Get the upgrade control from the implementation
    this.clusterControl = baseCluster.getUpgradeControl();
  }

  /**
   * Starts the Accumulo cluster and all its processes.
   */
  public void start() throws IOException, InterruptedException {
    if (started) {
      throw new IllegalStateException("Cluster already started");
    }

    log.info("Starting ProcessBasedMiniAccumuloCluster");
    baseCluster.start();
    started = true;
    log.info("ProcessBasedMiniAccumuloCluster started successfully");
  }

  /**
   * Stops all Accumulo processes and cleans up resources.
   */
  public void stop() throws IOException, InterruptedException {
    if (!started) {
      return;
    }

    log.info("Stopping ProcessBasedMiniAccumuloCluster");
    baseCluster.stop();
    started = false;
    log.info("ProcessBasedMiniAccumuloCluster stopped");
  }

  @Override
  public void close() throws IOException {
    try {
      stop();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  /**
   * Performs a rolling upgrade of all Accumulo server processes to the upgrade distribution. The
   * upgrade order is: TabletServers, Manager, GarbageCollector, Compactors, Monitor, ScanServers.
   */
  public void upgrade() throws IOException, InterruptedException {
    if (!started) {
      throw new IllegalStateException("Cluster must be started before upgrade");
    }

    if (versionRegistry.getUpgradeDistribution() == null) {
      throw new IllegalStateException("No upgrade distribution configured");
    }

    log.info("Starting rolling upgrade to {}",
        versionRegistry.getUpgradeDistribution().getVersion());

    // This will be implemented in Phase 2
    throw new UnsupportedOperationException("Rolling upgrade not yet implemented - Phase 2");
  }

  /**
   * Creates an AccumuloClient for connecting to this cluster.
   */
  public AccumuloClient createAccumuloClient(String user, AuthenticationToken token) {
    return baseCluster.createAccumuloClient(user, token);
  }

  /**
   * Returns the client properties for connecting to this cluster.
   */
  public Properties getClientProperties() {
    return baseCluster.getClientProperties();
  }

  /**
   * Returns the instance name of this cluster.
   */
  public String getInstanceName() {
    return baseCluster.getInstanceName();
  }

  /**
   * Returns the ZooKeeper connection string for this cluster.
   */
  public String getZooKeepers() {
    return baseCluster.getZooKeepers();
  }

  /**
   * Returns the version registry for this cluster.
   */
  public AccumuloVersionRegistry getVersionRegistry() {
    return versionRegistry;
  }

  /**
   * Returns the port manager for this cluster.
   */
  public PortManager getPortManager() {
    return portManager;
  }

  /**
   * Builder for creating ProcessBasedMiniAccumuloCluster instances.
   */
  public static class Builder {
    private final MiniAccumuloConfigImpl config;
    private String startDistribution;
    private String upgradeDistribution;

    /**
     * Creates a new builder.
     *
     * @param dir Directory for cluster data
     * @param rootPassword Root password for the cluster
     */
    public Builder(File dir, String rootPassword) throws IOException {
      this.config = new MiniAccumuloConfigImpl(dir, rootPassword);

      // Read system properties for accumulo home
      this.startDistribution = System.getProperty("accumulo.start.home");
      this.upgradeDistribution = System.getProperty("accumulo.upgrade.home");

      // Fallback to environment variable for start distribution
      if (this.startDistribution == null) {
        this.startDistribution = System.getenv("ACCUMULO_HOME");
      }

      log.info("Builder initialized with start distribution: {}", startDistribution);
      log.info("Builder initialized with upgrade distribution: {}", upgradeDistribution);
    }

    /**
     * Sets the Accumulo distribution to start with.
     */
    public Builder accumuloStartDistribution(String accumuloHome) {
      this.startDistribution = accumuloHome;
      return this;
    }

    /**
     * Sets the Accumulo distribution to upgrade to.
     */
    public Builder accumuloUpgradeDistribution(String accumuloHome) {
      this.upgradeDistribution = accumuloHome;
      return this;
    }

    /**
     * Sets the number of tablet servers.
     */
    public Builder numTabletServers(int numTabletServers) {
      config.setNumTservers(numTabletServers);
      return this;
    }

    /**
     * Sets the number of scan servers.
     */
    public Builder numScanServers(int numScanServers) {
      config.setNumScanServers(numScanServers);
      return this;
    }

    /**
     * Sets the number of compactors for the default resource group.
     */
    public Builder numCompactors(int numCompactors) {
      config.setNumCompactors(numCompactors);
      return this;
    }

    /**
     * Sets a site configuration property.
     */
    public Builder setProperty(String key, String value) {
      config.setProperty(key, value);
      return this;
    }

    /**
     * Builds the ProcessBasedMiniAccumuloCluster.
     */
    public ProcessBasedMiniAccumuloCluster build() throws IOException {
      if (startDistribution == null) {
        throw new IllegalStateException(
            "Start distribution not configured. Set accumulo.start.home system property "
                + "or call accumuloStartDistribution()");
      }

      return new ProcessBasedMiniAccumuloCluster(this);
    }
  }
}
