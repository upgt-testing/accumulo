# Step 2: Generate Accumulo Upgrade Base Test Class

## Purpose

This document provides the complete specification for generating `ProcessBasedUpgradeTestBase`, a JUnit base class for checkpoint-based upgrade testing of ProcessBasedMiniAccumuloCluster. The generated class provides automatic lifecycle management, checkpoint-based upgrade testing, and complete test isolation.

## How to Use This Document

This document is ready to be provided to an AI programming agent (like Claude) to generate the ProcessBasedUpgradeTestBase class. All placeholders have been filled with Accumulo-specific values.

---

## AI Agent Prompt

**Copy the section below and provide to an AI agent:**

```
Generate a JUnit base test class for checkpoint-based upgrade testing with the following requirements:

### SYSTEM INFORMATION

**Cluster Type**: distributed key-value store and data management system
Description: Apache Accumulo is a sorted, distributed key-value store that provides robust, scalable data storage and retrieval

**Cluster Class**: org.apache.accumulo.minicluster.ProcessBasedMiniAccumuloCluster
Full package name: org.apache.accumulo.minicluster.ProcessBasedMiniAccumuloCluster

**Client/Connection Class**: org.apache.accumulo.core.client.AccumuloClient
Full package name: org.apache.accumulo.core.client.AccumuloClient

**Configuration Class**: N/A (Accumulo uses directory + password for initialization)
Note: ProcessBasedMiniAccumuloCluster uses File directory and String password, not a Configuration object

**Package Name**: org.apache.accumulo.minicluster.upgrade

**File Location**: minicluster/src/test/java/org/apache/accumulo/minicluster/upgrade/

### CLUSTER LIFECYCLE

**Cluster Initialization Pattern**:
```java
File tempDir = Files.createTempDirectory("accumulo-upgrade-test").toFile();
cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "testPassword")
    .numTabletServers(3)
    .numScanServers(1)
    .numCompactors(2)
    .build();
cluster.start();
```

**Client Initialization Pattern**:
```java
client = cluster.createAccumuloClient("root", new PasswordToken("testPassword"));
```

**Cluster Shutdown Pattern**:
```java
cluster.stop();  // Shuts down all nodes
cluster.close(); // Cleanup resources
```

**Client Shutdown Pattern**:
```java
client.close();
```

### UPGRADE MECHANISM

**Upgrade Method**: Rolling upgrade
Description: Upgrade servers one by one (TabletServers, Manager, GC, Compactors, Monitor, ScanServers) while cluster remains operational

**Upgrade Invocation**:
```java
cluster.upgrade();  // Performs rolling upgrade of all Accumulo servers
```

**Node Identity Preservation Requirement**:
Critical requirement: Accumulo servers MUST preserve their identity during upgrade:
- Same ports (RPC, thrift, client ports)
- Same network addresses
- Same server IDs
- Same configuration directory

Example verification:
```java
// Before upgrade
Map<Integer, InetSocketAddress> preUpgradeAddrs = new HashMap<>();
for (int i = 0; i < cluster.getNumTabletServers(); i++) {
    preUpgradeAddrs.put(i, cluster.getTabletServerAddress(i));
}

// Perform upgrade
cluster.upgrade();

// After upgrade - verify identity preserved
for (int i = 0; i < cluster.getNumTabletServers(); i++) {
    InetSocketAddress postAddr = cluster.getTabletServerAddress(i);
    InetSocketAddress preAddr = preUpgradeAddrs.get(i);
    assert postAddr.equals(preAddr) :
        "TabletServer " + i + " address changed during upgrade!";
}
```

**Pre-upgrade Health Check**:
```java
if (!cluster.isClusterUp()) {
    throw new IllegalStateException("Cluster is not healthy before upgrade");
}
```

**Post-upgrade Health Check**:
```java
cluster.waitClusterUp();  // Wait for cluster to stabilize after upgrade

