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

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

/**
 * Health check to verify that the cluster is balanced.
 * Note: This check may take some time as it waits for the balancer to complete.
 */
public class AccumuloClusterBalancedCheck implements HealthCheck<MiniAccumuloCluster> {

  private static final long BALANCE_TIMEOUT_MS = 30000; // 30 seconds

  @Override
  public HealthCheckResult checkHealth(MiniAccumuloCluster cluster) throws Exception {
    HealthCheckResult result = new HealthCheckResult(true, getName());

    AccumuloClient client = cluster.createAccumuloClient("root",
        new PasswordToken(cluster.getConfig().getRootPassword()));

    try {
      long start = System.currentTimeMillis();

      // Wait for balance with timeout
      boolean balanced = false;
      try {
        // Create a separate thread for balance check with timeout
        Thread balanceThread = new Thread(() -> {
          try {
            client.instanceOperations().waitForBalance();
          } catch (Exception e) {
            // Ignore - will be caught by timeout
          }
        });

        balanceThread.start();
        balanceThread.join(BALANCE_TIMEOUT_MS);

        if (balanceThread.isAlive()) {
          // Timeout
          balanceThread.interrupt();
          balanced = false;
        } else {
          balanced = true;
        }
      } catch (Exception e) {
        balanced = false;
      }

      long duration = System.currentTimeMillis() - start;
      result.addMetric("balance_duration_ms", duration);
      result.addMetric("balanced", balanced);

      if (!balanced) {
        result.addFailure("Cluster did not balance within " + BALANCE_TIMEOUT_MS + "ms");
      }
    } finally {
      client.close();
    }

    return result;
  }

  @Override
  public String getName() {
    return "accumulo-cluster-balanced";
  }
}
