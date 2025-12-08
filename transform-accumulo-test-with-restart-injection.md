# Prompt: Transform Accumulo Mini Cluster Test with Restart Position Injection

## Objective

Transform an existing Accumulo mini cluster test to inject restart positions for distributed system restart testing. The transformation will generate:
1. A new test file with `_RestartInjected` suffix
2. A restart configuration file for the Maven plugin

## Input

- **Test File Path**: Path to the original test file (e.g., `/path/to/TestAccumuloOperations.java`)
- **Test Class**: Fully-qualified class name (e.g., `org.apache.accumulo.test.TestAccumuloOperations`)

## Output

1. **Generated Test File**: `{OriginalFileName}_RestartInjected.java` at the same directory as the input file
2. **Restart Configuration**: `restart-config.json` in the `restarts-config/` directory under the same module directory as the test file, if not exist create it.

## Transformation Instructions

### Step 1: Analyze the Original Test

Read the input test file and identify:

1. **Cluster Setup**: Find the Accumulo cluster instance variable (e.g., `MiniAccumuloCluster cluster` or `MiniAccumuloCluster accumulo`)
   - **IMPORTANT**: The cluster object may be inherited from a parent base test class rather than being directly initialized in the test class itself
   - Check the class hierarchy (look for `extends` clauses) and examine parent classes for cluster initialization
   - Common patterns:
     - Direct initialization: `private MiniAccumuloCluster cluster;` in the test class
     - Inherited from parent: `extends AccumuloClusterTestBase` where parent has the cluster field
     - Static shared cluster: `protected static MiniAccumuloCluster sharedCluster;` in base class
2. **Test Methods**: Identify all `@Test` annotated methods
3. **Critical Operations**: Look for operations that involve state transitions, such as:
   - Table operations: `createTable()`, `deleteTable()`, `renameTable()`, `cloneTable()`, `importTable()`, `exportTable()`
   - Write operations: `BatchWriter.addMutation()`, `BatchWriter.flush()`, `BatchWriter.close()`
   - Read operations: `Scanner`, `BatchScanner`, scan operations
   - Mutation operations: `put()`, `putDelete()`, conditional mutations
   - Flush operations: `flush()`, `compact()`, `majorCompact()`
   - Split operations: `addSplits()`, merge operations
   - User/security operations: `createLocalUser()`, `changeUserAuthorizations()`, `grantTablePermission()`
   - Configuration operations: Table configuration changes, property updates
   - Constraint operations: Adding/removing constraints
   - Iterator operations: Attaching/removing iterators
   - Namespace operations: `createNamespace()`, `deleteNamespace()`
   - Bulk import operations: `importDirectory()`, `addFilesTo()`
   - Replication operations: Replication setup and sync

### Step 2: Identify Restart Points

For each test method, identify potential restart points based on these criteria:

**Good Restart Points** (inject here):
- After table creation but before data insertion
- After batch write mutations (especially with flush)
- After flush/compact operations
- During scan operations (for consistency testing)
- After split operations
- Before/after table deletion
- After user/permission changes
- After configuration changes
- After iterator attachment
- During bulk import operations
- After namespace operations
- During multi-table operations

**Poor Restart Points** (avoid):
- Before cluster setup (no cluster exists yet)
- After cluster teardown (cluster already destroyed)
- During trivial operations (simple getters with no state changes)
- Operations that are too fast to test meaningful state

**Naming Convention for Restart Positions**:
- Use descriptive, lowercase names with underscores
- Pattern: `{operation}_{context}`
- Examples:
  - `after_table_create`
  - `after_batch_write`
  - `after_flush`
  - `during_scan`
  - `after_split`
  - `before_table_delete`
  - `after_compact`
  - `after_user_create`
  - `after_iterator_attach`
  - `after_bulk_import`
  - `before_mutation`
  - `after_namespace_create`

### Step 3: Generate the Restart-Injected Test File

Create a new test file with the following transformations:

#### 3.1 Package and Imports

```java
// Keep original package declaration
package org.apache.accumulo.test;

// Add these imports at the top (if not already present)
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

// Keep all original imports
```

