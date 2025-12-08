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

import java.lang.reflect.Field;
import java.util.List;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl;
import org.restarttest.health.HealthCheck;
import org.restarttest.health.HealthCheckResult;

/**
 * Health check to verify that all expected tablet servers are registered.
 */
public class AccumuloTabletServersRegisteredCheck implements HealthCheck<MiniAccumuloCluster> {

  @Override
  public HealthCheckResult checkHealth(MiniAccumuloCluster cluster) throws Exception {
    HealthCheckResult result = new HealthCheckResult(true, getName());

    MiniAccumuloClusterImpl impl = getImpl(cluster);
    AccumuloClient client = cluster.createAccumuloClient("root",
        new PasswordToken(impl.getConfig().getRootPassword()));

    try {
      int expected = impl.getConfig().getNumTservers();
      List<String> registered = client.instanceOperations().getTabletServers();

      result.addMetric("expected_tservers", expected);
      result.addMetric("registered_tservers", registered.size());

      if (registered.size() != expected) {
        result.addFailure("Expected " + expected + " tablet servers but found " +
            registered.size());
      }
    } finally {
      client.close();
    }

    return result;
  }

  @Override
  public String getName() {
    return "accumulo-tablet-servers-registered";
  }

  private MiniAccumuloClusterImpl getImpl(MiniAccumuloCluster cluster) throws Exception {
    try {
      Field implField = MiniAccumuloCluster.class.getDeclaredField("impl");
      implField.setAccessible(true);
      return (MiniAccumuloClusterImpl) implField.get(cluster);
    } catch (Exception e) {
      throw new Exception("Failed to access MiniAccumuloCluster implementation", e);
    }
  }
}