// CRITICAL: Verify node identities preserved
verifyNodeIdentitiesPreserved();
```

**Node Identity Verification Pattern**:
```java
// Store pre-upgrade node identities
Map<Integer, InetSocketAddress> preUpgradeTabletServerAddrs = new HashMap<>();
for (int i = 0; i < cluster.getNumTabletServers(); i++) {
    preUpgradeTabletServerAddrs.put(i, cluster.getTabletServerAddress(i));
}

// After upgrade, verify identities match
for (int i = 0; i < cluster.getNumTabletServers(); i++) {
    InetSocketAddress postAddr = cluster.getTabletServerAddress(i);
    InetSocketAddress preAddr = preUpgradeTabletServerAddrs.get(i);
    if (!postAddr.equals(preAddr)) {
        throw new AssertionError(
            "TabletServer " + i + " address changed during upgrade: " +
            preAddr + " -> " + postAddr + ". " +
            "This indicates node identity was not preserved!");
    }
}
```

### PROCESS/RESOURCE CLEANUP

**Process Pattern to Kill**: Manager|TabletServer|GarbageCollector|Compactor|Monitor|ScanServer
Regular expression to match process names

**Process Cleanup Command**:
Shell command to kill orphaned processes
```bash
jps | grep -E 'Manager|TabletServer|GarbageCollector|Compactor|Monitor|ScanServer' | awk '{print $1}' | xargs -r kill -9
```

**Temporary Directory Pattern**: accumulo-upgrade-test.*
Pattern to match cluster temporary directories created by tests

**Directory Cleanup Logic**:
Java code to find and delete old cluster directories
```java
File tmpDir = new File(System.getProperty("java.io.tmpdir"));
File[] oldDirs = tmpDir.listFiles((dir, name) ->
    name.startsWith("accumulo-upgrade-test") &&
    // Only delete directories older than 1 hour to avoid interfering with parallel tests
    (System.currentTimeMillis() - dir.lastModified() > 3600000));