#### 3.2 Class Declaration

```java
// Original class name: TestAccumuloOperations
// New class name: TestAccumuloOperations_RestartInjected

// Keep the same inheritance structure!
// If original: public class TestAccumuloOperations extends AccumuloClusterTestBase
// Then use:    public class TestAccumuloOperations_RestartInjected extends AccumuloClusterTestBase
public class TestAccumuloOperations_RestartInjected {
    // Keep all original fields and variables
}
```

**IMPORTANT**: If the test class extends a base class, preserve this inheritance in the generated test. The cluster object may be defined in the parent class.

#### 3.3 Cluster Setup and Teardown

Keep the `@Before`/`@BeforeAll`/`@BeforeEach` and `@After`/`@AfterAll`/`@AfterEach` methods unchanged:

```java
@BeforeAll
public static void setUp() throws Exception {
    // Keep original setup code unchanged
}

@AfterAll
public static void tearDown() throws Exception {
    // Keep original teardown code unchanged
}
```

**Note**: Setup methods may be inherited from parent class. In that case, don't add setup methods to the generated test.

#### 3.4 Transform Test Methods

For each `@Test` method, apply the following transformations:

**Original Test Method**:
```java
@Test
public void testTableOperations() throws Exception {
    String tableName = "test";

    client.tableOperations().create(tableName);

    try (BatchWriter writer = client.createBatchWriter(tableName)) {
        Mutation m = new Mutation("row1");
        m.put("cf", "cq", "value");
        writer.addMutation(m);
    }

    client.tableOperations().flush(tableName);

    assertTrue(client.tableOperations().exists(tableName));
}
```

**Transformed Test Method**:
```java
@Test
public void testTableOperations() throws Exception {
    String tableName = "test";

    client.tableOperations().create(tableName);

    // RESTART POINT 1: after_table_create
    RestartFramework.at("after_table_create")
        .on(cluster)  // Use the cluster instance (may be from parent class)
        .restart("manager")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    try (BatchWriter writer = client.createBatchWriter(tableName)) {
        Mutation m = new Mutation("row1");
        m.put("cf", "cq", "value");
        writer.addMutation(m);

        // RESTART POINT 2: after_batch_write
        RestartFramework.at("after_batch_write")
            .on(cluster)
            .restart("tablet_server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();
    }

    client.tableOperations().flush(tableName);

    // RESTART POINT 3: after_flush
    RestartFramework.at("after_flush")
        .on(cluster)
        .restart("tablet_server")
        .withIndex(0)
        .withMode(RestartMode.GRACEFUL)
        .execute();

    assertTrue(client.tableOperations().exists(tableName));
}
```

**IMPORTANT**: Identify the correct cluster variable name:
- If defined locally: `cluster`, `accumulo`, `mac`, etc.
- If inherited from parent: check parent class for field name (commonly `cluster`, `accumulo`, `sharedCluster`)
- Use the actual variable name in `.on(variableName)`

**Injection Pattern**:

