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

import static org.apache.accumulo.core.util.UtilWaitThread.sleepUninterruptibly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl;
import org.apache.accumulo.server.util.Admin;
import org.apache.accumulo.test.TestIngest;
import org.apache.accumulo.test.TestRandomDeletes;
import org.apache.accumulo.test.VerifyIngest;
import org.apache.accumulo.test.util.Wait;
import org.junit.jupiter.api.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

public class ShutdownIT_RestartInjected_Randomized_123 extends ConfigurableMacBase {

    @Override
    protected Duration defaultTimeout() {
        return Duration.ofMinutes(2);
    }

    @Test
    public void shutdownDuringIngest() throws Exception {
        RestartFramework.at("after_stopAll_during_ingest").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        Process ingest = cluster.exec(TestIngest.class, "-c", cluster.getClientPropsPath(), "--createTable").getProcess();
        sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        assertEquals(0, cluster.exec(Admin.class, "stopAll").getProcess().waitFor());
        RestartFramework.at("after_sleep_during_ingest").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_ingest_process_start").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        ingest.destroy();
    }

    @Test
    public void shutdownDuringQuery() throws Exception {
        RestartFramework.at("after_ingest_completion_query").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, cluster.exec(TestIngest.class, "-c", cluster.getClientPropsPath(), "--createTable").getProcess().waitFor());
        Process verify = cluster.exec(VerifyIngest.class, "-c", cluster.getClientPropsPath()).getProcess();
        RestartFramework.at("after_sleep_during_query").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_verify_process_start").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        assertEquals(0, cluster.exec(Admin.class, "stopAll").getProcess().waitFor());
        RestartFramework.at("after_stopAll_during_query").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        verify.destroy();
    }

    @Test
    public void shutdownDuringDelete() throws Exception {
        assertEquals(0, cluster.exec(TestIngest.class, "-c", cluster.getClientPropsPath(), "--createTable").getProcess().waitFor());
        Process deleter = cluster.exec(TestRandomDeletes.class, "-c", cluster.getClientPropsPath()).getProcess();
        RestartFramework.at("after_deleter_process_start").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_stopAll_during_delete").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_ingest_completion_delete").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        assertEquals(0, cluster.exec(Admin.class, "stopAll").getProcess().waitFor());
        RestartFramework.at("after_sleep_during_delete").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        deleter.destroy();
    }

    @Test
    public void shutdownDuringDeleteTable() throws Exception {
        try (AccumuloClient c = Accumulo.newClient().from(getClientProperties()).build()) {
            for (int i = 0; i < 10; i++) {
                c.tableOperations().create("table" + i);
            }
            RestartFramework.at("after_tables_create").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            final AtomicReference<Exception> ref = new AtomicReference<>();
            Thread async = new Thread(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        c.tableOperations().delete("table" + i);
                    }
                } catch (Exception ex) {
                    ref.set(ex);
                }
            });
            async.start();
            RestartFramework.at("after_async_delete_start").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            RestartFramework.at("after_sleep_during_delete_table").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            assertEquals(0, cluster.exec(Admin.class, "stopAll").getProcess().waitFor());
            RestartFramework.at("after_stopAll_during_delete_table").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
            if (ref.get() != null) {
                throw ref.get();
            }
        }
    }

    @Test
    public void stopDuringStart() throws Exception {
        assertEquals(0, cluster.exec(Admin.class, "stopAll").getProcess().waitFor());
        RestartFramework.at("after_stopAll_during_start").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
    }

    @Test
    public void adminStop() throws Exception {
        try (AccumuloClient c = Accumulo.newClient().from(getClientProperties()).build()) {
            runAdminStopTest(c, cluster);
        }
    }

    void runAdminStopTest(AccumuloClient c, MiniAccumuloClusterImpl cluster) throws InterruptedException, IOException {
        int x = cluster.exec(TestIngest.class, "-c", cluster.getClientPropsPath(), "--createTable").getProcess().waitFor();
        RestartFramework.at("after_admin_stop_tserver").on(getCluster()).restart("manager").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, x);
        RestartFramework.at("after_wait_for_tserver_removal").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        List<String> tabletServers = c.instanceOperations().getTabletServers();
        assertEquals(2, tabletServers.size());
        String doomed = tabletServers.get(0);
        log.info("Stopping " + doomed);
        RestartFramework.at("after_get_tablet_servers").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        assertEquals(0, cluster.exec(Admin.class, "stop", doomed).getProcess().waitFor());
        Wait.waitFor(() -> c.instanceOperations().getTabletServers().size() == 1);
        RestartFramework.at("after_ingest_completion_admin_stop").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        RestartFramework.at("after_final_verification_admin_stop").on(getCluster()).restart("tablet_server").withIndex(0).withMode(RestartMode.GRACEFUL).execute();
        tabletServers = c.instanceOperations().getTabletServers();
        assertEquals(1, tabletServers.size());
        assertNotEquals(tabletServers.get(0), doomed);
    }
}
