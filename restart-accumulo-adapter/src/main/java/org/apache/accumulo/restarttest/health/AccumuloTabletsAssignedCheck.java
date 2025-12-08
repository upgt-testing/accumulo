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
package org.apache.accumulo.restarttest.health;

import java.util.SortedSet;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.TableId;
import org.apache.accumulo.core.clientImpl.ClientContext;
import org.apache.accumulo.core.metadata.schema.Ample;
import org.apache.accumulo.core.metadata.schema.TabletMetadata;
import org.apache.accumulo.core.metadata.schema.TabletsMetadata;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

import static org.apache.accumulo.core.metadata.schema.TabletMetadata.ColumnType.LOCATION;
import static org.apache.accumulo.core.metadata.schema.TabletMetadata.ColumnType.PREV_ROW;

/**
 * Health check to verify that all tablets are assigned to tablet servers.
 */
public class AccumuloTabletsAssignedCheck implements HealthCheck<MiniAccumuloCluster> {

  @Override
  public HealthCheckResult checkHealth(MiniAccumuloCluster cluster) throws Exception {
    HealthCheckResult result = new HealthCheckResult(true, getName());

    AccumuloClient client = cluster.createAccumuloClient("root",
        new PasswordToken(cluster.getConfig().getRootPassword()));

    try {
      ClientContext ctx = (ClientContext) client;
      Ample ample = ctx.getAmple();

      int totalTablets = 0;
      int unassignedTablets = 0;

      SortedSet<String> tables = client.tableOperations().list();

      for (String tableName : tables) {
        try {
          TableId tableId = ctx.getTableId(tableName);

          try (TabletsMetadata tablets = ample.readTablets()
              .forTable(tableId)
              .fetch(LOCATION, PREV_ROW)
              .build()) {

            for (TabletMetadata tablet : tablets) {
              totalTablets++;
              if (tablet.getLocation() == null) {
                unassignedTablets++;
              }
            }
          }
        } catch (Exception e) {
          // Skip tables that can't be read
        }
      }

      result.addMetric("total_tablets", totalTablets);
      result.addMetric("unassigned_tablets", unassignedTablets);

      if (unassignedTablets > 0) {
        result.addFailure(unassignedTablets + " tablets are not assigned");
      }
    } finally {
      client.close();
    }

    return result;
  }

  @Override
  public String getName() {
    return "accumulo-tablets-assigned";
  }
}
