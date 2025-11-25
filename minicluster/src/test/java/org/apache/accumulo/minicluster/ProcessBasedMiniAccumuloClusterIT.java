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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.Map;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * End-to-end integration test for ProcessBasedMiniAccumuloCluster. This test actually starts a
 * cluster with real Accumulo distributions and performs operations.
 *
 * <p>
 * Requirements: - Accumulo 2.1.2 distribution must be available at
 * /Users/allenwang/xlab/accumulo-test-distributions/accumulo-2.1.2
 */
@Tag("integration-test")
@SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "paths not set by user input")
public class ProcessBasedMiniAccumuloClusterIT {
  private static final Logger log =
      LoggerFactory.getLogger(ProcessBasedMiniAccumuloClusterIT.class);

  @TempDir
  private File tempDir;

  private ProcessBasedMiniAccumuloCluster cluster;

  // Distribution paths
  private static final String ACCUMULO_2_0_1 =
      "/Users/allenwang/xlab/accumulo-test-distributions/accumulo-2.0.1";
  private static final String ACCUMULO_2_1_2 =
      "/Users/allenwang/xlab/accumulo-test-distributions/accumulo-2.1.2";

  @BeforeEach
  public void setUp() {
    // Check if distribution exists
    File distDir = new File(ACCUMULO_2_1_2);
    assumeTrue(distDir.exists() && distDir.isDirectory(),
        "Accumulo 2.1.2 distribution not found at " + ACCUMULO_2_1_2);

    log.info("Using Accumulo distribution at: {}", ACCUMULO_2_1_2);
  }

  @AfterEach
  public void tearDown() {
    if (cluster != null) {
      try {
        cluster.stop();
      } catch (Exception e) {
        log.warn("Error stopping cluster", e);
      }
    }
    if (tempDir != null && tempDir.exists()) {
      FileUtils.deleteQuietly(tempDir);
    }
  }

  @Test
  public void testBasicClusterStartup() throws Exception {
    log.info("=== Test: Basic Cluster Startup ===");

    // Build cluster with 2.1.2 distribution
    cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "password")
        .accumuloStartDistribution(ACCUMULO_2_1_2).numTabletServers(2).build();

    log.info("Starting cluster...");
    cluster.start();
    log.info("Cluster started successfully");

    // Verify we can get an instance name
    String instanceName = cluster.getInstanceName();
    assertNotNull(instanceName);
    log.info("Instance name: {}", instanceName);

    // Verify we can get zookeeper connection
    String zooKeepers = cluster.getZooKeepers();
    assertNotNull(zooKeepers);
    log.info("ZooKeepers: {}", zooKeepers);

