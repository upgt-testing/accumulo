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
package org.apache.accumulo.minicluster.upgrade;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages port allocations for cluster nodes and persists them to disk. This ensures that nodes
 * maintain the same ports across restarts and upgrades, which is critical for preserving node
 * identity in the cluster.
 *
 * <p>
 * Port allocations are persisted to {@code port-allocations.properties} in the cluster directory
 * and are automatically restored on restart.
 *
 * @since 2.1.2
 */
public class PortManager {
  private static final Logger log = LoggerFactory.getLogger(PortManager.class);

  private static final int DEFAULT_PORT_START = 50000;
  private static final int DEFAULT_PORT_END = 60000;

  private final File clusterDir;
  private final File persistenceFile;
  private final Map<String,Integer> portAllocations;
  private int nextPortCandidate;

  /**
   * Creates a new PortManager for the specified cluster directory.
   *
   * @param clusterDir The cluster's working directory
   */
  public PortManager(File clusterDir) {
    this.clusterDir = clusterDir;
    this.persistenceFile = new File(clusterDir, "port-allocations.properties");
    this.portAllocations = new HashMap<>();
    this.nextPortCandidate = DEFAULT_PORT_START;

    // Load any existing port allocations
    loadPersistedPorts();
  }

  /**
   * Allocates a port for the specified node and port type. If a port was previously allocated for
   * this node (persisted), it will be reused. Otherwise, a new port will be allocated and
   * persisted.
   *
   * @param nodeId Identifier for the node (e.g., "manager", "tserver.0")
   * @param portType Type of port (e.g., "clientPort", "thriftPort")
   * @return Allocated port number
   */
  public synchronized int allocatePort(String nodeId, String portType) throws IOException {
    String key = nodeId + "." + portType;

    // Check if we already have a port allocated for this node
    Integer existingPort = portAllocations.get(key);
    if (existingPort != null) {
      log.debug("Reusing existing port {} for {}", existingPort, key);
      return existingPort;
    }

    // Check if there's a persisted port we can restore
    Integer persistedPort = loadPersistedPort(nodeId, portType);
    if (persistedPort != null && isPortAvailable(persistedPort)) {
      log.info("Restored persisted port {} for {}", persistedPort, key);
      portAllocations.put(key, persistedPort);
      return persistedPort;
    }

    // Allocate a new port
    int port = findAvailablePort();
    log.info("Allocated new port {} for {}", port, key);
    portAllocations.put(key, port);
    persistPort(nodeId, portType, port);

    return port;
  }

  /**
   * Loads a persisted port allocation for a node, if it exists.
   *
   * @param nodeId Node identifier
   * @param portType Port type
   * @return Persisted port number, or null if not found
   */
  public synchronized Integer loadPersistedPort(String nodeId, String portType) {
    String key = nodeId + "." + portType;

    if (!persistenceFile.exists()) {
      return null;
    }

    try (FileInputStream fis = new FileInputStream(persistenceFile)) {
      Properties props = new Properties();
      props.load(fis);
      String portStr = props.getProperty(key);
      if (portStr != null) {
        return Integer.parseInt(portStr);
      }
    } catch (IOException | NumberFormatException e) {
      log.warn("Could not load persisted port for {}: {}", key, e.getMessage());
    }

    return null;
  }

  /**
   * Persists a port allocation to disk.
   *
   * @param nodeId Node identifier
   * @param portType Port type
   * @param port Port number
   */
  public synchronized void persistPort(String nodeId, String portType, int port)
      throws IOException {
    String key = nodeId + "." + portType;

    Properties props = new Properties();

    // Load existing properties if file exists
    if (persistenceFile.exists()) {
      try (FileInputStream fis = new FileInputStream(persistenceFile)) {
        props.load(fis);
      }
    }

    // Update with new port
    props.setProperty(key, String.valueOf(port));

    // Save to file
    try (FileOutputStream fos = new FileOutputStream(persistenceFile)) {
      props.store(fos, "Port allocations for ProcessBasedMiniAccumuloCluster");
    }

    log.debug("Persisted port {} for {}", port, key);
  }

  /**
   * Checks if a port is available for binding.
   *
   * @param port Port number to check
   * @return true if the port is available, false otherwise
   */
  public boolean isPortAvailable(int port) {
    try (ServerSocket socket = new ServerSocket(port)) {
      socket.setReuseAddress(true);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Finds an available port in the configured range.
   *
   * @return An available port number
   */
  private int findAvailablePort() throws IOException {
    int attempts = 0;
    int maxAttempts = DEFAULT_PORT_END - DEFAULT_PORT_START;

    while (attempts < maxAttempts) {
      int candidatePort = nextPortCandidate++;
      if (nextPortCandidate > DEFAULT_PORT_END) {
        nextPortCandidate = DEFAULT_PORT_START;
      }

      if (isPortAvailable(candidatePort)) {
        return candidatePort;
      }

      attempts++;
    }

    throw new IOException("Could not find an available port after " + maxAttempts + " attempts");
  }

  /**
   * Loads all persisted port allocations from disk into memory.
   */
  private void loadPersistedPorts() {
    if (!persistenceFile.exists()) {
      log.debug("No persisted port allocations found");
      return;
    }

    try (FileInputStream fis = new FileInputStream(persistenceFile)) {
      Properties props = new Properties();
      props.load(fis);

      for (String key : props.stringPropertyNames()) {
        String portStr = props.getProperty(key);
        try {
          int port = Integer.parseInt(portStr);
          portAllocations.put(key, port);
          log.debug("Loaded persisted port allocation: {} = {}", key, port);
        } catch (NumberFormatException e) {
          log.warn("Invalid port value in persisted allocations: {} = {}", key, portStr);
        }
      }

      log.info("Loaded {} persisted port allocations", portAllocations.size());
    } catch (IOException e) {
      log.warn("Could not load persisted port allocations: {}", e.getMessage());
    }
  }

  /**
   * Returns the current port allocations (for testing/debugging).
   */
  public synchronized Map<String,Integer> getPortAllocations() {
    return new HashMap<>(portAllocations);
  }

  /**
   * Clears all port allocations and deletes the persistence file. Use with caution - this will
   * cause nodes to get new ports on next start.
   */
  public synchronized void clearAllAllocations() throws IOException {
    portAllocations.clear();
    if (persistenceFile.exists()) {
      if (!persistenceFile.delete()) {
        throw new IOException("Could not delete port allocations file: " + persistenceFile);
      }
    }
    log.info("Cleared all port allocations");
  }
}
