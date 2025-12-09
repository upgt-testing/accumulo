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
package org.apache.accumulo.test.compaction;

import static org.apache.accumulo.core.util.UtilWaitThread.sleepUninterruptibly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.EnumSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.IteratorUtil.IteratorScope;
import org.apache.accumulo.harness.SharedMiniClusterBase;
import org.apache.accumulo.test.functional.SlowIterator;
import org.apache.hadoop.io.Text;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

// ACCUMULO-2862
public class SplitCancelsMajCIT_RestartInjected extends SharedMiniClusterBase {

  @Override
  protected Duration defaultTimeout() {
    return Duration.ofMinutes(2);
  }

  @BeforeAll
  public static void setup() throws Exception {
    SharedMiniClusterBase.startMiniCluster();
  }

  @AfterAll
  public static void teardown() {
    SharedMiniClusterBase.stopMiniCluster();
  }

  @Test
  public void test() throws Exception {
    final String tableName = getUniqueNames(1)[0];
    try (AccumuloClient c = Accumulo.newClient().from(getClientProps()).build()) {
      c.tableOperations().create(tableName);
      RestartFramework.at("after_table_create")
          .on(cluster)
          .restart("manager")
          .withIndex(0)
          .withMode(RestartMode.GRACEFUL)
          .execute();
      // majc should take 100 * .5 secs
      IteratorSetting it = new IteratorSetting(100, SlowIterator.class);
      SlowIterator.setSleepTime(it, 500);
      c.tableOperations().attachIterator(tableName, it, EnumSet.of(IteratorScope.majc));
      RestartFramework.at("after_iterator_attach")
          .on(cluster)
          .restart("manager")
          .withIndex(0)
          .withMode(RestartMode.GRACEFUL)
          .execute();
      try (BatchWriter bw = c.createBatchWriter(tableName)) {
        for (int i = 0; i < 100; i++) {
          Mutation m = new Mutation("" + i);
          m.put("", "", new Value());
          bw.addMutation(m);
        }
        bw.flush();
      }
      RestartFramework.at("after_data_flush")
          .on(cluster)
          .restart("tablet_server")
          .withIndex(0)
          .withMode(RestartMode.GRACEFUL)
          .execute();
      // start majc
      final AtomicReference<Exception> ex = new AtomicReference<>();
      Thread thread = new Thread(() -> {
        try {
          c.tableOperations().compact(tableName, null, null, true, true);
        } catch (Exception e) {
          ex.set(e);
        }
      });
      thread.start();
      RestartFramework.at("after_compaction_start")
          .on(cluster)
          .restart("tablet_server")
          .withIndex(0)
          .withMode(RestartMode.GRACEFUL)
          .execute();

      long now = System.currentTimeMillis();
      sleepUninterruptibly(10, TimeUnit.SECONDS);
      // split the table, interrupts the compaction
      SortedSet<Text> partitionKeys = new TreeSet<>();
      partitionKeys.add(new Text("10"));
      c.tableOperations().addSplits(tableName, partitionKeys);
      RestartFramework.at("after_split")
          .on(cluster)
          .restart("manager")
          .withIndex(0)
          .withMode(RestartMode.GRACEFUL)
          .execute();
      thread.join();
      // wait for the restarted compaction
      assertTrue(System.currentTimeMillis() - now > 59_000);
      if (ex.get() != null) {
        throw ex.get();
      }
    }
  }
}