    log.info("=== Test Passed: Basic Cluster Startup ===");
  }

  @Test
  public void testTableOperations() throws Exception {
    log.info("=== Test: Table Operations ===");

    // Build and start cluster
    cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "password")
        .accumuloStartDistribution(ACCUMULO_2_1_2).numTabletServers(2).build();

    log.info("Starting cluster...");
    cluster.start();
    log.info("Cluster started successfully");

    // Create client
    try (AccumuloClient client =
        cluster.createAccumuloClient("root", new PasswordToken("password"))) {

      log.info("Creating table 'test'...");
      client.tableOperations().create("test");

      // Verify table was created
      assertTrue(client.tableOperations().exists("test"));
      log.info("Table 'test' created successfully");

      // Write some data
      log.info("Writing data to table...");
      try (BatchWriter writer = client.createBatchWriter("test")) {
        for (int i = 0; i < 10; i++) {
          Mutation m = new Mutation("row" + i);
          m.put("cf1", "cq1", "value" + i);
          m.put("cf1", "cq2", "value" + (i * 2));
          writer.addMutation(m);
        }
      }
      log.info("Data written successfully");

      // Read data back
      log.info("Reading data from table...");
      int count = 0;
      try (Scanner scanner = client.createScanner("test", Authorizations.EMPTY)) {
        for (Map.Entry<Key,Value> entry : scanner) {
          count++;
          log.debug("Read: {} -> {}", entry.getKey(), entry.getValue());
        }
      }

      // We wrote 10 rows with 2 columns each = 20 entries
      assertEquals(20, count, "Should have read 20 entries");
      log.info("Read {} entries successfully", count);

      log.info("=== Test Passed: Table Operations ===");
    }
  }

  @Test
  public void testMultipleTableOperations() throws Exception {
    log.info("=== Test: Multiple Table Operations ===");

    // Build and start cluster
    cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "password")
        .accumuloStartDistribution(ACCUMULO_2_1_2).numTabletServers(2).numScanServers(1).build();

    log.info("Starting cluster...");
    cluster.start();
    log.info("Cluster started successfully");

    try (AccumuloClient client =
        cluster.createAccumuloClient("root", new PasswordToken("password"))) {

      // Create multiple tables
      String[] tables = {"table1", "table2", "table3"};
      for (String table : tables) {
        log.info("Creating table '{}'...", table);
        client.tableOperations().create(table);
        assertTrue(client.tableOperations().exists(table));
      }
      log.info("All tables created successfully");

      // Write data to each table
      for (String table : tables) {
        log.info("Writing data to '{}'...", table);
        try (BatchWriter writer = client.createBatchWriter(table)) {
          Mutation m = new Mutation("row1");
          m.put("cf", "cq", table + "_data");
          writer.addMutation(m);
        }
      }
      log.info("Data written to all tables");

      // Verify data in each table
      for (String table : tables) {
        log.info("Verifying data in '{}'...", table);
        try (Scanner scanner = client.createScanner(table, Authorizations.EMPTY)) {
          int count = 0;
          for (Map.Entry<Key,Value> entry : scanner) {
            count++;
            String expectedValue = table + "_data";
            assertEquals(expectedValue, entry.getValue().toString());
          }
          assertEquals(1, count, "Should have 1 entry in " + table);
        }
      }
      log.info("All tables verified successfully");

      log.info("=== Test Passed: Multiple Table Operations ===");
    }
  }

  @Test
  public void testClusterRestart() throws Exception {
    log.info("=== Test: Cluster Restart ===");

    // Build and start cluster
    cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "password")
        .accumuloStartDistribution(ACCUMULO_2_1_2).numTabletServers(1).build();

    log.info("Starting cluster (first time)...");
    cluster.start();
    log.info("Cluster started successfully");

    // Create table and write data
    try (AccumuloClient client =
        cluster.createAccumuloClient("root", new PasswordToken("password"))) {
      log.info("Creating table and writing data...");
      client.tableOperations().create("persistent_table");
      try (BatchWriter writer = client.createBatchWriter("persistent_table")) {
        Mutation m = new Mutation("persistent_row");
        m.put("cf", "cq", "persistent_value");
        writer.addMutation(m);
      }
      log.info("Data written successfully");
    }

    // Stop cluster
    log.info("Stopping cluster...");
    cluster.stop();
    log.info("Cluster stopped successfully");

    // Restart cluster
    log.info("Starting cluster (second time)...");
    cluster.start();
    log.info("Cluster restarted successfully");

    // Verify data persisted
    try (AccumuloClient client =
        cluster.createAccumuloClient("root", new PasswordToken("password"))) {
      log.info("Verifying data persisted...");
      assertTrue(client.tableOperations().exists("persistent_table"));

      try (Scanner scanner = client.createScanner("persistent_table", Authorizations.EMPTY)) {
        int count = 0;
        for (Map.Entry<Key,Value> entry : scanner) {
          count++;
          assertEquals("persistent_value", entry.getValue().toString());
        }
        assertEquals(1, count, "Should have 1 entry after restart");
      }
      log.info("Data persisted successfully across restart");
    }

    log.info("=== Test Passed: Cluster Restart ===");
  }

  @Test
  public void testVersionRegistry() throws Exception {
    log.info("=== Test: Version Registry ===");

    // Build cluster
    cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "password")
        .accumuloStartDistribution(ACCUMULO_2_1_2).numTabletServers(1).build();

    // Verify version registry was initialized
    assertNotNull(cluster.getVersionRegistry());
    assertNotNull(cluster.getVersionRegistry().getStartDistribution());

    log.info("Start distribution: {}",
        cluster.getVersionRegistry().getStartDistribution().getVersion());
    assertEquals(new File(ACCUMULO_2_1_2),
        cluster.getVersionRegistry().getStartDistribution().getAccumuloHome());

    log.info("=== Test Passed: Version Registry ===");
  }

  @Test
  public void testRollingUpgrade() throws Exception {
    log.info("=== Test: Rolling Upgrade (2.1.2 -> 2.1.2) ===");

    // Check if distribution exists
    File dist212 = new File(ACCUMULO_2_1_2);
    assumeTrue(dist212.exists() && dist212.isDirectory(),
        "Accumulo 2.1.2 distribution not found at " + ACCUMULO_2_1_2);

    // Build cluster with 2.1.2 as both start and upgrade distributions
    // This tests the upgrade mechanism works correctly even with same version
    log.info("Building cluster with start=2.1.2, upgrade=2.1.2");
    cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "password")
        .accumuloStartDistribution(ACCUMULO_2_1_2).accumuloUpgradeDistribution(ACCUMULO_2_1_2)
        .numTabletServers(2).build();

    // Verify both distributions are registered
    assertNotNull(cluster.getVersionRegistry().getStartDistribution());
    assertNotNull(cluster.getVersionRegistry().getUpgradeDistribution());
    log.info("Start distribution: {}",
        cluster.getVersionRegistry().getStartDistribution().getVersion());
    log.info("Upgrade distribution: {}",
        cluster.getVersionRegistry().getUpgradeDistribution().getVersion());

    // Start cluster with 2.1.2
    log.info("Starting cluster with Accumulo 2.1.2...");
    cluster.start();
    log.info("Cluster started successfully with 2.1.2");

    // Create table and write data with initial cluster
    try (AccumuloClient client =
        cluster.createAccumuloClient("root", new PasswordToken("password"))) {

      log.info("Creating table 'upgrade_test' and writing data...");
      client.tableOperations().create("upgrade_test");

      // Write test data
      try (BatchWriter writer = client.createBatchWriter("upgrade_test")) {
        for (int i = 0; i < 100; i++) {
          Mutation m = new Mutation("row" + i);
          m.put("cf", "cq", "value_before_upgrade_" + i);
          writer.addMutation(m);
        }
      }
      log.info("Wrote 100 rows before upgrade");

      // Verify data before upgrade
      int countBefore = 0;
      try (Scanner scanner = client.createScanner("upgrade_test", Authorizations.EMPTY)) {
        for (Map.Entry<Key,Value> entry : scanner) {
          countBefore++;
        }
      }
      assertEquals(100, countBefore, "Should have 100 entries before upgrade");
      log.info("Verified {} entries before upgrade", countBefore);

      // Perform rolling upgrade
      log.info("Performing rolling upgrade...");
      cluster.upgrade();
      log.info("Rolling upgrade completed successfully");

      // Verify data after upgrade
      log.info("Verifying data persisted after upgrade...");
      assertTrue(client.tableOperations().exists("upgrade_test"),
          "Table should still exist after upgrade");

      int countAfter = 0;
      try (Scanner scanner = client.createScanner("upgrade_test", Authorizations.EMPTY)) {
        for (Map.Entry<Key,Value> entry : scanner) {
          countAfter++;
          // Verify data content is intact
          assertTrue(entry.getValue().toString().startsWith("value_before_upgrade_"),
              "Data should be intact after upgrade");
        }
      }
      assertEquals(100, countAfter, "Should have 100 entries after upgrade");
      log.info("Verified {} entries after upgrade - all data intact!", countAfter);

      // Write new data after upgrade
      log.info("Writing new data after upgrade...");
      try (BatchWriter writer = client.createBatchWriter("upgrade_test")) {
        for (int i = 100; i < 110; i++) {
          Mutation m = new Mutation("row" + i);
          m.put("cf", "cq", "value_after_upgrade_" + i);
          writer.addMutation(m);
        }
      }
      log.info("Wrote 10 additional rows after upgrade");

      // Verify total data
      int totalCount = 0;
      try (Scanner scanner = client.createScanner("upgrade_test", Authorizations.EMPTY)) {
        for (Map.Entry<Key,Value> entry : scanner) {
          totalCount++;
        }
      }
      assertEquals(110, totalCount, "Should have 110 total entries after adding new data");
      log.info("Verified {} total entries - upgrade successful!", totalCount);
    }

    log.info("=== Test Passed: Rolling Upgrade (2.1.2 -> 2.1.2) ===");
  }
}
