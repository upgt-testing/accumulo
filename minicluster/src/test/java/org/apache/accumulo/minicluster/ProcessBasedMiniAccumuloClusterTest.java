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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.accumulo.minicluster.upgrade.AccumuloVersionRegistry;
import org.apache.accumulo.minicluster.upgrade.PortManager;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for ProcessBasedMiniAccumuloCluster infrastructure components. These tests verify the
 * version registry, port manager, and builder functionality without requiring a full cluster
 * startup.
 */
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "paths not set by user input")
public class ProcessBasedMiniAccumuloClusterTest {

  @TempDir
  private File tempDir;

  private File mockAccumuloHome;

  @BeforeEach
  public void setUp() throws IOException {
    // Create a mock Accumulo distribution directory structure
    mockAccumuloHome = new File(tempDir, "mock-accumulo-2.1.0");
    File libDir = new File(mockAccumuloHome, "lib");
    File confDir = new File(mockAccumuloHome, "conf");

    assertTrue(libDir.mkdirs());
    assertTrue(confDir.mkdirs());

    // Create some mock JAR files
    new File(libDir, "accumulo-core-2.1.0.jar").createNewFile();
    new File(libDir, "accumulo-server-base-2.1.0.jar").createNewFile();
    new File(libDir, "guava-31.1-jre.jar").createNewFile();

    // Create lib/ext directory
    File libExtDir = new File(mockAccumuloHome, "lib/ext");
    assertTrue(libExtDir.mkdirs());
    new File(libExtDir, "custom-iterator.jar").createNewFile();
  }

  @AfterEach
  public void tearDown() {
    if (tempDir != null && tempDir.exists()) {
      FileUtils.deleteQuietly(tempDir);
    }
  }

  @Test
  public void testVersionRegistryLoadDistribution() throws IOException {
    AccumuloVersionRegistry registry = new AccumuloVersionRegistry();

    // Register a distribution
    registry.registerStartDistribution(mockAccumuloHome.getAbsolutePath());

    // Verify distribution was loaded
    AccumuloVersionRegistry.AccumuloDistribution dist = registry.getStartDistribution();
    assertNotNull(dist);
    assertEquals(mockAccumuloHome, dist.getAccumuloHome());

    // Verify JARs were found
    assertTrue(dist.getCoreJarPaths().size() >= 3, "Should find at least 3 core JARs");
    assertTrue(dist.getExtensionJarPaths().size() >= 1, "Should find at least 1 extension JAR");

    // Verify classpath building
    var classpath = registry.buildClasspath(dist, ServerType.TABLET_SERVER);
    assertNotNull(classpath);
    assertTrue(classpath.size() >= 4, "Classpath should contain core and extension JARs");
  }

  @Test
  public void testVersionRegistryInvalidPath() {
    AccumuloVersionRegistry registry = new AccumuloVersionRegistry();

    // Try to register a non-existent path
    assertThrows(IOException.class, () -> {
      registry.registerStartDistribution("/nonexistent/path");
    });
  }

  @Test
  public void testPortManagerAllocation() throws IOException {
    File clusterDir = new File(tempDir, "cluster");
    assertTrue(clusterDir.mkdirs());

    PortManager portManager = new PortManager(clusterDir);

    // Allocate some ports
    int managerPort = portManager.allocatePort("manager", "thriftPort");
    int tserver0Port = portManager.allocatePort("tserver.0", "thriftPort");
    int tserver1Port = portManager.allocatePort("tserver.1", "thriftPort");

    // Verify ports are in valid range
    assertTrue(managerPort >= 50000 && managerPort <= 60000);
    assertTrue(tserver0Port >= 50000 && tserver0Port <= 60000);
    assertTrue(tserver1Port >= 50000 && tserver1Port <= 60000);

    // Verify ports are different
    assertTrue(managerPort != tserver0Port);
    assertTrue(managerPort != tserver1Port);
    assertTrue(tserver0Port != tserver1Port);

    // Verify persistence file was created
    File persistenceFile = new File(clusterDir, "port-allocations.properties");
    assertTrue(persistenceFile.exists());
  }

  @Test
  public void testPortManagerPersistence() throws IOException {
    File clusterDir = new File(tempDir, "cluster");
    assertTrue(clusterDir.mkdirs());

    PortManager portManager1 = new PortManager(clusterDir);

    // Allocate ports
    int managerPort1 = portManager1.allocatePort("manager", "thriftPort");
    int tserver0Port1 = portManager1.allocatePort("tserver.0", "thriftPort");

    // Create a new PortManager instance (simulating restart)
    PortManager portManager2 = new PortManager(clusterDir);

    // Allocate same ports - should get the same values back
    int managerPort2 = portManager2.allocatePort("manager", "thriftPort");
    int tserver0Port2 = portManager2.allocatePort("tserver.0", "thriftPort");

    // Verify ports were restored
    assertEquals(managerPort1, managerPort2, "Manager port should be restored from persistence");
    assertEquals(tserver0Port1, tserver0Port2, "TServer port should be restored from persistence");
  }

