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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.admin.NewTableConfiguration;
import org.apache.accumulo.core.client.admin.TimeType;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.harness.AccumuloClusterHarness;
import org.apache.accumulo.minicluster.ServerType;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl;
import org.apache.accumulo.miniclusterImpl.ProcessReference;
import org.apache.hadoop.io.Text;
import org.junit.jupiter.api.Test;

public class DebugDataLossIT extends AccumuloClusterHarness {

  @Override
  protected Duration defaultTimeout() {
    return Duration.ofMinutes(5);
  }

  @Test
  public void testDataLossAfterRestart() throws Exception {
    String tableName = getUniqueNames(1)[0];

    try (AccumuloClient client = Accumulo.newClient().from(getClientProps()).build()) {
      TreeSet<Text> splits = new TreeSet<>();
      splits.add(new Text("m"));
      client.tableOperations().create(tableName,
          new NewTableConfiguration().setTimeType(TimeType.LOGICAL).withSplits(splits));

      BatchWriter bw = client.createBatchWriter(tableName);

      Mutation m1 = new Mutation("a");
      m1.put("cf", "cq", "v");
      bw.addMutation(m1);
      bw.flush();
      System.out.println("DEBUG: Wrote and flushed row 'a'");

      client.tableOperations().merge(tableName, null, null);
      System.out.println("DEBUG: Merge completed");

      Mutation m2 = new Mutation("b");
      m2.put("cf", "cq", "v");
      bw.addMutation(m2);
      bw.flush();
      System.out.println("DEBUG: Wrote and flushed row 'b'");

      // Close BatchWriter BEFORE restart to ensure all data is committed
      bw.close();
      System.out.println("DEBUG: BatchWriter closed");

      // Verify data exists before restart
      try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
        int count = 0;
        for (var entry : scanner) {
          System.out.println("DEBUG: Before restart - found row: " + entry.getKey().getRow());
          count++;
        }
        System.out.println("DEBUG: Before restart - total rows: " + count);
      }

      MiniAccumuloClusterImpl cluster = (MiniAccumuloClusterImpl) getCluster();
      List<ProcessReference> tservers =
          new ArrayList<>(cluster.getProcesses().get(ServerType.TABLET_SERVER));

      if (!tservers.isEmpty()) {
        ProcessReference tserver = tservers.get(0);
        System.out.println("DEBUG: Killing tablet server...");

        long startKill = System.currentTimeMillis();
        cluster.killProcess(ServerType.TABLET_SERVER, tserver);
        long killTime = System.currentTimeMillis() - startKill;
        System.out.println("DEBUG: Kill completed in " + killTime + "ms");

        System.out.println("DEBUG: Starting new tablet server...");
        cluster.getClusterControl().start(ServerType.TABLET_SERVER);
        System.out.println("DEBUG: New tablet server started");

        System.out.println("DEBUG: Waiting for balance...");
        client.instanceOperations().waitForBalance();
        System.out.println("DEBUG: Balance complete");

        // Additional wait for WAL recovery
        Thread.sleep(5000);
        System.out.println("DEBUG: Additional 5s wait complete");
      }

      // Scan for data
      try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
        scanner.setRange(new Range("b"));

        if (!scanner.iterator().hasNext()) {
          try (Scanner fullScan = client.createScanner(tableName, Authorizations.EMPTY)) {
            fullScan.setRange(new Range());
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (var entry : fullScan) {
              sb.append(entry.getKey().getRow()).append(" ");
              count++;
            }
            fail("DATA LOSS DETECTED: Row 'b' not found after restart. Full table scan found "
                + count + " entries: " + sb.toString());
          }
        }

        long time = scanner.iterator().next().getKey().getTimestamp();
        assertTrue(time > 0, "Expected positive timestamp");
        System.out.println("DEBUG: SUCCESS - Found row 'b' with timestamp " + time);
      }
    }
  }
}
