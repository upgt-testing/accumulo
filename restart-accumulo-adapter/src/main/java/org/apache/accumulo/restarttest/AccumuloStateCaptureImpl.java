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
package org.apache.accumulo.restarttest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.TableId;
import org.apache.accumulo.core.clientImpl.ClientContext;
import org.apache.accumulo.core.metadata.schema.Ample;
import org.apache.accumulo.core.metadata.schema.TabletMetadata;
import org.apache.accumulo.core.metadata.schema.TabletsMetadata;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl;
import org.restarttest.state.AbstractStateCapture;
import org.restarttest.state.ClusterState;
import org.restarttest.state.DefaultClusterState;
import org.restarttest.state.StateVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.accumulo.core.metadata.schema.TabletMetadata.ColumnType.LOCATION;
import static org.apache.accumulo.core.metadata.schema.TabletMetadata.ColumnType.PREV_ROW;

/**
 * State capture for Accumulo cluster (Impl version).
 * Captures metadata about tables, tablets, and servers.
 */
public class AccumuloStateCaptureImpl extends AbstractStateCapture<MiniAccumuloClusterImpl> {

  private static final Logger log = LoggerFactory.getLogger(AccumuloStateCaptureImpl.class);

  @Override
  public ClusterState captureState(MiniAccumuloClusterImpl cluster) throws Exception {
    Map<String, Object> state = new HashMap<>();

    AccumuloClient client = cluster.createAccumuloClient("root",
        new PasswordToken(cluster.getConfig().getRootPassword()));

    try {
      log.info("Capturing cluster state");

      // Capture server counts
      List<String> tservers = client.instanceOperations().getTabletServers();
      state.put("tablet_server_count", tservers.size());
      state.put("tablet_servers", new ArrayList<>(tservers));

      Set<String> scanServers = client.instanceOperations().getScanServers();
      state.put("scan_server_count", scanServers.size());

      Set<String> compactors = client.instanceOperations().getCompactors();
      state.put("compactor_count", compactors.size());

      // Capture table information
      SortedSet<String> tables = client.tableOperations().list();
      state.put("table_count", tables.size());
      state.put("table_names", new ArrayList<>(tables));

      Map<String, String> tableIdMap = client.tableOperations().tableIdMap();
      state.put("table_ids", new HashMap<>(tableIdMap));

      // Capture tablet counts per table
      Map<String, Integer> tabletCounts = captureTabletCounts(client, tables);
      state.put("tablet_counts", tabletCounts);

      log.info("State captured: {} tables, {} tablet servers, {} tablets total",
          tables.size(), tservers.size(), tabletCounts.values().stream().mapToInt(Integer::intValue).sum());

    } finally {
      client.close();
    }

    return new DefaultClusterState(state);
  }

  @Override
  protected void verifyCustomInvariants(MiniAccumuloClusterImpl cluster,
                                       ClusterState before,
                                       ClusterState after) throws Exception {
    log.info("Verifying cluster state invariants");

    // Verify server counts preserved
    compareStateValue("tablet_server_count", before, after, false);

    // Verify no tables lost (but new tables may be created)
    compareStateValue("table_count", before, after, true);

    // Verify table IDs unchanged
    verifyTableIds(before, after);

    // Verify no tablets lost
    verifyTabletCounts(before, after);

    log.info("All state invariants verified successfully");
  }

  /**
   * Capture tablet counts for all tables.
   */
  private Map<String, Integer> captureTabletCounts(AccumuloClient client, SortedSet<String> tables)
      throws Exception {
    Map<String, Integer> tabletCounts = new HashMap<>();
    ClientContext ctx = (ClientContext) client;
    Ample ample = ctx.getAmple();

    for (String tableName : tables) {
      try {
        TableId tableId = ctx.getTableId(tableName);
        int count = 0;

        try (TabletsMetadata tablets = ample.readTablets()
            .forTable(tableId)
            .fetch(PREV_ROW)
            .build()) {

          for (@SuppressWarnings("unused") TabletMetadata tablet : tablets) {
            count++;
          }
        }

        tabletCounts.put(tableName, count);
      } catch (Exception e) {
        log.warn("Failed to get tablet count for table {}: {}", tableName, e.getMessage());
      }
    }

    return tabletCounts;
  }

  /**
   * Verify table IDs are unchanged.
   */
  @SuppressWarnings("unchecked")
  private void verifyTableIds(ClusterState before, ClusterState after)
      throws StateVerificationException {
    Map<String, String> beforeIds = (Map<String, String>) before.getStateMap().get("table_ids");
    Map<String, String> afterIds = (Map<String, String>) after.getStateMap().get("table_ids");

    if (beforeIds == null || afterIds == null) {
      return;
    }

    for (Map.Entry<String, String> entry : beforeIds.entrySet()) {
      String tableName = entry.getKey();
      String beforeId = entry.getValue();
      String afterId = afterIds.get(tableName);

      if (afterId == null) {
        throw new StateVerificationException(
            "Table disappeared after restart: " + tableName);
      }

      if (!beforeId.equals(afterId)) {
        throw new StateVerificationException(
            "Table ID changed for " + tableName +
            " (before=" + beforeId + ", after=" + afterId + ")");
      }
    }
  }

  /**
   * Verify tablet counts have not decreased.
   */
  @SuppressWarnings("unchecked")
  private void verifyTabletCounts(ClusterState before, ClusterState after)
      throws StateVerificationException {
    Map<String, Integer> beforeTablets = (Map<String, Integer>)
        before.getStateMap().get("tablet_counts");
    Map<String, Integer> afterTablets = (Map<String, Integer>)
        after.getStateMap().get("tablet_counts");

    if (beforeTablets == null || afterTablets == null) {
      return;
    }

    for (Map.Entry<String, Integer> entry : beforeTablets.entrySet()) {
      String tableName = entry.getKey();
      Integer beforeCount = entry.getValue();
      Integer afterCount = afterTablets.get(tableName);

      if (afterCount == null) {
        throw new StateVerificationException(
            "Table " + tableName + " has no tablets after restart");
      }

      if (afterCount < beforeCount) {
        throw new StateVerificationException(
            "Tablet count decreased for " + tableName +
            " (before=" + beforeCount + ", after=" + afterCount + ")");
      }
    }
  }
}
