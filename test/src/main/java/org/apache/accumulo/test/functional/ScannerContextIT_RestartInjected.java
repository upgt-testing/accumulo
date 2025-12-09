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
package org.apache.accumulo.test.functional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.BatchScanner;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.core.util.UtilWaitThread;
import org.apache.accumulo.harness.AccumuloClusterHarness;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class ScannerContextIT_RestartInjected extends AccumuloClusterHarness {

  private static final String CONTEXT = ScannerContextIT_RestartInjected.class.getSimpleName();
  @SuppressWarnings("removal")
  private static final Property VFS_CONTEXT_CLASSPATH_PROPERTY =
      Property.VFS_CONTEXT_CLASSPATH_PROPERTY;
  private static final String CONTEXT_PROPERTY = VFS_CONTEXT_CLASSPATH_PROPERTY + CONTEXT;
  private static final String CONTEXT_DIR = "file://" + System.getProperty("user.dir") + "/target";
  private static final String CONTEXT_CLASSPATH = CONTEXT_DIR + "/Test.jar";
  private static int ITERATIONS = 10;
  private static final long WAIT = 7000;

  private FileSystem fs;

  @Override
  protected Duration defaultTimeout() {
    return Duration.ofMinutes(2);
  }

  @BeforeEach
  public void checkCluster() throws Exception {
    assumeTrue(getClusterType() == ClusterType.MINI);
    assertTrue(MiniAccumuloClusterImpl.class.isInstance(getCluster()));
    fs = FileSystem.get(cluster.getServerContext().getHadoopConf());
  }

  private Path copyTestIteratorsJarToTmp() throws IOException {
    // Copy the test iterators jar to tmp
    Path baseDir = new Path(System.getProperty("user.dir"));
    Path targetDir = new Path(baseDir, "target");
    Path jarPath = new Path(targetDir, "TestJar-Iterators.jar");
    Path dstPath = new Path(CONTEXT_DIR + "/Test.jar");
    fs.copyFromLocalFile(jarPath, dstPath);
    // Sleep to ensure jar change gets picked up
    UtilWaitThread.sleep(WAIT);
    return dstPath;
  }

  @Test
  public void test() throws Exception {
    Path dstPath = copyTestIteratorsJarToTmp();
    try (AccumuloClient c = Accumulo.newClient().from(getClientProps()).build()) {
      // Set the classloader context property on the table to point to the test iterators jar file.
      c.instanceOperations().setProperty(CONTEXT_PROPERTY, CONTEXT_CLASSPATH);

      RestartFramework.at("after_set_instance_property").on(getCluster()).restart("manager")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      // Insert rows with the word "Test" in the value.
      String tableName = getUniqueNames(1)[0];
      c.tableOperations().create(tableName);

      RestartFramework.at("after_table_create").on(getCluster()).restart("manager").withIndex(0)
          .withMode(RestartMode.GRACEFUL).execute();

      try (BatchWriter bw = c.createBatchWriter(tableName)) {
        for (int i = 0; i < ITERATIONS; i++) {
          Mutation m = new Mutation("row" + i);
          m.put("cf", "col1", "Test");
          bw.addMutation(m);
        }
      }

      RestartFramework.at("after_batch_write").on(getCluster()).restart("tablet_server")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      // Ensure that we can get the data back
      scanCheck(c, tableName, null, null, "Test");

      RestartFramework.at("after_scan_check_no_iterator").on(getCluster()).restart("tablet_server")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      batchCheck(c, tableName, null, null, "Test");

      RestartFramework.at("after_batch_check_no_iterator").on(getCluster()).restart("tablet_server")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      // This iterator is in the test iterators jar file
      IteratorSetting cfg = new IteratorSetting(21, "reverse",
          "org.apache.accumulo.test.functional.ValueReversingIterator");

      // Check that ValueReversingIterator is not already on the classpath by not setting the
      // context. This should fail.
      assertThrows(Exception.class, () -> scanCheck(c, tableName, cfg, null, "tseT"),
          "This should have failed because context was not set");

      assertThrows(Exception.class, () -> batchCheck(c, tableName, cfg, null, "tseT"),
          "This should have failed because context was not set");

      // Ensure that the value is reversed using the iterator config and classloader context
      scanCheck(c, tableName, cfg, CONTEXT, "tseT");

      RestartFramework.at("after_scan_check_with_context").on(getCluster()).restart("tablet_server")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      batchCheck(c, tableName, cfg, CONTEXT, "tseT");

      RestartFramework.at("after_batch_check_with_context").on(getCluster())
          .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    } finally {
      // Delete file in tmp
      fs.delete(dstPath, true);
    }
  }

  @Test
  public void testScanContextOverridesTableContext() throws Exception {
    Path dstPath = copyTestIteratorsJarToTmp();
    try (AccumuloClient c = Accumulo.newClient().from(getClientProps()).build()) {
      // Create two contexts FOO and ScanContextIT. The FOO context will point to a classpath
      // that contains nothing. The ScanContextIT context will point to the test iterators jar
      String tableContext = "FOO";
      String tableContextProperty = VFS_CONTEXT_CLASSPATH_PROPERTY + tableContext;
      String tableContextDir = "file://" + System.getProperty("user.dir") + "/target";
      String tableContextClasspath = tableContextDir + "/TestFoo.jar";
      // Define both contexts
      c.instanceOperations().setProperty(tableContextProperty, tableContextClasspath);
      c.instanceOperations().setProperty(CONTEXT_PROPERTY, CONTEXT_CLASSPATH);

      RestartFramework.at("after_set_instance_properties").on(getCluster()).restart("manager")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      String tableName = getUniqueNames(1)[0];
      c.tableOperations().create(tableName);

      RestartFramework.at("after_table_create_override").on(getCluster()).restart("manager")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      // Set the FOO context on the table
      c.tableOperations().setProperty(tableName, Property.TABLE_CLASSLOADER_CONTEXT.getKey(),
          tableContext);

      RestartFramework.at("after_table_property_set").on(getCluster()).restart("manager")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      try (BatchWriter bw = c.createBatchWriter(tableName)) {
        for (int i = 0; i < ITERATIONS; i++) {
          Mutation m = new Mutation("row" + i);
          m.put("cf", "col1", "Test");
          bw.addMutation(m);
        }
      }

      RestartFramework.at("after_batch_write_override").on(getCluster()).restart("tablet_server")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      scanCheck(c, tableName, null, null, "Test");

      RestartFramework.at("after_scan_check_override").on(getCluster()).restart("tablet_server")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      batchCheck(c, tableName, null, null, "Test");

      RestartFramework.at("after_batch_check_override").on(getCluster()).restart("tablet_server")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      // This iterator is in the test iterators jar file
      IteratorSetting cfg = new IteratorSetting(21, "reverse",
          "org.apache.accumulo.test.functional.ValueReversingIterator");

      // Check that ValueReversingIterator is not already on the classpath by not setting the
      // context. This should fail.
      assertThrows(Exception.class, () -> scanCheck(c, tableName, cfg, null, "tseT"),
          "This should have failed because context was not set");

      assertThrows(Exception.class, () -> batchCheck(c, tableName, cfg, null, "tseT"),
          "This should have failed because context was not set");

      // Ensure that the value is reversed using the iterator config and classloader context
      scanCheck(c, tableName, cfg, CONTEXT, "tseT");

      RestartFramework.at("after_scan_check_context_override").on(getCluster())
          .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      batchCheck(c, tableName, cfg, CONTEXT, "tseT");

      RestartFramework.at("after_batch_check_context_override").on(getCluster())
          .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    } finally {
      // Delete file in tmp
      fs.delete(dstPath, true);
    }

  }

  @Test
  public void testOneScannerDoesntInterfereWithAnother() throws Exception {
    Path dstPath = copyTestIteratorsJarToTmp();
    try (AccumuloClient c = Accumulo.newClient().from(getClientProps()).build()) {
      // Set the classloader context property on the table to point to the test iterators jar file.
      c.instanceOperations().setProperty(CONTEXT_PROPERTY, CONTEXT_CLASSPATH);

      RestartFramework.at("after_set_instance_property_interference").on(getCluster())
          .restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      // Insert rows with the word "Test" in the value.
      String tableName = getUniqueNames(1)[0];
      c.tableOperations().create(tableName);

      RestartFramework.at("after_table_create_interference").on(getCluster()).restart("manager")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      try (BatchWriter bw = c.createBatchWriter(tableName)) {
        for (int i = 0; i < ITERATIONS; i++) {
          Mutation m = new Mutation("row" + i);
          m.put("cf", "col1", "Test");
          bw.addMutation(m);
        }
      }

      RestartFramework.at("after_batch_write_interference").on(getCluster())
          .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      try (Scanner one = c.createScanner(tableName, Authorizations.EMPTY);
          Scanner two = c.createScanner(tableName, Authorizations.EMPTY)) {

        IteratorSetting cfg = new IteratorSetting(21, "reverse",
            "org.apache.accumulo.test.functional.ValueReversingIterator");
        one.addScanIterator(cfg);
        one.setClassLoaderContext(CONTEXT);

        RestartFramework.at("after_first_scanner_setup").on(getCluster()).restart("tablet_server")
            .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

        Iterator<Entry<Key,Value>> iterator = one.iterator();
        for (int i = 0; i < ITERATIONS; i++) {
          assertTrue(iterator.hasNext());
          Entry<Key,Value> next = iterator.next();
          assertEquals("tseT", next.getValue().toString());
        }

        RestartFramework.at("after_first_scanner_iteration").on(getCluster())
            .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();

        Iterator<Entry<Key,Value>> iterator2 = two.iterator();
        for (int i = 0; i < ITERATIONS; i++) {
          assertTrue(iterator2.hasNext());
          Entry<Key,Value> next = iterator2.next();
          assertEquals("Test", next.getValue().toString());
        }

        RestartFramework.at("after_second_scanner_iteration").on(getCluster())
            .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
      }
    } finally {
      // Delete file in tmp
      fs.delete(dstPath, true);
    }
  }

  @Test
  public void testClearContext() throws Exception {
    Path dstPath = copyTestIteratorsJarToTmp();
    try (AccumuloClient c = Accumulo.newClient().from(getClientProps()).build()) {
      // Set the classloader context property on the table to point to the test iterators jar file.
      c.instanceOperations().setProperty(CONTEXT_PROPERTY, CONTEXT_CLASSPATH);

      RestartFramework.at("after_set_instance_property_clear").on(getCluster()).restart("manager")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      // Insert rows with the word "Test" in the value.
      String tableName = getUniqueNames(1)[0];
      c.tableOperations().create(tableName);

      RestartFramework.at("after_table_create_clear").on(getCluster()).restart("manager")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      try (BatchWriter bw = c.createBatchWriter(tableName)) {
        for (int i = 0; i < ITERATIONS; i++) {
          Mutation m = new Mutation("row" + i);
          m.put("cf", "col1", "Test");
          bw.addMutation(m);
        }
      }

      RestartFramework.at("after_batch_write_clear").on(getCluster()).restart("tablet_server")
          .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

      try (Scanner one = c.createScanner(tableName, Authorizations.EMPTY)) {
        IteratorSetting cfg = new IteratorSetting(21, "reverse",
            "org.apache.accumulo.test.functional.ValueReversingIterator");
        one.addScanIterator(cfg);
        one.setClassLoaderContext(CONTEXT);

        RestartFramework.at("after_scanner_setup_with_context").on(getCluster())
            .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();

        Iterator<Entry<Key,Value>> iterator = one.iterator();
        for (int i = 0; i < ITERATIONS; i++) {
          assertTrue(iterator.hasNext());
          Entry<Key,Value> next = iterator.next();
          assertEquals("tseT", next.getValue().toString());
        }

        RestartFramework.at("after_scanner_iteration_with_context").on(getCluster())
            .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();

        one.removeScanIterator("reverse");
        one.clearClassLoaderContext();

        RestartFramework.at("after_clear_context").on(getCluster()).restart("tablet_server")
            .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

        iterator = one.iterator();
        for (int i = 0; i < ITERATIONS; i++) {
          assertTrue(iterator.hasNext());
          Entry<Key,Value> next = iterator.next();
          assertEquals("Test", next.getValue().toString());
        }

        RestartFramework.at("after_scanner_iteration_without_context").on(getCluster())
            .restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
      }
    } finally {
      // Delete file in tmp
      fs.delete(dstPath, true);
    }
  }

  private void scanCheck(AccumuloClient c, String tableName, IteratorSetting cfg, String context,
      String expected) throws Exception {
    try (Scanner bs = c.createScanner(tableName, Authorizations.EMPTY)) {
      if (context != null) {
        bs.setClassLoaderContext(context);
      }
      if (cfg != null) {
        bs.addScanIterator(cfg);
      }
      Iterator<Entry<Key,Value>> iterator = bs.iterator();
      for (int i = 0; i < ITERATIONS; i++) {
        assertTrue(iterator.hasNext());
        Entry<Key,Value> next = iterator.next();
        assertEquals(expected, next.getValue().toString());
      }
      assertFalse(iterator.hasNext());
    }
  }

  private void batchCheck(AccumuloClient c, String tableName, IteratorSetting cfg, String context,
      String expected) throws Exception {
    try (BatchScanner bs = c.createBatchScanner(tableName)) {
      bs.setRanges(Collections.singleton(new Range()));
      if (context != null) {
        bs.setClassLoaderContext(context);
      }
      if (cfg != null) {
        bs.addScanIterator(cfg);
      }
      Iterator<Entry<Key,Value>> iterator = bs.iterator();
      for (int i = 0; i < ITERATIONS; i++) {
        assertTrue(iterator.hasNext());
        Entry<Key,Value> next = iterator.next();
        assertEquals(expected, next.getValue().toString());
      }
      assertFalse(iterator.hasNext());
    }
  }

}
