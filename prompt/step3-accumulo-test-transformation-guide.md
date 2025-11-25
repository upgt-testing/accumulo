# Step 3: Accumulo Test Transformation Guide

## Table of Contents
1. [Introduction & Philosophy](#introduction--philosophy)
2. [Prerequisites & Setup](#prerequisites--setup)
3. [Test Organization and Naming Convention](#test-organization-and-naming-convention)
4. [Core Transformation Rules](#core-transformation-rules)
5. [API Mapping Tables](#api-mapping-tables)
6. [Step-by-Step Transformation Process](#step-by-step-transformation-process)
7. [Common Transformation Patterns](#common-transformation-patterns)
8. [Inserting Cluster Upgrade Method Calls](#inserting-cluster-upgrade-method-calls)
9. [Upgrade Checkpoint Test Methods](#upgrade-checkpoint-test-methods)
10. [When to Comment Out Logic](#when-to-comment-out-logic)
11. [Testing Checklist](#testing-checklist)
12. [Best Practices](#best-practices)
13. [Quick Reference Decision Tree](#quick-reference-decision-tree)

---

## Introduction & Philosophy

### Purpose
Transform existing `MiniAccumuloCluster` tests to `ProcessBasedMiniAccumuloCluster` to enable:
- **Process-based testing** - Each node already runs in separate JVM (existing feature)
- **Multi-version testing** - Test upgrades between different Accumulo versions
- **Rolling upgrade scenarios** - Simulate production upgrade procedures
- **Version compatibility** - Verify protocol compatibility across versions

### Key Principle
**MiniAccumuloCluster already uses client-side APIs.** Most test logic will transfer directly because Accumulo has always enforced client-only access. The main additions are:
1. System properties for version distributions
2. Upgrade checkpoint calls
3. Node identity verification

### Critical Transformation Mindset

**EVERY REDUCED VERSION IS MEANINGFUL!**

If you cannot transform 100% of a test, transform what you CAN. A test with 80% preserved logic is infinitely better than 0%. Never skip a test just because:
- It uses configuration overrides (adapt the configuration)
- It has timing assumptions (adjust timeouts)
- It seems "too complex" (break it into checkpoints)

**Transform as much as possible, comment out as little as necessary.**

### Transformation Hierarchy
When encountering operations, try these approaches in order:

1. **AccumuloClient API** - High-level client operations (tableOperations(), securityOperations(), etc.)
2. **Admin operations** - Administrative operations via client
3. **Properties/Config** - Cluster configuration and client properties
4. **Wait/Polling** - For state changes (already client-side)
5. **Comment Out** - Only if truly no client-side equivalent exists (rare!)

### Why ProcessBasedMiniAccumuloCluster?

**MiniAccumuloCluster limitations:**
- All nodes run same Accumulo version - cannot test upgrades
- Cannot simulate version compatibility issues
- Cannot test rolling upgrade procedures

**ProcessBasedMiniAccumuloCluster benefits:**
- **Multi-version support** - Essential for upgrade testing
- **Same client-side API** - AccumuloClient operations unchanged
- **Better represents production** - Version upgrades are real
- **Node identity persistence** - Nodes maintain address/port across restarts

---

## Prerequisites & Setup

### System Properties (Automatic!)
ProcessBasedMiniAccumuloCluster automatically reads distributions from system properties:

```bash
# No manual setup needed! Just pass system properties to Maven:
mvn test -Dtest=YourTransformedTest \
  -Daccumulo.start.home=/path/to/accumulo-2.1.x \
  -Daccumulo.upgrade.home=/path/to/accumulo-3.0.x \
  -pl minicluster
```

**Backward compatibility:** Environment variables (`ACCUMULO_HOME`, `ACCUMULO_UPGRADE_HOME`) still work as fallback.

### Test Configuration
```java
import org.apache.accumulo.minicluster.ProcessBasedMiniAccumuloCluster;

// No @Before setup needed! System properties are read automatically!
@Test
public void testSomething() throws Exception {
    // Just build - automatic!
    File tempDir = Files.createTempDirectory("test").toFile();
    ProcessBasedMiniAccumuloCluster cluster =
        new ProcessBasedMiniAccumuloCluster.Builder(tempDir, "password")
            .numTabletServers(3)
            .build();  // Automatically reads system properties!
    cluster.start();
}
```

### Running Transformed Tests
```bash
# Run with system properties (recommended)
mvn test -Dtest=YourTransformedTest \
  -Daccumulo.start.home=/opt/accumulo-2.1.x \
  -Daccumulo.upgrade.home=/opt/accumulo-3.0.x \
  -pl minicluster
```

---

## Test Organization and Naming Convention

### File Location Strategy
**Place transformed tests in the SAME directory as the original tests**, using a naming suffix to distinguish them.

This approach provides:
- ✅ Side-by-side comparison of original and transformed tests
- ✅ Tests alphabetically adjacent in file listings
- ✅ Clear visual distinction via suffix
- ✅ Original package structure preserved
- ✅ Both versions can coexist long-term

### Naming Convention: `_ProcessBased` Suffix

```
Original Test:     TestTableOperations.java
Transformed Test:  TestTableOperations_ProcessBased.java

Location:          Same directory, same package
```

### Directory Structure Examples

```
minicluster/src/test/java/org/apache/accumulo/minicluster/
├── MiniAccumuloClusterTest.java                      [ORIGINAL]
├── MiniAccumuloClusterTest_ProcessBased.java         [TRANSFORMED]
├── MiniAccumuloClusterStartStopTest.java             [ORIGINAL]
├── MiniAccumuloClusterStartStopTest_ProcessBased.java [TRANSFORMED]
└── upgrade/
    ├── ProcessBasedUpgradeTestBase.java              [BASE CLASS]
    └── UpgradeCheckpoints.java                       [CONSTANTS]
```

### Package and Class Declaration

The transformed test uses the **same package** as the original:

```java
// Original: MiniAccumuloClusterTest.java
package org.apache.accumulo.minicluster;

public class MiniAccumuloClusterTest {
  // ... MiniAccumuloCluster tests
}
```

```java
// Transformed: MiniAccumuloClusterTest_ProcessBased.java
package org.apache.accumulo.minicluster;  // Same package!

/**
 * ProcessBased version of {@link MiniAccumuloClusterTest}.
 *
 * Transformed from MiniAccumuloCluster to ProcessBasedMiniAccumuloCluster to enable
 * process-based testing and multi-version upgrade scenarios.
 *
 * @see MiniAccumuloClusterTest Original test using MiniAccumuloCluster
 */
public class MiniAccumuloClusterTest_ProcessBased extends ProcessBasedUpgradeTestBase {
  // ... ProcessBasedMiniAccumuloCluster tests
}
```

### Test Execution Patterns

```bash
# Run original test only
mvn test -Dtest=MiniAccumuloClusterTest

# Run transformed test only
mvn test -Dtest=MiniAccumuloClusterTest_ProcessBased

# Run ALL ProcessBased tests across the codebase
mvn test -Dtest="*_ProcessBased"

# Run both versions for comparison
mvn test -Dtest=MiniAccumuloClusterTest,MiniAccumuloClusterTest_ProcessBased
```

---

## Core Transformation Rules

### Rule 1: Maximize Test Logic Preservation
**Preserve as much of the original test logic as possible** since most AccumuloClient operations are already client-side.

✅ **DO**: Keep all AccumuloClient operations unchanged
❌ **DON'T**: Remove test logic unless absolutely necessary

### Rule 2: Use the API Hierarchy
Most operations are already client-side:
1. **AccumuloClient API** (tableOperations(), securityOperations(), instanceOperations())
2. **Admin operations** (via tableOperations().compact(), etc.)
3. **Config/Properties** (cluster configuration)

### Rule 3: Comment Out Only When Necessary
Only comment out operations when:
- Accessing internal cluster state not available via client API (very rare)
- Direct process manipulation (already not supported in original)
- JVM-internal state inspection

### Rule 4: Document Minimally
Add comments only for:
- Non-obvious transformations
- Commented-out logic (explain why and what was removed)
- Upgrade-specific additions

---

## API Mapping Tables

### Table 1: MiniAccumuloCluster → ProcessBasedMiniAccumuloCluster

| MiniAccumuloCluster Method | ProcessBasedMiniAccumuloCluster | Status | Notes |
|----------------------------|----------------------------------|--------|-------|
| **Cluster Creation** |
| `new MiniAccumuloConfig(dir, pw)` | `new Builder(dir, pw)` | ⚠️ | Builder pattern instead of config object |
| `new MiniAccumuloCluster(config)` | `new Builder(dir, pw).build()` | ⚠️ | Simplified builder |
| **Client Access** |
| `createAccumuloClient(user, token)` | `createAccumuloClient(user, token)` | ✓ | Same API |
| `getClientProperties()` | `getClientProperties()` | ✓ | Same API |
| `getInstanceName()` | `getInstanceName()` | ✓ | Same API |
| `getZooKeepers()` | `getZooKeepers()` | ✓ | Same API |
| **Cluster Control** |
| `start()` | `start()` | ✓ | Same API |
| `stop()` | `stop()` | ✓ | Same API |
| `close()` | `close()` | ✓ | Same API (AutoCloseable) |
| **NEW - Upgrade Operations** |
| N/A | `upgrade()` | ✓ | NEW - Rolling upgrade |
| N/A | `changeTabletServerVersion(idx, home)` | ✓ | NEW - Per-server version |
| N/A | `changeManagerVersion(home)` | ✓ | NEW - Manager version |
| **Configuration** |
| `config.setZooKeeperPort(port)` | `.setZooKeeperPort(port)` | ✓ | Builder method |
| `config.setNumTabletServers(n)` | `.numTabletServers(n)` | ✓ | Builder method |
| `config.setSiteConfig(map)` | `.setSiteConfig(map)` | ✓ | Builder method |

### Table 2: AccumuloClient Operations (No Change!)

**Context**: All AccumuloClient operations are already client-side and work identically.

| Operation Category | API | Status | Notes |
|--------------------|-----|--------|-------|
| **Table Operations** |
| Create table | `client.tableOperations().create(name)` | ✓ | No change |
| Delete table | `client.tableOperations().delete(name)` | ✓ | No change |
| List tables | `client.tableOperations().list()` | ✓ | No change |
| Compact table | `client.tableOperations().compact(name)` | ✓ | No change |
| Flush table | `client.tableOperations().flush(name)` | ✓ | No change |
| **Security Operations** |
| Create user | `client.securityOperations().createLocalUser(u, pw)` | ✓ | No change |
| Grant permission | `client.securityOperations().grantTablePermission(...)` | ✓ | No change |
| Change authorizations | `client.securityOperations().changeUserAuthorizations(...)` | ✓ | No change |
| **Data Operations** |
| Batch write | `client.createBatchWriter(table)` | ✓ | No change |
| Scan | `client.createScanner(table, auths)` | ✓ | No change |
| Batch scan | `client.createBatchScanner(table, auths, threads)` | ✓ | No change |
| **Instance Operations** |
| Get instance ID | `client.instanceOperations().getInstanceId()` | ✓ | No change |
| Get tablet locations | `client.instanceOperations().getTabletServers()` | ✓ | No change |

### Table 3: Configuration Changes

| MiniAccumuloConfig Method | Builder Method | Example |
|---------------------------|----------------|---------|
| `setZooKeeperPort(int)` | `.setZooKeeperPort(int)` | `.setZooKeeperPort(0)` |
| `setNumTabletServers(int)` | `.numTabletServers(int)` | `.numTabletServers(3)` |
| `setSiteConfig(Map)` | `.setSiteConfig(Map)` | `.setSiteConfig(siteMap)` |
| `setJDWPEnabled(boolean)` | `.setJDWPEnabled(boolean)` | `.setJDWPEnabled(true)` |

---

## Step-by-Step Transformation Process

### Step 0: Create Transformed Test File

1. **Locate the original test:**
   ```bash
   minicluster/src/test/java/org/apache/accumulo/minicluster/MiniAccumuloClusterTest.java
   ```

2. **Create new file with `_ProcessBased` suffix in SAME directory:**
   ```bash
   minicluster/src/test/java/org/apache/accumulo/minicluster/MiniAccumuloClusterTest_ProcessBased.java
   ```

3. **Copy original test content:**
   ```bash
   cp MiniAccumuloClusterTest.java MiniAccumuloClusterTest_ProcessBased.java
   ```

4. **Update class name and add Javadoc:**
   ```java
   package org.apache.accumulo.minicluster;  // Same package!

   /**
    * ProcessBased version of {@link MiniAccumuloClusterTest}.
    *
    * Transformed from MiniAccumuloCluster to ProcessBasedMiniAccumuloCluster
    * to enable process-based testing and multi-version upgrade scenarios.
    *
    * @see MiniAccumuloClusterTest Original test using MiniAccumuloCluster
    */
   public class MiniAccumuloClusterTest_ProcessBased extends ProcessBasedUpgradeTestBase {
     // ... test methods
   }
   ```

### Step 1: Transform Import Statements

```java
// BEFORE
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.apache.accumulo.minicluster.MiniAccumuloConfig;

// AFTER
import org.apache.accumulo.minicluster.ProcessBasedMiniAccumuloCluster;
import org.apache.accumulo.minicluster.upgrade.ProcessBasedUpgradeTestBase;
import org.apache.accumulo.minicluster.upgrade.UpgradeCheckpoints;
// Remove: import org.apache.accumulo.minicluster.MiniAccumuloConfig;
```

### Step 2: Extend ProcessBasedUpgradeTestBase

```java
// BEFORE
public class MiniAccumuloClusterTest {
  @TempDir
  private static Path tempDir;
  private static MiniAccumuloCluster cluster;

  @BeforeAll
  public static void setup() throws Exception {
    MiniAccumuloConfig config = new MiniAccumuloConfig(tempDir.toFile(), "password");
    cluster = new MiniAccumuloCluster(config);
    cluster.start();
  }

  @AfterAll
  public static void teardown() throws Exception {
    cluster.stop();
  }
}

// AFTER - Use base class, instance variables, @Before/@After
public class MiniAccumuloClusterTest_ProcessBased extends ProcessBasedUpgradeTestBase {
  // No @TempDir - inherited from base class as 'tempDir'
  // No @BeforeAll/@AfterAll - handled by base class @Before/@After
  // cluster and client fields inherited from base class
}
```

### Step 3: Transform Cluster Setup (Per Test Method)

```java
// BEFORE (shared cluster in @BeforeAll)
@BeforeAll
public static void setup() throws Exception {
  MiniAccumuloConfig config = new MiniAccumuloConfig(tempDir.toFile(), "password");
  config.setZooKeeperPort(0);
  config.setNumTabletServers(3);
  cluster = new MiniAccumuloCluster(config);
  cluster.start();
}

// AFTER (per-test cluster initialization)
@Test
public void testTableOperations_NO_UPGRADE() throws Exception {
  upgradeCheckpoint = UpgradeCheckpoints.NO_UPGRADE;

  cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, rootPassword)
      .setZooKeeperPort(0)
      .numTabletServers(3)
      .build();  // Automatically reads system properties!
  cluster.start();
  client = cluster.createAccumuloClient("root", new PasswordToken(rootPassword));
  checkpoint(UpgradeCheckpoints.AFTER_CLUSTER_START);

  // Test logic here...
  // No try-finally needed - base class @After handles cleanup!
}
```

### Step 4: Transform Test Methods to Checkpoint Variants

For each original test method, create checkpoint variants:

```java
// ORIGINAL TEST METHOD
@Test
public void testTableOperations() throws Exception {
  try (AccumuloClient client = Accumulo.newClient().from(cluster.getClientProperties())
      .as("root", "password").build()) {

    String tableName = "test";
    client.tableOperations().create(tableName);

    try (BatchWriter bw = client.createBatchWriter(tableName)) {
      Mutation m = new Mutation("row1");
      m.put("cf", "cq", "value");
      bw.addMutation(m);
    }

    try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
      assertEquals(1, Iterators.size(scanner.iterator()));
    }
  }
}

// TRANSFORMED - Checkpoint Variants
@Test
public void testTableOperations_NO_UPGRADE() throws Exception {
  upgradeCheckpoint = UpgradeCheckpoints.NO_UPGRADE;

  cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, rootPassword)
      .numTabletServers(3)
      .build();
  cluster.start();
  client = cluster.createAccumuloClient("root", new PasswordToken(rootPassword));
  checkpoint(UpgradeCheckpoints.AFTER_CLUSTER_START);

  String tableName = "test";
  client.tableOperations().create(tableName);
  checkpoint("AFTER_CREATE_TABLE");

  try (BatchWriter bw = client.createBatchWriter(tableName)) {
    Mutation m = new Mutation("row1");
    m.put("cf", "cq", "value");
    bw.addMutation(m);
  }
  checkpoint("AFTER_WRITE");

  try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
    assertEquals(1, Iterators.size(scanner.iterator()));
  }
}

@Test
public void testTableOperations_AFTER_CLUSTER_START() throws Exception {
  upgradeCheckpoint = UpgradeCheckpoints.AFTER_CLUSTER_START;

  // Same full test logic - upgrade happens at AFTER_CLUSTER_START
  cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, rootPassword)
      .numTabletServers(3)
      .build();
  cluster.start();
  client = cluster.createAccumuloClient("root", new PasswordToken(rootPassword));
  checkpoint(UpgradeCheckpoints.AFTER_CLUSTER_START);

  String tableName = "test";
  client.tableOperations().create(tableName);
  checkpoint("AFTER_CREATE_TABLE");

  try (BatchWriter bw = client.createBatchWriter(tableName)) {
    Mutation m = new Mutation("row1");
    m.put("cf", "cq", "value");
    bw.addMutation(m);
  }
  checkpoint("AFTER_WRITE");

  try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
    assertEquals(1, Iterators.size(scanner.iterator()));
  }
}

@Test
public void testTableOperations_AFTER_CREATE_TABLE() throws Exception {
  upgradeCheckpoint = "AFTER_CREATE_TABLE";

  // Same full test logic - upgrade happens at AFTER_CREATE_TABLE
  // ... (same code as above)
}

@Test
public void testTableOperations_AFTER_WRITE() throws Exception {
  upgradeCheckpoint = "AFTER_WRITE";

  // Same full test logic - upgrade happens at AFTER_WRITE
  // ... (same code as above)
}
```

---

## Common Transformation Patterns

### Pattern 1: Table Operations (No Change!)

```java
// Works identically in both frameworks
client.tableOperations().create("tableName");
client.tableOperations().delete("tableName");
client.tableOperations().compact("tableName");
client.tableOperations().flush("tableName");
```

### Pattern 2: Security Operations (No Change!)

```java
// Works identically in both frameworks
client.securityOperations().createLocalUser("user", new PasswordToken("pass"));
client.securityOperations().grantTablePermission("user", "table", TablePermission.READ);
client.securityOperations().changeUserAuthorizations("user", new Authorizations("A", "B"));
```

### Pattern 3: Data Operations (No Change!)

```java
// Works identically in both frameworks
try (BatchWriter bw = client.createBatchWriter("table")) {
  Mutation m = new Mutation("row");
  m.put("cf", "cq", "value");
  bw.addMutation(m);
}

try (Scanner scanner = client.createScanner("table", Authorizations.EMPTY)) {
  for (Entry<Key, Value> entry : scanner) {
    // Process entries
  }
}
```

---

## Inserting Cluster Upgrade Method Calls

### Overview

When transforming tests to support rolling upgrades, you need to insert `checkpoint()` method calls at appropriate points in the test. This section explains how to identify upgrade points and handle the critical pattern of **closing resources before upgrade and reopening them afterward**.

### Why Resource Management is Critical

During a rolling upgrade, cluster nodes are restarted with new software versions. This restart **breaks active connections** between the client and the nodes.

**Key principle**: Any active connection/stream/resource that spans an upgrade point must be:
1. **Closed** before calling `checkpoint()` that triggers upgrade
2. **Reopened** after upgrade completes

### Identifying Upgrade Points

An upgrade point is a logical location in your test where you want to simulate a rolling upgrade. Common upgrade points include:

1. **After cluster starts** - Testing upgrade immediately after initialization
2. **After creating tables** - Testing that metadata survives upgrade
3. **After writing data** - Testing that data survives upgrade
4. **Mid-operation** - Testing resilience during operations

### Step-by-Step: Inserting Checkpoint Calls

#### Step 1: Identify Potential Checkpoints

```java
// BEFORE: Original test without checkpoints
client.tableOperations().create("test");
writeData(client, "test");
verifyData(client, "test");
```

#### Step 2: Insert checkpoint() Calls

```java
// AFTER: With checkpoint calls
client.tableOperations().create("test");
checkpoint("AFTER_CREATE_TABLE");

writeData(client, "test");
checkpoint("AFTER_WRITE");

verifyData(client, "test");
checkpoint("AFTER_VERIFY");
```

#### Step 3: Handle Resource Lifecycles

**Example: Scanner Across Upgrade**

```java
// WRONG - Scanner remains open during upgrade
try (Scanner scanner = client.createScanner("test", Authorizations.EMPTY)) {
  // Read some data
  Iterator<Entry<Key,Value>> iter = scanner.iterator();
  Entry<Key,Value> first = iter.next();

  checkpoint("DURING_SCAN");  // WRONG! Scanner will break!

  // This will fail - connection is broken
  Entry<Key,Value> second = iter.next();
}

// CORRECT - Close scanner before upgrade
try (Scanner scanner = client.createScanner("test", Authorizations.EMPTY)) {
  Entry<Key,Value> first = scanner.iterator().next();
}
checkpoint("AFTER_FIRST_SCAN");

try (Scanner scanner = client.createScanner("test", Authorizations.EMPTY)) {
  // Reopen scanner after upgrade
  Iterator<Entry<Key,Value>> iter = scanner.iterator();
  iter.next(); // skip first
  Entry<Key,Value> second = iter.next();
}
```

**Example: BatchWriter Across Upgrade**

```java
// WRONG - BatchWriter remains open during upgrade
try (BatchWriter bw = client.createBatchWriter("test")) {
  Mutation m1 = new Mutation("row1");
  m1.put("cf", "cq", "val1");
  bw.addMutation(m1);

  checkpoint("DURING_WRITE");  // WRONG! Writer will break!

  Mutation m2 = new Mutation("row2");
  m2.put("cf", "cq", "val2");
  bw.addMutation(m2);  // This will fail
}

// CORRECT - Close writer, upgrade, reopen
try (BatchWriter bw = client.createBatchWriter("test")) {
  Mutation m1 = new Mutation("row1");
  m1.put("cf", "cq", "val1");
  bw.addMutation(m1);
}
checkpoint("AFTER_FIRST_WRITE");

try (BatchWriter bw = client.createBatchWriter("test")) {
  Mutation m2 = new Mutation("row2");
  m2.put("cf", "cq", "val2");
  bw.addMutation(m2);
}
```

### Complete Example Pattern

```java
@Test
public void testWriteAndRead_AFTER_WRITE() throws Exception {
  upgradeCheckpoint = "AFTER_WRITE";

  cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, rootPassword)
      .numTabletServers(3)
      .build();
  cluster.start();
  client = cluster.createAccumuloClient("root", new PasswordToken(rootPassword));
  checkpoint(UpgradeCheckpoints.AFTER_CLUSTER_START);

  // Create table
  String tableName = "test";
  client.tableOperations().create(tableName);
  checkpoint("AFTER_CREATE_TABLE");

  // Write first batch
  try (BatchWriter bw = client.createBatchWriter(tableName)) {
    Mutation m = new Mutation("row1");
    m.put("cf", "cq", "value1");
    bw.addMutation(m);
  }
  checkpoint("AFTER_WRITE");  // UPGRADE HAPPENS HERE!

  // Write second batch after upgrade
  try (BatchWriter bw = client.createBatchWriter(tableName)) {
    Mutation m = new Mutation("row2");
    m.put("cf", "cq", "value2");
    bw.addMutation(m);
  }

  // Verify both writes visible
  try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
    assertEquals(2, Iterators.size(scanner.iterator()));
  }
}
```

---

## Upgrade Checkpoint Test Methods

### Overview

**Recommended Approach**: Generate multiple test methods with checkpoint suffixes. Each test method tests the same logic but with upgrade at a different checkpoint.

**Key Benefits**:
- Single test logic → multiple test methods with different checkpoints
- 100% reproducible (deterministic checkpoint execution)
- Comprehensive coverage (standard + test-specific checkpoints)
- Guaranteed cleanup between executions (base class handles it)
- Easy Maven execution: can run specific checkpoint with `-Dtest=Test#method_CHECKPOINT`

### Base Class: ProcessBasedUpgradeTestBase

All ProcessBased tests should extend `ProcessBasedUpgradeTestBase`, which provides:

1. **@Before cleanup**: Kills orphaned processes, cleans old directories
2. **@After cleanup**: Closes client, shuts down cluster, verifies cleanup
3. **checkpoint(name)**: Performs upgrade if name matches upgradeCheckpoint
4. **shouldUpgrade(name)**: Checks if upgrade should happen

### Transformation Steps

#### Step 1: Identify Checkpoints for Each Test Method

For each original test method, identify:
1. **Standard checkpoints** (always include):
   - `NO_UPGRADE` - Baseline test without upgrade
   - `AFTER_CLUSTER_START` - Upgrade immediately after cluster starts

2. **Test-specific checkpoints** (from actual checkpoint() calls):
   - Look for all `checkpoint("NAME")` calls in the test method
   - Each unique checkpoint name becomes a test method variant

**Example:**
```java
// Original test method
@Test
public void testTableOperations() {
  cluster.start();
  checkpoint("AFTER_CLUSTER_START");

  client.tableOperations().create("test");
  checkpoint("AFTER_CREATE_TABLE");

  writeData();
  checkpoint("AFTER_WRITE");

  verify();
}
```

**Identified checkpoints:**
- `NO_UPGRADE` (standard)
- `AFTER_CLUSTER_START` (standard + in test)
- `AFTER_CREATE_TABLE` (test-specific)
- `AFTER_WRITE` (test-specific)

#### Step 2: Generate Test Methods

Create one test method per checkpoint with naming pattern `testMethodName_CHECKPOINT_NAME()`:

```java
public class TestTableOperations_ProcessBased extends ProcessBasedUpgradeTestBase {

  @Test
  public void testTableOperations_NO_UPGRADE() throws Exception {
    upgradeCheckpoint = UpgradeCheckpoints.NO_UPGRADE;

    cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, rootPassword)
        .numTabletServers(3).build();
    cluster.start();
    client = cluster.createAccumuloClient("root", new PasswordToken(rootPassword));
    checkpoint(UpgradeCheckpoints.AFTER_CLUSTER_START);

    client.tableOperations().create("test");
    checkpoint("AFTER_CREATE_TABLE");

    writeData();
    checkpoint("AFTER_WRITE");

    verify();
  }

  @Test
  public void testTableOperations_AFTER_CLUSTER_START() throws Exception {
    upgradeCheckpoint = UpgradeCheckpoints.AFTER_CLUSTER_START;

    // Same full test logic - upgrade happens at AFTER_CLUSTER_START
    cluster = new ProcessBasedMiniAccumuloCluster.Builder(tempDir, rootPassword)
        .numTabletServers(3).build();
    cluster.start();
    client = cluster.createAccumuloClient("root", new PasswordToken(rootPassword));
    checkpoint(UpgradeCheckpoints.AFTER_CLUSTER_START);

    client.tableOperations().create("test");
    checkpoint("AFTER_CREATE_TABLE");

    writeData();
    checkpoint("AFTER_WRITE");

    verify();
  }

  @Test
  public void testTableOperations_AFTER_CREATE_TABLE() throws Exception {
    upgradeCheckpoint = "AFTER_CREATE_TABLE";

    // Same full test logic - upgrade happens at AFTER_CREATE_TABLE
    // ... (same code)
  }

  @Test
  public void testTableOperations_AFTER_WRITE() throws Exception {
    upgradeCheckpoint = "AFTER_WRITE";

    // Same full test logic - upgrade happens at AFTER_WRITE
    // ... (same code)
  }
}
```

### Running Checkpoint Test Methods

**Run all test methods (all checkpoints for all tests)**:
```bash
mvn test -Dtest=TestTableOperations_ProcessBased \
  -Daccumulo.start.home=/opt/accumulo-2.1.x \
  -Daccumulo.upgrade.home=/opt/accumulo-3.0.x \
  -pl minicluster
```

**Run specific checkpoint for specific test**:
```bash
mvn test -Dtest=TestTableOperations_ProcessBased#testTableOperations_AFTER_WRITE \
  -Daccumulo.start.home=/opt/accumulo-2.1.x \
  -Daccumulo.upgrade.home=/opt/accumulo-3.0.x \
  -pl minicluster
```

**Run all checkpoints for one test method** (using wildcard):
```bash
mvn test -Dtest='TestTableOperations_ProcessBased#testTableOperations_*' \
  -Daccumulo.start.home=/opt/accumulo-2.1.x \
  -Daccumulo.upgrade.home=/opt/accumulo-3.0.x \
  -pl minicluster
```

**Run all baseline (NO_UPGRADE) tests**:
```bash
mvn test -Dtest='TestTableOperations_ProcessBased#*_NO_UPGRADE' \
  -Daccumulo.start.home=/opt/accumulo-2.1.x \
  -pl minicluster
```

---

## When to Comment Out Logic

### Only comment out operations that are:

1. **Internal Cluster State** (Very Rare in Accumulo)
   - Direct process manipulation (not supported in original either)
   - JVM-internal state inspection
   - Private field access

2. **Note**: Almost all Accumulo test operations are already client-side, so commenting out should be minimal.

### Comment Template

Use this template when commenting out unsupported logic:

```java
// TRANSFORMATION NOTE: [Brief explanation of what was removed]
// [Why it was removed - what makes it inaccessible via client APIs]
// [What the original code verified/tested]
// [Suggestion for alternative verification if applicable, or "No client-side alternative available"]
//
// Original code:
// [indented commented-out code]
```

### When NOT to Comment Out

Do NOT comment out if there's a client-side equivalent:

❌ **WRONG**:
```java
// TRANSFORMATION NOTE: Cannot access internal state
// Original code:
// boolean isCompacting = cluster.isCompacting();
```

✅ **CORRECT**:
```java
// Check compaction status via client API
boolean isCompacting = client.instanceOperations().getActiveCompactions().size() > 0;
```

---

## Testing Checklist

### Before Running Test

- [ ] **File organization correct**
  - Transformed test in same directory as original
  - File name has `_ProcessBased` suffix
  - Package declaration identical to original
  - Class name matches file name with `_ProcessBased` suffix
  - Javadoc includes `@see` reference to original test

- [ ] **System properties ready**
  ```bash
  mvn test -Dtest=MyTest \
    -Daccumulo.start.home=/path/to/accumulo-2.1.x \
    -Daccumulo.upgrade.home=/path/to/accumulo-3.0.x
  ```

- [ ] **Test compiles without errors**
  ```bash
  mvn test-compile -pl minicluster
  ```

- [ ] **Imports are correct**
  - ProcessBasedMiniAccumuloCluster imported
  - ProcessBasedUpgradeTestBase extended
  - UpgradeCheckpoints imported

### During Test Execution

- [ ] **Cluster starts successfully**
  - Check logs for "Cluster started successfully"
  - Verify all TabletServers are up

- [ ] **Client accessible**
  - Can get client instance
  - Can perform basic operations

- [ ] **Core assertions pass**
  - Main test logic validates correctly
  - Data integrity checks pass

### After Test Execution

- [ ] **Test passes (or fails as expected)**
  - If original test passed, transformed test should pass
  - If failure, verify it's not due to transformation

- [ ] **Cluster cleans up properly**
  - No orphaned processes (jps shows clean)
  - Test directories cleaned up

- [ ] **Review transformation quality**
  - Maximum logic preserved?
  - Only necessary operations commented out?
  - Appropriate documentation added?

---

## Best Practices

### DO ✅

1. **Keep all AccumuloClient operations unchanged** - They already work!

2. **Use checkpoint-based testing** - Generate test methods for each checkpoint

3. **Close resources before upgrades** - BatchWriter, Scanner, etc.

4. **Test both single-version and multi-version scenarios**

5. **Verify node identity preservation** - Check addresses/ports unchanged after restart/upgrade

6. **Use base class @Before/@After** - Automatic cleanup guaranteed

### DON'T ❌

1. **Don't remove AccumuloClient operations** - They work identically

2. **Don't keep resources open across checkpoints** - They'll break during upgrades

3. **Don't mix MiniAccumuloCluster and ProcessBasedMiniAccumuloCluster** in same test

4. **Don't manually check environment variables** - System properties are handled automatically!

### Performance Considerations

1. **Process startup is slower** - ProcessBasedMiniAccumuloCluster takes longer to start
   - Be patient with cluster startup
   - Consider increasing timeouts (default @Timeout annotations)

2. **Upgrade operations take time** - Rolling upgrades restart servers one by one
   - Account for server restart time in test timeouts
   - Use waitClusterUp() after upgrades

3. **RPC overhead** - All operations go through RPC (same as original)
   - Not significant for most tests

---

## Quick Reference Decision Tree

```
Found operation in original test?
    │
    ├─> Is it AccumuloClient operation?
    │   └─> ✅ Keep as-is, no transformation needed
    │
    ├─> Is it cluster start/stop?
    │   └─> ⚠️ Use Builder pattern, add system property reading
    │
    ├─> Is it configuration?
    │   └─> ⚠️ Use Builder methods instead of MiniAccumuloConfig
    │
    ├─> Is it resource (BatchWriter/Scanner)?
    │   └─> ⚠️ Ensure closed before checkpoint() calls
    │
    └─> Is it internal cluster state?
        └─> ❌ Comment out if truly no client API (very rare!)
```

---

## Summary

### Transformation Success Criteria

A successful transformation:
1. ✅ Compiles without errors
2. ✅ Runs with ProcessBasedMiniAccumuloCluster
3. ✅ Preserves maximum test logic (usually 95%+)
4. ✅ Uses AccumuloClient APIs (unchanged)
5. ✅ Includes checkpoint-based test methods
6. ✅ Handles resource lifecycle correctly (close before checkpoints)
7. ✅ Passes when original test passed

### Key Takeaways

- **Most operations unchanged** - AccumuloClient API is already client-side
- **Use checkpoint-based testing** - Generate test methods for each checkpoint
- **Resource management is critical** - Close before upgrades, reopen after
- **Base class handles cleanup** - Extend ProcessBasedUpgradeTestBase
- **System properties automatic** - Just pass to Maven

---

**End of Template**

This guide provides comprehensive instructions for transforming Accumulo tests from MiniAccumuloCluster to ProcessBasedMiniAccumuloCluster with upgrade support.
