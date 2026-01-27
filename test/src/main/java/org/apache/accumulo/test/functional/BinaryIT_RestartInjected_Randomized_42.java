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

import java.time.Duration;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.accumulo.cluster.AccumuloCluster;
import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.admin.NewTableConfiguration;
import org.apache.accumulo.harness.AccumuloClusterHarness;
import org.apache.accumulo.test.TestBinaryRows;
import org.apache.hadoop.io.Text;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class BinaryIT_RestartInjected_Randomized_42 extends AccumuloClusterHarness {

    @Override
    protected Duration defaultTimeout() {
        return Duration.ofSeconds(90);
    }

    @Test
    public void test() throws Exception {
        try (AccumuloClient c = Accumulo.newClient().from(getClientProps()).build()) {
            String tableName = getUniqueNames(1)[0];
            c.tableOperations().create(tableName);
            RestartFramework.at("after_table_create").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            runTest(c, tableName, cluster);
            RestartFramework.at("after_test_completion").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }

    @Test
    public void testPreSplit() throws Exception {
        String tableName = getUniqueNames(1)[0];
        try (AccumuloClient c = Accumulo.newClient().from(getClientProps()).build()) {
            SortedSet<Text> splits = new TreeSet<>();
            splits.add(new Text("8"));
            splits.add(new Text("256"));
            NewTableConfiguration ntc = new NewTableConfiguration().withSplits(splits);
            c.tableOperations().create(tableName, ntc);
            RestartFramework.at("after_presplit_table_create").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            runTest(c, tableName, cluster);
            RestartFramework.at("after_presplit_test_completion").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        }
    }

    public static void runTest(AccumuloClient c, String tableName, AccumuloCluster cluster) throws Exception {
        TestBinaryRows.Opts opts = new TestBinaryRows.Opts();
        opts.tableName = tableName;
        opts.start = 0;
        opts.num = 100000;
        opts.mode = "ingest";
        TestBinaryRows.runTest(c, opts);
        opts.mode = "verify";
        TestBinaryRows.runTest(c, opts);
        RestartFramework.at("after_delete").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_second_verify").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        opts.start = 25000;
        opts.num = 50000;
        RestartFramework.at("after_ingest").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        opts.mode = "delete";
        RestartFramework.at("after_first_verify").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        TestBinaryRows.runTest(c, opts);
        RestartFramework.at("after_random_lookups").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        opts.start = 0;
        opts.num = 25000;
        opts.mode = "verify";
        TestBinaryRows.runTest(c, opts);
        opts.start = 75000;
        opts.num = 25000;
        opts.mode = "randomLookups";
        TestBinaryRows.runTest(c, opts);
        opts.start = 25000;
        opts.num = 50000;
        opts.mode = "verifyDeleted";
        TestBinaryRows.runTest(c, opts);
    }
}