if (oldDirs != null) {
    for (File dir : oldDirs) {
        deleteDirectory(dir);
    }
}
```

### CHECKPOINT CONFIGURATION

**Checkpoint Constants Class**: UpgradeCheckpoints
Class name for checkpoint constants

**Common Checkpoint Names**: NO_UPGRADE, AFTER_CLUSTER_START, AFTER_CREATE_TABLE, AFTER_WRITE, AFTER_FLUSH, AFTER_COMPACT, AFTER_SCAN
List of common checkpoint names (comma-separated)

**Checkpoint No-Upgrade Constant**: NO_UPGRADE
Constant name for baseline test (no upgrade)

### ADDITIONAL REQUIREMENTS

**Additional Managed Resources**:
List any additional resources that need cleanup beyond cluster and client:
- Admin (org.apache.accumulo.core.client.admin.TableOperations) - Table administration handles (closed via client)
- Scanner (org.apache.accumulo.core.client.Scanner) - Open scanner handles (must be closed explicitly in tests)
- BatchWriter (org.apache.accumulo.core.client.BatchWriter) - Open writer handles (must be closed explicitly in tests)

Note: These are typically managed in individual test methods with try-with-resources, not in the base class.

**Additional Cleanup Steps**:
Additional cleanup operations in order:
1. Close AccumuloClient (handles all admin connections)
2. Stop cluster (shuts down all server processes)
3. Close cluster (cleanup resources)
4. Delete temporary directory
5. Verify no orphaned processes

**Special Considerations**:
- Accumulo requires ZooKeeper, but ZooKeeper is managed by the cluster and doesn't need separate cleanup
- HDFS may be used (MiniDFSCluster) - also managed by the cluster
- TabletServers, ScanServers, and Compactors support resource groups - tests may create multiple instances
- System properties: accumulo.start.home and accumulo.upgrade.home must be set for upgrade tests

### PLATFORM COMPATIBILITY

**Operating Systems**: Linux, macOS
Target operating systems

**Process Management Approach**:
Use jps and kill -9 on Unix systems. Cross-platform support for Linux and macOS.

### GENERATED CLASS STRUCTURE

Please generate a base test class with the following structure:

1. **Class Header**:
   - Apache License header (ASF standard)
   - Package declaration: org.apache.accumulo.minicluster.upgrade
   - Comprehensive JavaDoc explaining:
     - Purpose of the base class
     - Usage pattern with named checkpoint test methods
     - Example test implementation
     - Test isolation guarantees
     - Cleanup guarantees

2. **Protected Fields**:
   - upgradeCheckpoint (String) - Set directly by test methods
   - cluster (ProcessBasedMiniAccumuloCluster) - Cluster instance
   - client (AccumuloClient) - Client instance
   - tempDir (File) - Temporary directory for cluster
   - rootPassword (String) - Root password for cluster (default: "testPassword")

3. **@Before setupTest() Method**:
   - Log setup start with checkpoint name (if upgradeCheckpoint is set)
   - Clean up orphaned processes from previous failed runs
   - Clean up old cluster directories (older than 1 hour)
   - Create fresh temporary directory
   - Set cluster = null, client = null (defensive)
   - Log setup completion
   - NOTE: upgradeCheckpoint field is set directly by each test method, no reflection needed

4. **@After tearDownTest() Method**:
   - Log teardown start with checkpoint name
   - Close client (with try-catch, null check, finally block)
   - Stop cluster (with try-catch, null check, finally block)
   - Close cluster (with try-catch, null check, finally block)
   - Delete temporary directory (with try-catch)
   - Wait for processes to terminate (Thread.sleep with reasonable timeout)
   - Verify cleanup success (no orphaned processes)
   - Force cleanup if verification fails
   - Log teardown completion
   - **CRITICAL**: Use independent try-catch blocks for each cleanup step to ensure all cleanup runs even if one step fails

5. **checkpoint(String name) Method**:
   - Check if upgrade should happen at this checkpoint (call shouldUpgrade(name))
   - If no upgrade needed, return immediately
   - Log checkpoint name
   - Verify cluster health before upgrade (cluster.isClusterUp())
   - **Capture node identities BEFORE upgrade** (store addresses for verification)
   - Perform upgrade using cluster.upgrade()
   - **Verify node identities PRESERVED AFTER upgrade** (compare with captured addresses)
   - Verify cluster health after upgrade (cluster.waitClusterUp())
   - Log any identity violations as ERRORS
   - Log upgrade completion
   - **JavaDoc should warn**: "IMPORTANT: Close all streams (BatchWriter, Scanner) and resources before calling checkpoint() to avoid broken connections during server restarts"

6. **shouldUpgrade(String name) Method**:
   - Return false if upgradeCheckpoint is null
   - Return false if upgradeCheckpoint equals "NO_UPGRADE"
   - Return true if upgradeCheckpoint.equals(name)
   - Return false otherwise

7. **Private Helper Methods**:
   - cleanupOrphanedProcesses(): Execute jps | grep pattern, kill orphaned Accumulo processes
   - cleanupOldClusterDirectories(): Find and delete accumulo-upgrade-test.* directories older than 1 hour
   - deleteDirectory(File): Recursive directory deletion utility
   - verifyCleanup(): Execute jps and verify no Accumulo processes remain
   - NOTE: No reflection-based parameter syncing needed - test methods set upgradeCheckpoint directly

8. **verifyNodeIdentitiesPreserved() Method**:
   - Store node addresses before upgrade (in setupTest or checkpoint)
   - After upgrade, verify all TabletServers have same addresses
   - Throw AssertionError if any node identity changed
   - Log verification results

9. **System Property Handling**:
   - Automatically check for accumulo.start.home system property
   - Automatically check for accumulo.upgrade.home system property
   - Log warning if upgrade checkpoint is used but accumulo.upgrade.home is not set
   - Provide clear error messages if required properties are missing

10. **Best Practices**:
   - Use SLF4J Logger for all logging
   - Each cleanup step in @After must be in independent try-catch block
   - Set fields to null in finally blocks after cleanup
   - Log all major steps (setup, cleanup, upgrade, verification)
   - Defensive cleanup in @Before (kill orphaned processes from failed previous runs)
   - Comprehensive JavaDoc with usage examples
   - Automatic system property handling for start and upgrade distributions
   - Platform-aware process cleanup (handle Linux/macOS)
   - Node identity preservation verification (verify ports/addresses unchanged)
   - Pre-upgrade identity snapshot (store node addresses before upgrade)
   - Post-upgrade identity validation (compare with snapshot)

### OUTPUT FORMAT

Generate:
1. Complete Java source file with Apache Software Foundation license header
2. Necessary import statements (minimize unused imports)
3. All methods with comprehensive JavaDoc comments
4. Inline comments for complex logic
5. Proper exception handling and logging
6. Example usage in class-level JavaDoc showing:
   - How to extend this base class
   - How to create named checkpoint test methods
   - How to use checkpoint() in tests
   - Example test method for each common checkpoint

The generated class should be production-ready and follow Apache Accumulo code style.

### EXAMPLE USAGE (to include in class JavaDoc)

```java
/**
 * Base test class for checkpoint-based upgrade testing of ProcessBasedMiniAccumuloCluster.
 *
 * <p>This class provides automatic lifecycle management, checkpoint-based upgrade testing,
 * and complete test isolation. Each test execution is fully isolated with guaranteed
 * cleanup between runs.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * public class TestTableOperations extends ProcessBasedUpgradeTestBase {
 *
 *   @Test
 *   public void testCreateTable_NO_UPGRADE() throws Exception {
 *     upgradeCheckpoint = UpgradeCheckpoints.NO_UPGRADE;
 *
 *     cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, rootPassword)
 *         .numTabletServers(3)
 *         .build();
 *     cluster.start();
 *     client = cluster.createAccumuloClient("root", new PasswordToken(rootPassword));
 *     checkpoint(UpgradeCheckpoints.AFTER_CLUSTER_START);
 *
 *     // Create table
 *     String tableName = "test";
 *     client.tableOperations().create(tableName);
 *     checkpoint("AFTER_CREATE_TABLE");
 *
 *     // Write data
 *     try (BatchWriter bw = client.createBatchWriter(tableName)) {
 *       Mutation m = new Mutation("row1");
 *       m.put("cf", "cq", "value");
 *       bw.addMutation(m);
 *     }
 *     checkpoint("AFTER_WRITE");
 *
 *     // Verify data
 *     try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
 *       assertEquals(1, Iterators.size(scanner.iterator()));
 *     }
 *
 *     // No try-finally needed - @After handles cleanup!
 *   }
 *
 *   @Test
 *   public void testCreateTable_AFTER_CLUSTER_START() throws Exception {
 *     upgradeCheckpoint = UpgradeCheckpoints.AFTER_CLUSTER_START;
 *
 *     // Same full test logic as above - upgrade happens at AFTER_CLUSTER_START
 *     cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, rootPassword)
 *         .numTabletServers(3)
 *         .build();
 *     cluster.start();
 *     client = cluster.createAccumuloClient("root", new PasswordToken(rootPassword));
 *     checkpoint(UpgradeCheckpoints.AFTER_CLUSTER_START);
 *     // ... rest of test logic
 *   }
 *
 *   @Test
 *   public void testCreateTable_AFTER_CREATE_TABLE() throws Exception {
 *     upgradeCheckpoint = "AFTER_CREATE_TABLE";
 *
 *     // Same full test logic - upgrade happens at AFTER_CREATE_TABLE
 *     // ...
 *   }
 *
 *   @Test
 *   public void testCreateTable_AFTER_WRITE() throws Exception {
 *     upgradeCheckpoint = "AFTER_WRITE";
 *
 *     // Same full test logic - upgrade happens at AFTER_WRITE
 *     // ...
 *   }
 * }
 * }</pre>
 *
 * <p><strong>Running Tests:</strong></p>
 * <pre>
 * # Run specific checkpoint
 * mvn test -Dtest=TestTableOperations#testCreateTable_AFTER_CLUSTER_START \
 *   -Daccumulo.start.home=/path/to/accumulo-2.1.x \
 *   -Daccumulo.upgrade.home=/path/to/accumulo-3.0.x \
 *   -pl minicluster
 *
 * # Run all checkpoints for one test
 * mvn test -Dtest='TestTableOperations#testCreateTable_*' \
 *   -Daccumulo.start.home=/path/to/accumulo-2.1.x \
 *   -Daccumulo.upgrade.home=/path/to/accumulo-3.0.x \
 *   -pl minicluster
 *
 * # Run all baseline (NO_UPGRADE) tests
 * mvn test -Dtest='TestTableOperations#*_NO_UPGRADE' \
 *   -Daccumulo.start.home=/path/to/accumulo-2.1.x \
 *   -pl minicluster
 * </pre>
 *
 * <h3>Guarantees:</h3>
 * <ul>
 *   <li>Complete isolation between checkpoint executions</li>
 *   <li>Automatic cleanup of processes and directories</li>
 *   <li>Verification of cleanup success</li>
 *   <li>Force cleanup if verification fails</li>
 *   <li>Node identity preservation across upgrades</li>
 * </ul>
 *
 * <h3>System Properties:</h3>
 * <ul>
 *   <li>accumulo.start.home - Path to starting Accumulo distribution (required)</li>
 *   <li>accumulo.upgrade.home - Path to upgrade Accumulo distribution (required for upgrade tests)</li>
 * </ul>
 */