1. **After Table Operations**:
   ```java
   client.tableOperations().create(tableName);

   // Inject restart point
   RestartFramework.at("after_table_create")
       .on(cluster)
       .restart("manager")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

2. **After Write Operations**:
   ```java
   writer.addMutation(m);
   writer.flush();

   // Inject restart point
   RestartFramework.at("after_batch_write")
       .on(cluster)
       .restart("tablet_server")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();
   ```

3. **Before Critical Operations**:
   ```java
   // Inject restart point before delete
   RestartFramework.at("before_table_delete")
       .on(cluster)
       .restart("manager")
       .withIndex(0)
       .withMode(RestartMode.GRACEFUL)
       .execute();

   client.tableOperations().delete(tableName);
   ```

4. **During Long Operations**:
   ```java
   // Start large scan
   try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
       int count = 0;
       for (Entry<Key,Value> entry : scanner) {
           count++;

           // Inject restart point in the middle
           if (count == 500) {
               RestartFramework.at("during_scan")
                   .on(cluster)
                   .restart("tablet_server")
                   .withIndex(0)
                   .withMode(RestartMode.GRACEFUL)
                   .execute();
           }
       }
   }
   ```

#### 3.5 Accumulo-Specific Node Roles

Accumulo has the following node types:

- **`manager`** (or `"master"`): The manager node that coordinates the cluster, manages tablet assignments, and handles metadata operations
- **`tablet_server`** (or `"tserver"`, `"worker"`): Worker nodes that serve tablets and handle data read/write operations
- **`garbage_collector`** (or `"gc"`): Garbage collection process that removes unused files
- **`scan_server`** (optional): Dedicated scan servers for offloading scan operations
- **`compactor`** (optional): Dedicated compaction processes

**Default Restart Configuration**:
Use these defaults for all injected restart points:
- **For table/metadata operations**: `"manager"` (manages table metadata, tablet assignment)
- **For data operations**: `"tablet_server"` (serves data, handles reads/writes)
- **For cluster-wide operations**: Both `"manager"` and `"tablet_server"`
- **Node Index**: `0` (first node)
- **Restart Mode**: `RestartMode.GRACEFUL` (default, safest)

#### 3.6 Node Role Selection Guidelines

| Operation Type | Primary Node Role | Secondary Node Role | Reason |
|----------------|-------------------|---------------------|--------|
| Table creation/deletion | `manager` | - | Manager manages table metadata |
| Table configuration | `manager` | `tablet_server` | Manager coordinates, tablet servers apply |
| Namespace operations | `manager` | - | Manager manages namespaces |
| Batch write operations | `tablet_server` | - | Tablet servers handle writes |
| Scan operations | `tablet_server` | - | Tablet servers serve data |
| Flush operations | `tablet_server` | - | Tablet servers flush data to disk |
| Compaction operations | `tablet_server` | `compactor` | Tablet servers or dedicated compactors |
| Split operations | `manager` | `tablet_server` | Manager coordinates, tablet servers execute |
| User/security operations | `manager` | - | Manager manages security |
| Iterator operations | `manager` | `tablet_server` | Manager stores config, tablet servers execute |
| Bulk import | `tablet_server` | `manager` | Tablet servers import, manager assigns |
| Constraint operations | `manager` | `tablet_server` | Manager stores, tablet servers enforce |

### Step 4: Generate Restart Configuration File

Create `restarts-config/restart-config.json` with the following structure:

```json
{
  "tests": [
    {
      "testClass": "org.apache.accumulo.test.TestAccumuloOperations_RestartInjected",
      "testMethod": "testTableOperations",
      "restartPoints": [
        {
          "position": "after_table_create",
          "targets": ["manager", "tablet_server"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_batch_write",
          "targets": ["tablet_server"],
          "modes": ["GRACEFUL", "CRASH", "DELAYED_CRASH"]
        },
        {
          "position": "after_flush",
          "targets": ["tablet_server"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

#### Configuration Generation Rules

For each test method in the transformed test:

1. **Create a test specification** with:
   - `testClass`: The fully-qualified name of the generated test class
   - `testMethod`: The test method name (same as original)
   - `restartPoints`: Array of restart point configurations

2. **For each restart point** injected in the test method:
   - `position`: The position identifier used in `.at("...")`
   - `targets`: Array of node roles to test
   - `modes`: Array of restart modes to test

#### Target Selection for Accumulo Operations

**Manager-only operations** (`["manager"]`):
- Table creation, deletion, renaming, cloning
- Namespace operations
- User/security management
- Table configuration (initial setup)
- System configuration
- Tablet assignment decisions

**TabletServer-only operations** (`["tablet_server"]`):
- Data read/write (Mutations, Scans)
- Batch writer operations
- Flush operations
- Compaction (if not using dedicated compactors)
- Local data serving
- Cache operations

**Both Manager and TabletServer** (`["manager", "tablet_server"]`):
- Table creation + initial writes
- Split operations
- Bulk import operations
- Iterator attachment (affects both metadata and execution)
- Constraint operations
- Replication operations

#### Mode Selection Guidelines

- **`["GRACEFUL"]`**: Basic test, verify restart works
  - Use for: Initial testing, simple state transitions

- **`["GRACEFUL", "CRASH"]`**: Standard test, verify crash recovery
  - Use for: Table operations, data operations, flush operations

- **`["GRACEFUL", "CRASH", "DELAYED_CRASH"]`**: Advanced test, verify timing-sensitive operations
  - Use for: Batch writes, flush operations, compaction, split operations, distributed coordination

### Step 5: File Placement

1. **Generated Test File**:
   - Location: Same directory as original test file
   - Name: `{OriginalClassName}_RestartInjected.java`
   - Example: `TestAccumuloOps.java` → `TestAccumuloOps_RestartInjected.java`

2. **Restart Configuration**:
   - Location: `restarts-config/` directory under the same module directory as the test file
   - Name: `restart-config.json`
   - If file exists, append to the `tests` array (avoid duplicates)
   - If file doesn't exist, create new file

## Example Transformation

### Input: `TestBatchWrite.java`

```java
package org.apache.accumulo.test.functional;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class TestBatchWrite {
    private static MiniAccumuloCluster cluster;
    private static AccumuloClient client;

    @BeforeClass
    public static void setUp() throws Exception {
        File tempDir = Files.createTempDirectory("accumulo-test").toFile();
        cluster = new MiniAccumuloCluster(tempDir, "password");
        cluster.start();
        client = cluster.createAccumuloClient("root", new PasswordToken("password"));
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (cluster != null) {
            cluster.stop();
        }
    }

    @Test
    public void testWriteAndFlush() throws Exception {
        String tableName = "test_table";
        client.tableOperations().create(tableName);

        try (BatchWriter writer = client.createBatchWriter(tableName)) {
            Mutation m = new Mutation("row1");
            m.put("cf1", "cq1", "value1");
            writer.addMutation(m);
        }

        client.tableOperations().flush(tableName, null, null, true);

        try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
            int count = 0;
            for (Map.Entry<Key,Value> entry : scanner) {
                count++;
            }
            assertEquals(1, count);
        }
    }
}
```

### Output 1: `TestBatchWrite_RestartInjected.java`

```java
package org.apache.accumulo.test.functional;

import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restarttest.api.RestartFramework;
import org.restarttest.core.RestartMode;

import java.io.File;

import static org.junit.Assert.*;

public class TestBatchWrite_RestartInjected {
    private static MiniAccumuloCluster cluster;
    private static AccumuloClient client;

    @BeforeClass
    public static void setUp() throws Exception {
        File tempDir = Files.createTempDirectory("accumulo-test").toFile();
        cluster = new MiniAccumuloCluster(tempDir, "password");
        cluster.start();
        client = cluster.createAccumuloClient("root", new PasswordToken("password"));
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (cluster != null) {
            cluster.stop();
        }
    }

    @Test
    public void testWriteAndFlush() throws Exception {
        String tableName = "test_table";
        client.tableOperations().create(tableName);

        // RESTART POINT 1: after_table_create
        RestartFramework.at("after_table_create")
            .on(cluster)
            .restart("manager")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        try (BatchWriter writer = client.createBatchWriter(tableName)) {
            Mutation m = new Mutation("row1");
            m.put("cf1", "cq1", "value1");
            writer.addMutation(m);

            // RESTART POINT 2: after_batch_write
            RestartFramework.at("after_batch_write")
                .on(cluster)
                .restart("tablet_server")
                .withIndex(0)
                .withMode(RestartMode.GRACEFUL)
                .execute();
        }

        client.tableOperations().flush(tableName, null, null, true);

        // RESTART POINT 3: after_flush
        RestartFramework.at("after_flush")
            .on(cluster)
            .restart("tablet_server")
            .withIndex(0)
            .withMode(RestartMode.GRACEFUL)
            .execute();

        try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
            int count = 0;
            for (Map.Entry<Key,Value> entry : scanner) {
                count++;
            }
            assertEquals(1, count);
        }
    }
}
```

### Output 2: `restarts-config/restart-config.json`

```json
{
  "tests": [
    {
      "testClass": "org.apache.accumulo.test.functional.TestBatchWrite_RestartInjected",
      "testMethod": "testWriteAndFlush",
      "restartPoints": [
        {
          "position": "after_table_create",
          "targets": ["manager", "tablet_server"],
          "modes": ["GRACEFUL", "CRASH"]
        },
        {
          "position": "after_batch_write",
          "targets": ["tablet_server"],
          "modes": ["GRACEFUL", "CRASH", "DELAYED_CRASH"]
        },
        {
          "position": "after_flush",
          "targets": ["tablet_server"],
          "modes": ["GRACEFUL", "CRASH"]
        }
      ]
    }
  ]
}
```

## Accumulo-Specific Patterns

### Pattern 1: Table Lifecycle Testing

```java
@Test
public void testTableLifecycle() throws Exception {
    String tableName = "test";

    // Create
    client.tableOperations().create(tableName);

    RestartFramework.at("after_create")
        .on(cluster)
        .restart("manager")
        .execute();

    // Write data
    writeData(client, tableName, 100);

    RestartFramework.at("after_write")
        .on(cluster)
        .restart("tablet_server")
        .execute();

    // Flush
    client.tableOperations().flush(tableName);

    RestartFramework.at("after_flush")
        .on(cluster)
        .restart("tablet_server")
        .execute();

    // Delete
    client.tableOperations().delete(tableName);
}
```

### Pattern 2: Batch Write Testing

```java
@Test
public void testBatchWrite() throws Exception {
    try (BatchWriter writer = client.createBatchWriter(tableName)) {
        // Write multiple mutations
        for (int i = 0; i < 1000; i++) {
            Mutation m = new Mutation("row" + i);
            m.put("cf", "cq", "value" + i);
            writer.addMutation(m);
        }

        RestartFramework.at("before_flush")
            .on(cluster)
            .restart("tablet_server")
            .execute();

        writer.flush();

        RestartFramework.at("after_flush")
            .on(cluster)
            .restart("tablet_server")
            .execute();
    }

    // Verify data persists
    verifyData(client, tableName, 1000);
}
```

### Pattern 3: Split and Compaction Testing

```java
@Test
public void testSplitAndCompact() throws Exception {
    // Load data
    loadData(client, tableName, 10000);

    // Add splits
    TreeSet<Text> splits = new TreeSet<>();
    splits.add(new Text("row5000"));
    client.tableOperations().addSplits(tableName, splits);

    RestartFramework.at("after_split")
        .on(cluster)
        .restart("manager")
        .execute();

    // Compact
    client.tableOperations().compact(tableName, null, null, true, true);

    RestartFramework.at("after_compact")
        .on(cluster)
        .restart("tablet_server")
        .execute();

    // Verify data
    verifyData(client, tableName, 10000);
}
```

### Pattern 4: Scan Consistency Testing

```java
@Test
public void testScanConsistency() throws Exception {
    // Write data
    loadData(client, tableName, 1000);

    RestartFramework.at("after_write")
        .on(cluster)
        .restart("tablet_server")
        .execute();

    // Scan during restart
    try (Scanner scanner = client.createScanner(tableName, Authorizations.EMPTY)) {
        int count = 0;
        for (Map.Entry<Key,Value> entry : scanner) {
            count++;

            // Restart during scan
            if (count == 500) {
                RestartFramework.at("during_scan")
                    .on(cluster)
                    .restart("tablet_server")
                    .withIndex(0)
                    .execute();
            }
        }
        assertEquals(1000, count);
    }
}
```

### Pattern 5: User and Security Testing

```java
@Test
public void testUserOperations() throws Exception {
    String username = "testuser";
    String password = "testpass";

    // Create user
    client.securityOperations().createLocalUser(username, new PasswordToken(password));

    RestartFramework.at("after_user_create")
        .on(cluster)
        .restart("manager")
        .execute();

    // Grant permissions
    client.securityOperations().grantTablePermission(username, tableName, TablePermission.WRITE);

    RestartFramework.at("after_grant_permission")
        .on(cluster)
        .restart("manager")
        .execute();

    // Verify permissions persist
    assertTrue(client.securityOperations().hasTablePermission(username, tableName, TablePermission.WRITE));
}
```

### Pattern 6: Iterator Testing

```java
@Test
public void testIterators() throws Exception {
    // Create table with iterator
    IteratorSetting setting = new IteratorSetting(10, "summing", SummingCombiner.class);
    SummingCombiner.setEncodingType(setting, LongCombiner.Type.STRING);

    NewTableConfiguration ntc = new NewTableConfiguration().attachIterator(setting);
    client.tableOperations().create(tableName, ntc);

    RestartFramework.at("after_create_with_iterator")
        .on(cluster)
        .restart("manager")
        .execute();

    // Write data that should be combined
    writeCombinerData(client, tableName);

    RestartFramework.at("after_write")
        .on(cluster)
        .restart("tablet_server")
        .execute();

    // Verify combiner worked
    verifyCombinedData(client, tableName);
}
```

### Pattern 7: Bulk Import Testing

```java
@Test
public void testBulkImport() throws Exception {
    // Prepare bulk import files
    String importDir = prepareBulkImportFiles();

    RestartFramework.at("before_bulk_import")
        .on(cluster)
        .restart("tablet_server")
        .execute();

    // Import
    client.tableOperations().importDirectory(importDir).to(tableName).load();

    RestartFramework.at("after_bulk_import")
        .on(cluster)
        .restart("manager")
        .execute();

    // Verify imported data
    verifyImportedData(client, tableName);
}
```

## Validation Checklist

After transformation, verify:

- [ ] Generated test file compiles without errors
- [ ] All original test logic is preserved
- [ ] Class inheritance is maintained (if test extends a base class)
- [ ] Correct cluster variable name is used (check parent classes if inherited)
- [ ] Restart points are placed at meaningful Accumulo operations
- [ ] Restart position names are descriptive and Accumulo-specific
- [ ] Node roles (manager/tablet_server/gc) are correctly chosen
- [ ] Configuration file has correct fully-qualified class names
- [ ] Configuration file includes all restart points from the test
- [ ] Target arrays match the operation type (Manager vs TabletServer vs both)
- [ ] Mode arrays are appropriate for timing sensitivity
- [ ] Files are placed in correct locations
- [ ] Original test file is not modified (only new files created)

## Advanced Scenarios

### Multiple Test Methods

If the original test has multiple `@Test` methods:

1. Transform each method independently
2. Inject restart points in each method
3. Create a separate test specification for each method in the configuration

Example configuration:
```json
{
  "tests": [
    {
      "testClass": "org.apache.accumulo.test.TestAccumulo_RestartInjected",
      "testMethod": "testWrite",
      "restartPoints": [...]
    },
    {
      "testClass": "org.apache.accumulo.test.TestAccumulo_RestartInjected",
      "testMethod": "testFlush",
      "restartPoints": [...]
    }
  ]
}
```

### Inherited Cluster from Base Class

**CRITICAL**: Many Accumulo tests extend a base test class that contains the cluster setup:

**Example Base Class** (`AccumuloClusterTestBase.java`):
```java
public class AccumuloClusterTestBase {
    protected static MiniAccumuloCluster cluster;
    protected static AccumuloClient client;

    @BeforeAll
    public static void setupCluster() throws Exception {
        cluster = new MiniAccumuloCluster(...);
        cluster.start();
        client = cluster.createAccumuloClient(...);
    }

    @AfterAll
    public static void teardownCluster() throws Exception {
        client.close();
        cluster.stop();
    }
}
```

**Example Test Using Base Class**:
```java
public class TestTableOps extends AccumuloClusterTestBase {
    @Test
    public void testCreate() throws Exception {
        // No cluster setup here - it's in the parent!
        client.tableOperations().create("test");
    }
}
```

**Transformation Strategy**:
1. **Preserve the inheritance**: `TestTableOps_RestartInjected extends AccumuloClusterTestBase`
2. **Do NOT duplicate setup/teardown** methods in the generated test
3. **Use the inherited cluster variable**: `.on(cluster)` (assuming parent uses `cluster`)
4. **Check parent class** to identify:
   - Cluster variable name (e.g., `cluster`, `accumulo`, `sharedCluster`)
   - Client variable name (e.g., `client`, `accumuloClient`)
   - Whether cluster is `static` or instance variable

**Generated Test**:
```java
public class TestTableOps_RestartInjected extends AccumuloClusterTestBase {
    // NO setup/teardown methods - inherited from parent!

    @Test
    public void testCreate() throws Exception {
        client.tableOperations().create("test");

        // Use inherited 'cluster' variable
        RestartFramework.at("after_create")
            .on(cluster)
            .restart("manager")
            .execute();
    }
}
```

### Helper Methods

If the test has helper methods:

1. **Do not inject restart points in helper methods**
2. Only inject in `@Test` annotated methods
3. Keep helper methods unchanged

### Tests Without Obvious Restart Points

If a test has no clear state transitions:

1. Inject restart points within the range of (a) after cluster setup and (b) before cluster teardown
2. Evenly distribute restart points to cover the test execution
3. You MUST Use percentage-based positions (e.g., `at_25_percent`, `at_50_percent`) to at least cover 4 points during the test execution
4. Find cluster operations to place restart points around

**IMPORTANT**: You are NOT allowed to skip any test transformation due to lack of restart points. Always inject at least one restart point per test method.

## Common Accumulo Operations and Suggested Restart Points

| Accumulo Operation | Suggested Restart Point Name | Node Role | Timing |
|-------------------|------------------------------|-----------|--------|
| `createTable()` | `after_table_create` | `manager` | After |
| `deleteTable()` | `before_table_delete` | `manager` | Before |
| `renameTable()` | `after_table_rename` | `manager` | After |
| `cloneTable()` | `after_table_clone` | `manager` | After |
| `addMutation()` | `after_mutation` | `tablet_server` | After |
| `BatchWriter.flush()` | `after_batch_flush` | `tablet_server` | After |
| `BatchWriter.close()` | `before_batch_close` | `tablet_server` | Before |
| `scan()` | `during_scan` | `tablet_server` | During |
| `flush()` | `after_flush` | `tablet_server` | After |
| `compact()` | `before_compact`, `after_compact` | `tablet_server` | Before/After |
| `majorCompact()` | `before_major_compact`, `after_major_compact` | `tablet_server` | Before/After |
| `addSplits()` | `after_split` | `manager`, `tablet_server` | After |
| `merge()` | `after_merge` | `manager`, `tablet_server` | After |
| `createLocalUser()` | `after_user_create` | `manager` | After |
| `grantTablePermission()` | `after_grant_permission` | `manager` | After |
| `attachIterator()` | `after_attach_iterator` | `manager` | After |
| `importDirectory()` | `after_bulk_import` | `tablet_server`, `manager` | After |
| `createNamespace()` | `after_namespace_create` | `manager` | After |
| `setProperty()` | `after_property_set` | `manager` | After |

## Notes

- **Non-invasive**: Original test file is never modified
- **Incremental**: Can transform tests one at a time
- **Compatible**: Generated tests can run both with and without restart injection
- **Configurable**: Configuration file allows easy adjustment of test matrix
- **Accumulo-aware**: Node role selection is specific to Accumulo architecture (Manager vs TabletServer vs GC)
- **Inheritance-aware**: Handles tests that inherit cluster setup from parent classes

## Dependencies

Ensure the following dependencies are included in the test's module to use the Restart Testing Framework:

```xml
<dependencies>
    <!-- Existing dependencies... -->

    <!-- Restart Testing Framework - Core -->
    <dependency>
        <groupId>org.restarttest</groupId>
        <artifactId>restart-core</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>

    <!-- Restart Testing Framework - Accumulo Adapter -->
    <dependency>
        <groupId>org.restarttest</groupId>
        <artifactId>restart-accumulo-adapter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Build Requirements

**IMPORTANT**: Accumulo tests require Java 17 to build and compile.

Before building or running Accumulo tests with restart injection:

```bash
# Ensure Java 17 is being used
java -version  # Should show Java 17

# If using SDKMAN or similar:
# sdk use java 17.x.x

# Then build the tests
mvn clean test
```
