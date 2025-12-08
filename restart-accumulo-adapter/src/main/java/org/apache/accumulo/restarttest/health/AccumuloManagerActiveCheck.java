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

import java.util.List;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

/**
 * Health check to verify that the Accumulo manager is active.
 */
public class AccumuloManagerActiveCheck implements HealthCheck<MiniAccumuloCluster> {

  @Override
  public HealthCheckResult checkHealth(MiniAccumuloCluster cluster) throws Exception {
    HealthCheckResult result = new HealthCheckResult(true, getName());

    AccumuloClient client = cluster.createAccumuloClient("root",
        new PasswordToken(cluster.getConfig().getRootPassword()));

    try {
      List<String> managers = client.instanceOperations().getManagerLocations();
      result.addMetric("manager_count", managers.size());

      if (managers.isEmpty()) {
        result.addFailure("No manager found");
      } else {
        result.addMetric("manager_location", managers.get(0));
      }
    } finally {
      client.close();
    }

    return result;
  }

  @Override
  public String getName() {
    return "accumulo-manager-active";
  }
}