```
```

---

## UpgradeCheckpoints Constants Class

Also generate a simple constants class for common checkpoints:

```java
package org.apache.accumulo.minicluster.upgrade;

/**
 * Common checkpoint names for upgrade testing.
 *
 * @since 3.1.0
 */
public class UpgradeCheckpoints {
  /**
   * No upgrade - baseline test
   */
  public static final String NO_UPGRADE = "NO_UPGRADE";

  /**
   * Upgrade immediately after cluster starts
   */
  public static final String AFTER_CLUSTER_START = "AFTER_CLUSTER_START";

  /**
   * Upgrade after creating table
   */
  public static final String AFTER_CREATE_TABLE = "AFTER_CREATE_TABLE";

  /**
   * Upgrade after writing data
   */
  public static final String AFTER_WRITE = "AFTER_WRITE";

  /**
   * Upgrade after flushing data
   */
  public static final String AFTER_FLUSH = "AFTER_FLUSH";

  /**
   * Upgrade after compaction
   */
  public static final String AFTER_COMPACT = "AFTER_COMPACT";

  /**
   * Upgrade after scanning data
   */
  public static final String AFTER_SCAN = "AFTER_SCAN";

  private UpgradeCheckpoints() {
    // Constants class
  }
}
```

---

## Validation Checklist