  @Test
  public void testPortManagerLoadPersistedPort() throws IOException {
    File clusterDir = new File(tempDir, "cluster");
    assertTrue(clusterDir.mkdirs());

    PortManager portManager = new PortManager(clusterDir);

    // Allocate a port
    int originalPort = portManager.allocatePort("manager", "thriftPort");

    // Load the persisted port
    Integer loadedPort = portManager.loadPersistedPort("manager", "thriftPort");

    assertNotNull(loadedPort);
    assertEquals(originalPort, loadedPort.intValue());
  }

  @Test
  public void testBuilderRequiresStartDistribution() {
    File clusterDir = new File(tempDir, "cluster");

    // Try to build without setting start distribution
    assertThrows(IllegalStateException.class, () -> {
      new ProcessBasedMiniAccumuloCluster.Builder(clusterDir, "password").build();
    });
  }

  @Test
  public void testBuilderWithSystemProperty() throws IOException {
    File clusterDir = new File(tempDir, "cluster");
    assertTrue(clusterDir.mkdirs());

    // Set system property
    String originalProperty = System.getProperty("accumulo.start.home");
    try {
      System.setProperty("accumulo.start.home", mockAccumuloHome.getAbsolutePath());

      // Build should succeed with system property set
      ProcessBasedMiniAccumuloCluster cluster =
          new ProcessBasedMiniAccumuloCluster.Builder(clusterDir, "password").build();

      assertNotNull(cluster);
      assertNotNull(cluster.getVersionRegistry());
      assertNotNull(cluster.getVersionRegistry().getStartDistribution());
    } finally {
      // Restore original property
      if (originalProperty != null) {
        System.setProperty("accumulo.start.home", originalProperty);
      } else {
        System.clearProperty("accumulo.start.home");
      }
    }
  }

  @Test
  public void testBuilderWithExplicitDistribution() throws IOException {
    File clusterDir = new File(tempDir, "cluster");
    assertTrue(clusterDir.mkdirs());

    // Build with explicit distribution
    ProcessBasedMiniAccumuloCluster cluster =
        new ProcessBasedMiniAccumuloCluster.Builder(clusterDir, "password")
            .accumuloStartDistribution(mockAccumuloHome.getAbsolutePath()).numTabletServers(3)
            .numScanServers(1).build();

    assertNotNull(cluster);
    assertNotNull(cluster.getVersionRegistry());
    assertNotNull(cluster.getVersionRegistry().getStartDistribution());
  }

  @Test
  public void testBuilderWithUpgradeDistribution() throws IOException {
    File clusterDir = new File(tempDir, "cluster");
    assertTrue(clusterDir.mkdirs());

    // Create a second mock distribution for upgrade
    File upgradeHome = new File(tempDir, "mock-accumulo-3.0.0");
    File upgradeLib = new File(upgradeHome, "lib");
    assertTrue(upgradeLib.mkdirs());
    new File(upgradeLib, "accumulo-core-3.0.0.jar").createNewFile();

    // Build with both start and upgrade distributions
    ProcessBasedMiniAccumuloCluster cluster =
        new ProcessBasedMiniAccumuloCluster.Builder(clusterDir, "password")
            .accumuloStartDistribution(mockAccumuloHome.getAbsolutePath())
            .accumuloUpgradeDistribution(upgradeHome.getAbsolutePath()).build();

    assertNotNull(cluster);
    assertNotNull(cluster.getVersionRegistry().getStartDistribution());
    assertNotNull(cluster.getVersionRegistry().getUpgradeDistribution());
  }

  @Test
  public void testUpgradeWithoutStartedClusterThrows() throws IOException {
    File clusterDir = new File(tempDir, "cluster");
    assertTrue(clusterDir.mkdirs());

    ProcessBasedMiniAccumuloCluster cluster =
        new ProcessBasedMiniAccumuloCluster.Builder(clusterDir, "password")
            .accumuloStartDistribution(mockAccumuloHome.getAbsolutePath()).build();

    // Try to upgrade without starting
    assertThrows(IllegalStateException.class, () -> {
      cluster.upgrade();
    });
  }

  @Test
  public void testUpgradeWithoutUpgradeDistributionThrows()
      throws IOException, InterruptedException {
    File clusterDir = new File(tempDir, "cluster");
    assertTrue(clusterDir.mkdirs());

    ProcessBasedMiniAccumuloCluster cluster =
        new ProcessBasedMiniAccumuloCluster.Builder(clusterDir, "password")
            .accumuloStartDistribution(mockAccumuloHome.getAbsolutePath()).build();

    // Start the cluster (delegates to MiniAccumuloClusterImpl)
    cluster.start();

    try {
      // Try to upgrade without upgrade distribution configured
      assertThrows(IllegalStateException.class, () -> {
        cluster.upgrade();
      });
    } finally {
      cluster.stop();
    }
  }
}