After generation, verify the base class has:

- [ ] Complete JavaDoc with usage example showing named checkpoint test methods
- [ ] Apache Software Foundation license header
- [ ] @Before method that performs cleanup and initialization
- [ ] @After method with independent try-catch blocks for each cleanup step
- [ ] checkpoint(String) method with health checks before and after upgrade
- [ ] shouldUpgrade(String) method with proper logic
- [ ] Node identity verification before and after upgrade
- [ ] Process cleanup logic (jps | grep Manager|TabletServer|...)
- [ ] Directory cleanup with age-based filtering (older than 1 hour)
- [ ] Cleanup verification method that checks for orphaned processes
- [ ] Proper null checks before all cleanup operations
- [ ] Fields set to null in finally blocks after cleanup
- [ ] Logger with appropriate log levels (INFO for major steps, DEBUG for details)
- [ ] No resource leaks in helper methods
- [ ] System property handling for accumulo.start.home and accumulo.upgrade.home
- [ ] Comprehensive exception handling (independent try-catch per cleanup step)

---

## Notes

- This template is designed for JUnit 4 with standard @Test methods (no parameterization)
- Test methods set `upgradeCheckpoint` field directly at the start of each test
- Each test method should contain full test logic for clarity
- Test method naming: `testMethodName_CHECKPOINT_NAME()` format
- System properties passed via Maven: -Daccumulo.start.home=... -Daccumulo.upgrade.home=...

---

**End of Template**

Use this document to generate a comprehensive base test class for Accumulo checkpoint-based upgrade testing framework.
