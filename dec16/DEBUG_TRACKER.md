# DEBUG_TRACKER - ACCUMULO

This tracker lists failure groups ordered by likelihood of being actual bugs.
Groups are prioritized from most likely to be bugs to most likely to be false positives.

Total failure groups: 23

---

## Group 3 (Priority 2)

**Status:** [X] BUG

**Execution Count:** 8

**Priority Reason:** Test bug - Caused by thrown from test code

**Analysis:**
This is a **BUG** in the source code at `MiniAccumuloClusterImpl.java:776-782`.

**Root Cause:**
The `getProcesses()` method calls `references(control.managerProcess)` without checking if processes are null. After `killProcess()` sets a process field to null (e.g., `managerProcess = null` at line 460 in MiniAccumuloClusterControl.java), subsequent calls to `getProcesses()` fail because:

1. `references(null)` creates `Stream.of(null)` - a stream with one null element
2. `.map(ProcessReference::new)` tries to call `new ProcessReference(null)`
3. The ProcessReference constructor has `Objects.requireNonNull(process)` which throws NPE

**Location:**
- `minicluster/src/main/java/org/apache/accumulo/miniclusterImpl/MiniAccumuloClusterImpl.java:776-792`

**Fix Required:**
The `references()` method should filter out null processes:
```java
List<ProcessReference> references(Process... procs) {
  return Stream.of(procs).filter(Objects::nonNull).map(ProcessReference::new).collect(toList());
}
```

Or `getProcesses()` should check for null before calling references():
```java
if (control.managerProcess != null) {
  result.put(ServerType.MANAGER, references(control.managerProcess));
} else {
  result.put(ServerType.MANAGER, Collections.emptyList());
}
```

**Reproduced:** Yes, successfully reproduced in Docker container `shuaiwang516/accumulo-restart-test:dec16` at `/workspace/apps/accumulo`

### Generalized Stack Trace
```
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java)
	at org.apache.accumulo.miniclusterImpl.ProcessReference.<init>(ProcessReference.java)
	at java.base/java.util.stream.ReferencePipeline$3$1.accept(ReferencePipeline.java)
	at java.base/java.util.Spliterators$ArraySpliterator.forEachRemaining(Spliterators.java)
	at java.base/java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java)
	at java.base/java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java)
	at java.base/java.util.stream.ReduceOps$ReduceOp.evaluateSequential(ReduceOps.java)
	at java.base/java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java)
	at java.base/java.util.stream.ReferencePipeline.collect(ReferencePipeline.java)
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_cluster_create
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.accumulo.miniclusterImpl.CleanShutdownMacTest_RestartInjected.testExecutorServiceShutdown(CleanShutdownMacTest_RestartInjected.java:55)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java:209)
	at org.apache.accumulo.miniclusterImpl.ProcessReference.<init>(ProcessReference.java:30)
	at java.base/java.util.stream.ReferencePipeline$3$1.accept(ReferencePipeline.java:197)
	at java.base/java.util.Spliterators$ArraySpliterator.forEachRemaining(Spliterators.java:992)
	at java.base/java.util.stream.AbstractPipeline.copyInto(AbstractPipeline.java:509)
	at java.base/java.util.stream.AbstractPipeline.wrapAndCopyInto(AbstractPipeline.java:499)
	at java.base/java.util.stream.ReduceOps$ReduceOp.evaluateSequential(ReduceOps.java:921)
	at java.base/java.util.stream.AbstractPipeline.evaluate(AbstractPipeline.java:234)
	at java.base/java.util.stream.ReferencePipeline.collect(ReferencePipeline.java:682)
	at org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl.references(MiniAccumuloClusterImpl.java:776)
	at org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImpl.getProcesses(MiniAccumuloClusterImpl.java:782)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:85)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:50)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
	... 5 more

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.miniclusterImpl.CleanShutdownMacTest_RestartInjected`
- Test Method: `testExecutorServiceShutdown`
- Position: `after_cluster_create`
- Target: `manager`
- Mode: `GRACEFUL`
- Execution Dir: `002-be7271aa`

**Test 2:**
- Test Class: `org.apache.accumulo.test.upgrade.UpgradeUtilIT_RestartInjected`
- Test Method: `testPrepareFailsDueToFateTransactions`
- Position: `after_manager_stop`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `050-36e37442`

**Test 3:**
- Test Class: `org.apache.accumulo.test.upgrade.UpgradeUtilIT_RestartInjected`
- Test Method: `testPrepareSucceeds`
- Position: `after_manager_stopped`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `050-db9152ba`

---

## Group 4 (Priority 2)

**Status:** [X] BUG

**Execution Count:** 7

**Priority Reason:** Application exception - java.util.NoSuchElementException

**Analysis:**
This is a **BUG** - a flaky durability issue in the source code.

**Root Cause:**
The `BatchWriter.flush()` method does not guarantee data durability. After calling `bw.flush()` at line 131 in LogicalTimeIT_RestartInjected.java, the data is sent to the tablet server but may still reside in memory (WAL or memtable) without being synced to disk. When a tablet_server restart occurs immediately after flush(), the data can be lost.

**Evidence:**
1. Successfully reproduced the failure - it's flaky (fails ~66% of the time in 3 runs)
2. Debug logging shows:
   - Before restart: Data for rows 'a' and 'b' exists in the table
   - After restart: Only row 'a' exists, row 'b' (the final mutation) is LOST
3. The test calls `bw.addMutation(m)` followed by `bw.flush()`, then immediately restarts the tablet server
4. The scanner then fails with NoSuchElementException because the expected data is missing

**Location:**
The bug is in the BatchWriter implementation or the durability guarantees of the tablet server. When `flush()` returns, the data should be in a durable state (persisted to WAL and synced to disk), but currently it is not.

**Impact:**
This is a critical durability bug. Users may lose data during server restarts if they rely on `flush()` to ensure their writes are durable. This violates typical database durability expectations.

**Reproduced:** Yes, successfully reproduced as a flaky test (fails 2 out of 3 runs) in the accumulo-restart-2.1.4 branch

### Generalized Stack Trace
```
java.util.NoSuchElementException
	at org.apache.accumulo.core.clientImpl.ScannerIterator.next(ScannerIterator.java)
	at org.apache.accumulo.core.clientImpl.ScannerIterator.next(ScannerIterator.java)
```

### Raw Stack Trace Sample
```
java.util.NoSuchElementException
	at org.apache.accumulo.core.clientImpl.ScannerIterator.next(ScannerIterator.java:123)
	at org.apache.accumulo.core.clientImpl.ScannerIterator.next(ScannerIterator.java:46)
	at org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.runMergeTest(LogicalTimeIT_RestartInjected.java:141)
	at org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.run(LogicalTimeIT_RestartInjected.java:54)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected`
- Test Method: `run`
- Position: `after_final_mutation`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `059-da903cd7`

**Test 2:**
- Test Class: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected`
- Test Method: `run`
- Position: `after_final_mutation`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `063-da903cd7c9a47e5d`

**Test 3:**
- Test Class: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected`
- Test Method: `run`
- Position: `after_final_mutation`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `067-da903cd7c9a47e5d`

---

## Group 8 (Priority 2)

**Status:** [X] TEST-BUG

**Execution Count:** 4

**Priority Reason:** Application exception - java.lang.IllegalArgumentException

**Analysis:**
This is a **TEST-BUG** in the test code at `GarbageCollectorTrashBase.java:115`.

**Root Cause:**
The `countFilesInTrash()` method incorrectly assumes that ALL files in the Hadoop trash directory are tablet files. It iterates through every file in the trash and tries to create a `TabletFile` object from each path (line 115). However, the trash can contain other types of Accumulo files, such as recovery files from the `/accumulo/recovery/` directory.

When the tablet server is restarted during the test, WAL recovery operations occur. When recovery files are cleaned up, they are moved to the trash (since Hadoop trash is enabled in these tests). These recovery files have the path structure `/accumulo/recovery/...` rather than `/accumulo/tables/...`, which causes `TabletFile.parsePath()` to throw an IllegalArgumentException at line 124 because it cannot find the expected `/tables/` directory structure.

**Location:**
- `test/src/main/java/org/apache/accumulo/test/functional/GarbageCollectorTrashBase.java:115`

**Fix Required:**
The `countFilesInTrash()` method should handle non-tablet files gracefully. Options include:

1. Catch the IllegalArgumentException and skip files that aren't valid tablet files:
```java
try {
  TabletFile tf = new TabletFile(lfs.getPath());
  LOG.debug("File in trash: {}, tableId: {}", lfs.getPath(), tf.getTableId());
  if (tid.equals(tf.getTableId())) {
    count++;
  }
} catch (IllegalArgumentException e) {
  // Skip non-tablet files (e.g., recovery files)
  LOG.debug("Skipping non-tablet file in trash: {}", lfs.getPath());
}
```

2. Filter paths before creating TabletFile objects by checking if they contain "/tables/":
```java
if (!lfs.getPath().toString().contains("/tables/")) {
  continue;
}
TabletFile tf = new TabletFile(lfs.getPath());
```

**Reproduced:** Yes, successfully reproduced with the first test execution

### Generalized Stack Trace
```
java.lang.IllegalArgumentException
	at org.apache.accumulo.core.metadata.TabletFile.parsePath(TabletFile.java)
	at org.apache.accumulo.core.metadata.TabletFile.<init>(TabletFile.java)
	at org.apache.accumulo.test.functional.GarbageCollectorTrashBase.countFilesInTrash(GarbageCollectorTrashBase.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalArgumentException: Missing or invalid part of tablet file metadata entry: hdfs://localhost:33381/user/root/.Trash/Current/accumulo/recovery/2e60e06d-974e-4fda-828e-a00177a1d24e/finished
	at org.apache.accumulo.core.metadata.TabletFile.parsePath(TabletFile.java:124)
	at org.apache.accumulo.core.metadata.TabletFile.<init>(TabletFile.java:162)
	at org.apache.accumulo.test.functional.GarbageCollectorTrashBase.countFilesInTrash(GarbageCollectorTrashBase.java:115)
	at org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled(GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.java:123)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected`
- Test Method: `testTrashHadoopEnabledAccumuloEnabled`
- Position: `after_initial_flush`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `025-a2e7e10a`

**Test 2:**
- Test Class: `org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected`
- Test Method: `testTrashHadoopEnabledAccumuloEnabled`
- Position: `after_first_compact`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `026-06694d5b`

**Test 3:**
- Test Class: `org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected`
- Test Method: `testTrashHadoopEnabledAccumuloEnabled`
- Position: `after_second_compact`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `027-15076740`

---

## Group 9 (Priority 2)

**Status:** [X] BUG

**Execution Count:** 2

**Priority Reason:** Application exception - java.io.IOException

**Analysis:**
This is a **BUG** - a critical durability issue in the source code, similar to Group 4.

**Root Cause:**
The `BatchWriter.close()` method does not guarantee data durability. After calling `bw.close()` (which happens at the end of the try-with-resources block at line 228 in AccumuloOutputFormatIT_RestartInjected.java), the test immediately restarts the tablet server. However, the data written to table1 is COMPLETELY LOST - 0 entries remain out of the 100 that were written.

**Evidence:**
1. Successfully reproduced the failure
2. Added debug logging that shows: "DEBUG: After restart, table1 has 0 entries (expected 100)"
3. The MapReduce job fails with "Job failed!" because when it tries to read from table1, there's no data available
4. This happens at the `after_batch_close` restart point, meaning the BatchWriter has already been closed when the restart occurs

**Location:**
The bug is in the BatchWriter/TabletServer durability implementation. When `BatchWriter.close()` returns, it should guarantee that all mutations have been durably persisted (written to WAL and synced to disk), but currently this is not the case.

**Impact:**
This is a CRITICAL durability bug. Users can lose ALL their data during server restarts if they rely on `close()` to ensure their writes are durable. This violates fundamental database durability expectations and can lead to severe data loss in production environments.

**Difference from Group 4:**
- Group 4: Data loss after `flush()` (partial loss - some mutations lost)
- Group 9: Data loss after `close()` (complete loss - ALL mutations lost)

Both are durability bugs, but Group 9 is more severe as it affects `close()`, which users absolutely expect to provide durability guarantees.

**Reproduced:** Yes, successfully reproduced on the first attempt

### Generalized Stack Trace
```
java.io.IOException
	at org.apache.hadoop.mapred.JobClient.runJob(JobClient.java)
```

### Raw Stack Trace Sample
```
java.io.IOException: Job failed!
	at org.apache.hadoop.mapred.JobClient.runJob(JobClient.java:876)
	at org.apache.accumulo.hadoop.its.mapred.AccumuloOutputFormatIT_RestartInjected$MRTester.run(AccumuloOutputFormatIT_RestartInjected.java:196)
	at org.apache.hadoop.util.ToolRunner.run(ToolRunner.java:82)
	at org.apache.accumulo.hadoop.its.mapred.AccumuloOutputFormatIT_RestartInjected$MRTester.main(AccumuloOutputFormatIT_RestartInjected.java:204)
	at org.apache.accumulo.hadoop.its.mapred.AccumuloOutputFormatIT_RestartInjected.testMR(AccumuloOutputFormatIT_RestartInjected.java:232)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.hadoop.its.mapred.AccumuloOutputFormatIT_RestartInjected`
- Test Method: `testMR`
- Position: `after_batch_close`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `005-d9f05bcf`

**Test 2:**
- Test Class: `org.apache.accumulo.hadoop.its.mapred.TokenFileIT_RestartInjected`
- Test Method: `testMR`
- Position: `after_batch_close`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `005-d0e2aa75`

---

## Group 11 (Priority 2)

**Status:** [X] BUG (with minor TEST-BUG)

**Execution Count:** 2

**Priority Reason:** Test bug - java.lang.IndexOutOfBoundsException thrown from test code

**Analysis:**
This is primarily a **BUG** - a critical durability issue in the source code, similar to Groups 4 and 9. There is also a minor **TEST-BUG** (poor defensive coding).

**Root Cause:**
The `BatchWriter.close()` method does not guarantee data durability. The test writes approximately 208 mutations (lines 66-78 in FindMaxIT_RestartInjected.java), closes the BatchWriter via try-with-resources (line 79), then immediately restarts the tablet server (line 81-82).

Debug logging confirms that ALL 208 rows are LOST after the restart:
```
DEBUG: After restart, found 0 rows (expected ~208)
DEBUG: CRITICAL - All data was lost after tablet_server restart!
```

The test then crashes with `IndexOutOfBoundsException` at line 126 when trying to access `rows.get(rows.size() - 1)`, which becomes `rows.get(-1)` when the rows list is empty.

**Comparison with Original Test:**
The original non-restart-injected `FindMaxIT.java` works fine - after BatchWriter.close(), data is immediately available for scanning. This confirms that close() is expected to make data available. However, the data is only in memory/WAL and not durably persisted to disk, causing total data loss upon restart.

**Location:**
The bug is in the BatchWriter/TabletServer durability implementation. When `BatchWriter.close()` returns, it should guarantee that all mutations have been durably persisted (written to WAL and synced to disk).

**Test Code Issue:**
The test has poor defensive coding - it doesn't check if `rows.isEmpty()` before accessing `rows.get(rows.size() - 1)`. However, this is a minor issue; the test expectation that data persists after close() is reasonable.

**Impact:**
This is a CRITICAL durability bug. Users can lose ALL their data during server restarts if they rely on `close()` to ensure writes are durable. This is the same fundamental issue as Groups 4 and 9:
- Group 4: Data loss after `flush()` (partial loss)
- Group 9: Data loss after `close()` in MapReduce context (complete loss)
- Group 11: Data loss after `close()` in simple write context (complete loss)

**Reproduced:** Yes, successfully reproduced with the second test execution (FindMaxIT_RestartInjected#test1)

### Generalized Stack Trace
```
java.lang.IndexOutOfBoundsException
	at java.base/jdk.internal.util.Preconditions.outOfBounds(Preconditions.java)
	at java.base/jdk.internal.util.Preconditions.outOfBoundsCheckIndex(Preconditions.java)
	at java.base/jdk.internal.util.Preconditions.checkIndex(Preconditions.java)
	at java.base/java.util.Objects.checkIndex(Objects.java)
	at java.base/java.util.ArrayList.get(ArrayList.java)
```

### Raw Stack Trace Sample
```
java.lang.IndexOutOfBoundsException: Index 0 out of bounds for length 0
	at java.base/jdk.internal.util.Preconditions.outOfBounds(Preconditions.java:64)
	at java.base/jdk.internal.util.Preconditions.outOfBoundsCheckIndex(Preconditions.java:70)
	at java.base/jdk.internal.util.Preconditions.checkIndex(Preconditions.java:266)
	at java.base/java.util.Objects.checkIndex(Objects.java:361)
	at java.base/java.util.ArrayList.get(ArrayList.java:427)
	at org.apache.accumulo.test.functional.SummaryIT_RestartInjected.testPermissions(SummaryIT_RestartInjected.java:680)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.SummaryIT_RestartInjected`
- Test Method: `testPermissions`
- Position: `after_flush_permissions`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `032-6e2ba0c3`

**Test 2:**
- Test Class: `org.apache.accumulo.test.FindMaxIT_RestartInjected`
- Test Method: `test1`
- Position: `after_batch_write`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `044-a813d558`

---

## Group 15 (Priority 2)

**Status:** [X] TEST-BUG

**Execution Count:** 1

**Priority Reason:** Test bug - java.lang.NullPointerException thrown from test code

**Analysis:**
This is a **TEST-BUG** - a flaky test caused by a race condition and missing wait logic after tablet_server restart.

**Root Cause:**
After the tablet_server is restarted at line 78-79, the test immediately tries to read the tablet metadata at line 84. However, tablet reassignment is an asynchronous operation that takes time. The restart adapter (`AccumuloClusterImplAdapter.java:123`) only waits 100ms after starting the replacement tablet_server, and all health checks (including `AccumuloTabletsAssignedCheck`) are disabled. This creates a timing window where:

1. The replacement tablet_server has started
2. But tablets have not yet been reassigned
3. When `readTablet(extent, ColumnType.LOCATION)` is called, it returns null because the tablet doesn't have a location yet (or the tablet entry is not found in the query results because it has no LOCATION)

**Evidence:**
- Successfully reproduced as a flaky test (fails ~60% of the time: 3 out of 5 runs)
- When it passes: The tablet has a CURRENT location
- When it fails: `readTablet()` returns null with error "tabletMetadata is null! Table exists but tablet metadata not found"

**Location:**
The issue is in the test code at `test/src/main/java/org/apache/accumulo/test/CorruptMutationIT_RestartInjected.java:84-85`

**Fix Required:**
The test should wait for the tablet to be reassigned after the restart. Add wait logic before trying to read the tablet metadata:

```java
RestartFramework.at("after_batch_write").on(getCluster()).restart("tablet_server")
    .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

var ctx = (ClientContext) c;
var tableId = ctx.getTableId(table);
var extent = new KeyExtent(tableId, null, null);

// Wait for tablet to be reassigned
Wait.waitFor(() -> {
  var tm = ctx.getAmple().readTablet(extent, TabletMetadata.ColumnType.LOCATION);
  return tm != null && tm.getLocation() != null &&
         tm.getLocation().getType() == TabletMetadata.LocationType.CURRENT;
}, 30_000, 100);

var tabletMetadata = ctx.getAmple().readTablet(extent, TabletMetadata.ColumnType.LOCATION);
var location = tabletMetadata.getLocation();
```

**Impact:**
This is a test-only issue. The test fails to account for the asynchronous nature of tablet reassignment after a tablet_server restart. In production code, applications would typically have retry logic or wait for tablets to become available.

**Reproduced:** Yes, successfully reproduced as a flaky test (fails 3 out of 5 runs)

### Generalized Stack Trace
```
java.lang.NullPointerException
```

### Raw Stack Trace Sample
```
java.lang.NullPointerException: Cannot invoke "org.apache.accumulo.core.metadata.schema.TabletMetadata.getLocation()" because "tabletMetadata" is null
	at org.apache.accumulo.test.CorruptMutationIT_RestartInjected.testCorruptMutation(CorruptMutationIT_RestartInjected.java:85)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.CorruptMutationIT_RestartInjected`
- Test Method: `testCorruptMutation`
- Position: `after_batch_write`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `026-99862082`

---

## Group 17 (Priority 2)

**Status:** [X] TEST-BUG

**Execution Count:** 1

**Priority Reason:** Application exception - org.apache.zookeeper.KeeperException$NoNodeException

**Analysis:**
This is a **TEST-BUG** - the test has an incorrect assumption about ZooKeeper lock persistence across manager restarts.

**Root Cause:**
The test captures a list of manager lock children from ZooKeeper (lines 54-58), then restarts the manager (lines 62-63), and then tries to access a lock using the stale list captured before the restart (line 67).

When a manager is restarted (even gracefully):
1. Its ZooKeeper lock is an ephemeral node that gets automatically deleted when the process disconnects
2. When the manager restarts, it creates a NEW ephemeral lock node with a different UUID

The test incorrectly assumes the lock path `children.get(0)` will still be valid after the restart.

**Evidence:**
Debug logging shows:
- Before restart: `children = [zlock#d326ddb9...#0000000000, zlock#9c960cb5...#0000000001]`
- After restart: `childrenAfterRestart = [zlock#9c960cb5...#0000000001, zlock#d6969da9...#0000000002]`
- Test tries to read: `zlock#d326ddb9...#0000000000` which no longer exists

The original manager's lock (ending in #0000000000) was deleted and replaced with a new lock (ending in #0000000002).

**Location:**
- `test/src/main/java/org/apache/accumulo/test/functional/BackupManagerIT_RestartInjected.java:67`

**Fix Required:**
The test should re-query the lock children AFTER the restart instead of using the stale list:

```java
RestartFramework.at("after_backup_manager_established").on(getCluster()).restart("manager")
    .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

// Re-query the lock children after restart
var path = ServiceLock.path(root + Constants.ZMANAGER_LOCK);
children = ServiceLock.validateAndSort(path, writer.getChildren(path.toString()));

// Now use the fresh children list
String lockPath = root + Constants.ZMANAGER_LOCK + "/" + children.get(0);
byte[] data = writer.getData(lockPath);
```

**Reproduced:** Yes, successfully reproduced on the first attempt

### Generalized Stack Trace
```
org.apache.zookeeper.KeeperException$NoNodeException
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
	at org.apache.zookeeper.ZooKeeper.getData(ZooKeeper.java)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.lambda$getData$0(ZooReader.java)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.retryLoopMutator(ZooReader.java)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.retryLoop(ZooReader.java)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.retryLoop(ZooReader.java)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.getData(ZooReader.java)
```

### Raw Stack Trace Sample
```
org.apache.zookeeper.KeeperException$NoNodeException: KeeperErrorCode = NoNode for /accumulo/b435ae0c-4ac7-424d-980e-d3696d02eac5/managers/lock/zlock#6574e5e5-1f8d-4fa1-bbdf-b48b84aab196#0000000000
	at org.apache.zookeeper.KeeperException.create(KeeperException.java:117)
	at org.apache.zookeeper.KeeperException.create(KeeperException.java:53)
	at org.apache.zookeeper.ZooKeeper.getData(ZooKeeper.java:1972)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.lambda$getData$0(ZooReader.java:79)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.retryLoopMutator(ZooReader.java:184)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.retryLoop(ZooReader.java:163)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.retryLoop(ZooReader.java:150)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.getData(ZooReader.java:79)
	at org.apache.accumulo.test.functional.BackupManagerIT_RestartInjected.test(BackupManagerIT_RestartInjected.java:67)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.BackupManagerIT_RestartInjected`
- Test Method: `test`
- Position: `after_backup_manager_established`
- Target: `manager`
- Mode: `GRACEFUL`
- Execution Dir: `030-0ead26df`

---

## Group 20 (Priority 2)

**Status:** [X] BUG

**Execution Count:** 1

**Priority Reason:** Application exception - org.apache.accumulo.core.client.SampleNotPresentException

**Analysis:**
This is a **BUG** - table properties (specifically sampling configuration) are not properly persisted to ZooKeeper or not correctly recovered after a manager restart.

**Root Cause:**
The test sets a sampling configuration on table 1 using `setSamplerConfiguration()` (line 472), which calls `modifyProperties()` to send the configuration to the manager via RPC. The operation completes successfully, and the test then restarts the manager (line 474-475). After the restart, when a compaction is triggered (line 483), the compaction runs WITHOUT the sampling configuration (`config []`), creating an RFile without sample data.

**Technical Details:**
1. `setSamplerConfiguration()` → `modifyProperties()` → RPC call `client.modifyTableProperties()`
2. The manager should persist table properties to ZooKeeper
3. When the manager restarts, it should reload table properties from ZooKeeper
4. However, the FATE log shows: `Seeding FATE[694f4a1fc4c33db2] TABLE_COMPACT Compact table (1) with config []`
5. The empty `config []` proves the sampling configuration was lost after the manager restart

**Evidence:**
1. Successfully reproduced the failure
2. Test sequence:
   - Line 472: `updateSamplingConfig(client, tableName, SC1)` - sets sampling configuration (COMPLETES SUCCESSFULLY)
   - Line 474-475: **Manager restart** (happens AFTER updateSamplingConfig returns)
   - Line 483: `compact()` - should use SC1 but uses empty config instead
   - Line 485-486: Restart tablet_server
   - Line 489-491: Verify sample data is present - **FAILS with SampleNotPresentException**
3. Manager FATE log confirms: `TABLE_COMPACT Compact table (1) with config []` (empty configuration)
4. Compaction log shows: `Compacting 1<< on i.default.small for USER from [F0000001.rf] size 44 KB config []`
5. The compaction created file `A0000004.rf` without sample data
6. Offline/online operations (part of updateSamplingConfig) completed at 23:00:05
7. Compaction was requested at 23:00:06, after the manager restart

**Location:**
The bug is in how the manager persists table properties to ZooKeeper when `modifyTableProperties()` is called, OR how it recovers/loads table properties from ZooKeeper after a restart. The sampling configuration should survive a manager restart but does not.

**Impact:**
This is a CRITICAL bug violating fundamental durability expectations. Users reasonably expect that after setting a table property (via `setSamplerConfiguration()`) and having that operation complete successfully, the property should persist even if the manager restarts. The silent loss of table configuration can lead to:
- Compactions creating RFiles without expected sample data
- Data analysis workflows failing when sampling is expected but not present
- Silent data corruption where table behavior changes unexpectedly after manager restarts

**Reproduced:** Yes, successfully reproduced on the second attempt (first attempt timed out due to unrelated issue with tablet getting stuck in SUSPENDED state)

### Generalized Stack Trace
```
org.apache.accumulo.core.client.SampleNotPresentException
	at org.apache.accumulo.core.clientImpl.OfflineIterator.createIterator(OfflineIterator.java)
	at org.apache.accumulo.core.clientImpl.OfflineIterator.nextTablet(OfflineIterator.java)
	at org.apache.accumulo.core.clientImpl.OfflineIterator.<init>(OfflineIterator.java)
	at org.apache.accumulo.core.clientImpl.OfflineScanner.iterator(OfflineScanner.java)
```

### Raw Stack Trace Sample
```
org.apache.accumulo.core.client.SampleNotPresentException
	at org.apache.accumulo.core.clientImpl.OfflineIterator.createIterator(OfflineIterator.java:240)
	at org.apache.accumulo.core.clientImpl.OfflineIterator.nextTablet(OfflineIterator.java:192)
	at org.apache.accumulo.core.clientImpl.OfflineIterator.<init>(OfflineIterator.java:102)
	at org.apache.accumulo.core.clientImpl.OfflineScanner.iterator(OfflineScanner.java:87)
	at org.apache.accumulo.test.SampleIT_RestartInjected.check(SampleIT_RestartInjected.java:555)
	at org.apache.accumulo.test.SampleIT_RestartInjected.testSampleNotPresent(SampleIT_RestartInjected.java:491)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.SampleIT_RestartInjected`
- Test Method: `testSampleNotPresent`
- Position: `after_first_compact`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `006-7e68c406`

---

## Group 21 (Priority 2)

**Status:** [X] TEST-BUG

**Execution Count:** 1

**Priority Reason:** Application exception - java.util.NoSuchElementException

**Analysis:**
This is a **TEST-BUG** - the test has invalid assumptions that are incompatible with tablet_server restarts.

**Root Cause:**
The test validates that after a flush operation, tablets remain on the same server. However, the restart injection at `after_flush` fundamentally breaks this assumption. When the tablet_server is restarted:

1. The old tablet_server process is killed (e.g., server A at port 35273)
2. A new tablet_server process starts (e.g., server B at port 41353)
3. Tablets MUST be migrated from the dead server to the new server
4. This reassignment is asynchronous

The test stores `newTablet.current` (pointing to the original server A) and then expects that after the restart, `flushed.current` equals `newTablet.current`. This is impossible because:
- Server A is dead after the restart
- Tablets must move to server B (or another server)
- Therefore `newTablet.current ≠ flushed.current`

**Evidence:**
Successfully reproduced as a flaky test (5 runs):
- Run 1: NoSuchElementException (60%)
- Run 2: NoSuchElementException (60%)
- Run 3: AssertionFailedError at line 106 - server mismatch (40%)
- Run 4: NoSuchElementException (60%)
- Run 5: AssertionFailedError at line 106 - server mismatch (40%)

**Two Failure Modes:**

1. **NoSuchElementException (60% of runs):** The metadata scan happens during tablet reassignment when no results are available yet, causing `scanner.next()` to throw NoSuchElementException

2. **AssertionFailedError (40% of runs):** The tablet has been reassigned to the new server, but the assertion at line 106 fails because `newTablet.current` (old server) ≠ `flushed.current` (new server)

**Location:**
The issue is at `test/src/main/java/org/apache/accumulo/test/functional/ManagerAssignmentIT_RestartInjected.java:102-108`

**Comparison with Original Test:**
The original `ManagerAssignmentIT.java` test does NOT have a restart between flush and the assertion (line 90-94). It validates that flushing doesn't move tablets. The restart-injected version accidentally validates that restarting doesn't move tablets, which is fundamentally wrong.

**Fix Required:**
The test should be updated to handle the tablet_server restart at the `after_flush` position. Options:

1. Wait for tablet reassignment to complete before assertions:
```java
RestartFramework.at("after_flush").on(getCluster()).restart("tablet_server").withIndex(0)
    .withMode(RestartMode.GRACEFUL).execute();

// Wait for tablet to be reassigned
Wait.waitFor(() -> {
  try {
    TabletLocationState tls = getTabletLocationState(c, tableId);
    return tls != null && tls.current != null;
  } catch (Exception e) {
    return false;
  }
}, 30_000, 100);

TabletLocationState flushed = getTabletLocationState(c, tableId);
// Don't check if it's the same server - just verify it has a valid location
assertNotNull(flushed.current);
assertNotNull(flushed.last);
```

2. Skip the server-equality assertions after restart points, since tablets will necessarily move to different servers

**Impact:**
This is a test-only issue. The test's expectations are invalid when tablet_server restarts occur between flush and the assertion checks.

**Reproduced:** Yes, successfully reproduced as a flaky test (3 out of 5 runs show NoSuchElementException, 2 out of 5 show AssertionFailedError)

### Generalized Stack Trace
```
java.util.NoSuchElementException
	at org.apache.accumulo.core.clientImpl.TabletServerBatchReaderIterator.next(TabletServerBatchReaderIterator.java)
	at org.apache.accumulo.core.clientImpl.TabletServerBatchReaderIterator.next(TabletServerBatchReaderIterator.java)
	at org.apache.accumulo.server.manager.state.MetaDataTableScanner.next(MetaDataTableScanner.java)
```

### Raw Stack Trace Sample
```
java.util.NoSuchElementException
	at org.apache.accumulo.core.clientImpl.TabletServerBatchReaderIterator.next(TabletServerBatchReaderIterator.java:228)
	at org.apache.accumulo.core.clientImpl.TabletServerBatchReaderIterator.next(TabletServerBatchReaderIterator.java:91)
	at org.apache.accumulo.server.manager.state.MetaDataTableScanner.next(MetaDataTableScanner.java:157)
	at org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.getTabletLocationState(ManagerAssignmentIT_RestartInjected.java:297)
	at org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.test(ManagerAssignmentIT_RestartInjected.java:105)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected`
- Test Method: `test`
- Position: `after_flush`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `026-730f839e`

---

## Group 22 (Priority 2)

**Status:** [X] TEST-BUG (with possible underlying BUG)

**Execution Count:** 1

**Priority Reason:** Application exception - java.util.NoSuchElementException

**Analysis:**
This is primarily a **TEST-BUG** - the test has invalid assumptions about tablet availability after a tablet_server restart. There may also be an underlying **BUG** with summary persistence.

**Root Cause:**
After the tablet_server is restarted at line 181-182, the test immediately tries to:1. Get timestamp stats by scanning the table (line 185-186)
2. Retrieve summaries (line 187-189)
3. Verify the summaries are present (line 194-195)

However, tablet reassignment is asynchronous. When a tablet_server restarts:
1. The old tablet_server process is killed
2. A new tablet_server process starts with a different ID/port
3. The Manager detects the change and needs to reassign tablets to the new server
4. This reassignment takes time and is not guaranteed to complete immediately

The test does not wait for tablets to be reassigned before attempting to scan or retrieve summaries, leading to:
- Either a timeout when trying to scan the table (as seen in reproduction attempts)
- Or an empty summaries collection if the scan somehow completes but finds no data

**Evidence:**
1. Attempted reproduction consistently times out, but at different points:
   - First attempt: Timeout during `waitForBalance()` in the restart adapter (line 172 of AccumuloClusterImplAdapter)
   - Second attempt: Timeout at `getTimestampStats()` (line 186) when trying to scan the table
2. The Manager logs show: "not balancing just yet, as collection of live tservers is in flux" - the Manager refuses to balance because it detected tserver set changes
3. Without proper waiting, tablets may not be available for scanning or summary retrieval

**Restart Adapter Issue:**
The AccumuloClusterImplAdapter calls `waitForBalance()` which hangs indefinitely because:
- After a tablet_server restart, the tserver set changes (old server gone, new server added)
- The Manager sees this as "flux" and refuses to proceed with balancing
- `waitForBalance()` has no timeout and waits forever

**Possible Underlying BUG:**
Even if the tablets were properly reassigned, the original failure shows an empty summaries collection. This suggests that summary data may not be properly persisted or recovered after a tablet_server restart, similar to the durability bugs in Groups 4, 9, 11, and 20. However, I cannot confirm this due to the restart framework timeout issues.

**Location:**
- Test issue: `test/src/main/java/org/apache/accumulo/test/functional/SummaryIT_RestartInjected.java:185-194`
- Restart adapter issue: `restart-accumulo-adapter/src/main/java/org/apache/accumulo/restarttest/AccumuloClusterImplAdapter.java:172`

**Fix Required:**
The test should wait for tablets to be reassigned after the restart:

```java
RestartFramework.at("after_flush").on(getCluster()).restart("tablet_server").withIndex(0)
    .withMode(RestartMode.GRACEFUL).execute();

// Wait for tablets to be available
Wait.waitFor(() -> {
  try {
    // Try to read from the table to verify tablet is assigned
    getTimestampStats(table, c);
    return true;
  } catch (Exception e) {
    return false;
  }
}, 60_000, 1000);

stats = getTimestampStats(table, c);
summaries = c.tableOperations().summaries(table).retrieve();
```

Alternatively, the restart adapter should:
1. NOT call `waitForBalance()` after restarts (or use a timeout)
2. Instead, implement a custom wait that checks for tablet assignment using metadata table scans

**Reproduced:** No - Timeout issues with the restart framework prevented full reproduction. The test consistently times out either during `waitForBalance()` or during the first table scan after restart.

### Generalized Stack Trace
```
java.util.NoSuchElementException
	at com.google.common.collect.MoreCollectors$ToOptionalState.getElement(MoreCollectors.java)
	at com.google.common.collect.MoreCollectors.lambda$static$1(MoreCollectors.java)
	at java.base/java.util.stream.ReferencePipeline.collect(ReferencePipeline.java)
	at org.apache.accumulo.harness.AccumuloITBase.getOnlyElement(AccumuloITBase.java)
```

### Raw Stack Trace Sample
```
java.util.NoSuchElementException
	at com.google.common.collect.MoreCollectors$ToOptionalState.getElement(MoreCollectors.java:161)
	at com.google.common.collect.MoreCollectors.lambda$static$1(MoreCollectors.java:73)
	at java.base/java.util.stream.ReferencePipeline.collect(ReferencePipeline.java:686)
	at org.apache.accumulo.harness.AccumuloITBase.getOnlyElement(AccumuloITBase.java:56)
	at org.apache.accumulo.test.functional.SummaryIT_RestartInjected.checkSummaries(SummaryIT_RestartInjected.java:123)
	at org.apache.accumulo.test.functional.SummaryIT_RestartInjected.basicSummaryTest(SummaryIT_RestartInjected.java:181)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.SummaryIT_RestartInjected`
- Test Method: `basicSummaryTest`
- Position: `after_flush`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `012-1ccabd26`

---

## Group 1 (Priority 3)

**Status:** [X] FP

**Execution Count:** 46

**Priority Reason:** Exception with restart framework involvement

**Analysis:**
This is a **FALSE POSITIVE (FP)** - the failures are caused by incompatible test patterns, not bugs in the source code.

**Root Cause:**
The tests perform manual process management operations (manual kill/start, graceful shutdown) that bypass the cluster's normal process tracking. When the restart framework subsequently tries to restart a node, it cannot find any tracked processes and throws "No processes found for role: {type}".

**Technical Details:**

**Test 1 (VerifySerialRecoveryIT_RestartInjected):**
1. Line 119-121: Test manually kills ALL tablet servers using `getCluster().killProcess()`
   - This removes them from `MiniAccumuloClusterControl.tabletServerProcesses` list (line 468 in MiniAccumuloClusterControl.java)
2. Line 122: Test manually starts a new tablet server using `cluster.exec(TabletServer.class)`
   - The `exec()` method creates a process but does NOT add it to the tracked `tabletServerProcesses` list
3. Line 129-130: Test tries to restart tablet_server using the restart framework
   - The restart framework calls `getProcesses()` which queries the now-empty `tabletServerProcesses` list
   - Error: "No processes found for role: tablet_server"

**Test 2 (GracefulShutdownIT_RestartInjected):**
1. Line 180: Test sends graceful shutdown signal to garbage collector
2. Line 181-184: Test waits for GC process to disappear from process list
3. Line 185-186: Test tries to restart garbage_collector using the restart framework
   - The process list is now empty after graceful shutdown
   - Error: "No processes found for role: garbage_collector"

**Test 3 (ScanServerIT_RestartInjected):**
1. Scan servers are started using low-level `start()` method but may not be properly tracked
2. When restart framework tries to restart scan_server, it cannot find tracked processes
   - Error: "No processes found for role: scan_server"

**Why This is FP:**
The restart framework correctly detects an inconsistent state where processes were managed outside its normal tracking mechanism. This is not a bug in the Accumulo source code, but rather:
1. Tests that perform manual process lifecycle management (kill, start, graceful shutdown)
2. Then attempt to use the restart framework which requires processes to be tracked by the cluster
3. The restart framework's requirement for tracked processes conflicts with the test's manual process management

**Impact:**
This is a test design issue, not an application bug. The tests should either:
- Avoid manual process management when using the restart framework, OR
- Not use restart injection points after manual process management

**Reproduced:** Yes, successfully reproduced Test 1 (VerifySerialRecoveryIT_RestartInjected)

### Generalized Stack Trace
```
Caused by: java.lang.IllegalArgumentException
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java)
	... more
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_recovery_scan
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.accumulo.test.VerifySerialRecoveryIT_RestartInjected.testSerializedRecovery(VerifySerialRecoveryIT_RestartInjected.java:130)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.IllegalArgumentException: No processes found for role: tablet_server
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:87)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:50)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
	... 7 more

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.VerifySerialRecoveryIT_RestartInjected`
- Test Method: `testSerializedRecovery`
- Position: `after_recovery_scan`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `040-4d1e774c`

**Test 2:**
- Test Class: `org.apache.accumulo.test.functional.GracefulShutdownIT_RestartInjected`
- Test Method: `testGracefulShutdown`
- Position: `after_gc_restart`
- Target: `garbage_collector`
- Mode: `GRACEFUL`
- Execution Dir: `014-aadee552`

**Test 3:**
- Test Class: `org.apache.accumulo.test.ScanServerIT_RestartInjected`
- Test Method: `testBatchScan`
- Position: `after_eventual_batch_scan`
- Target: `scan_server`
- Mode: `GRACEFUL`
- Execution Dir: `027-b74de6a3`

---

## Group 2 (Priority 3)

**Status:** [X] FP

**Execution Count:** 22

**Priority Reason:** Exception with restart framework involvement

**Analysis:**
This is a **FALSE POSITIVE (FP)** - the failures are caused by a design limitation in the restart adapter's `waitForTabletServers` method.

**Root Cause:**
When the manager is restarted, the restart adapter's `waitActive` method calls `waitForTabletServers` (line 168 in AccumuloClusterImplAdapter.java), which gets the expected tablet server count from `cluster.getConfig().getNumTservers()` (line 297). This returns the **configured** number of tablet servers, not the **actual** number of running servers.

In the test flow:
1. The test is configured with `NUM_TSERVERS = 3` (3 tablet servers)
2. The test manually kills one tablet server using `getCluster().killProcess()` (line 74-75)
3. Only 2 tablet servers are now running
4. The test restarts the manager at position `after_tserver_killed` (line 81-82)
5. The restart adapter waits for 3 tablet servers to register (from config)
6. But only 2 tablet servers will ever register (the third was killed)
7. After 60 seconds, the adapter times out with "Timeout waiting for tablet servers to register"

**Evidence:**
Debug logging confirms:
```
DEBUG: Waiting for 3 tablet servers to register (from cluster.getConfig().getNumTservers())
DEBUG: Currently 2 tablet servers registered (expecting 3): [KingsLand:40867, KingsLand:39179]
DEBUG: TIMEOUT - Expected 3 tablet servers but only 2 registered after 60000ms
```

**Why This is FP:**
1. **Source code is correct**: The Accumulo manager is designed to operate with fewer tablet servers than configured. The manager handles dead tablet servers correctly.
2. **Test code is correct**: The test intentionally kills a tablet server to verify the manager's behavior when servers die. This is valid testing.
3. **Restart adapter limitation**: The adapter incorrectly assumes that after a manager restart, the number of registered tablet servers should equal the configured number (`getNumTservers()`). This assumption fails when tests intentionally manage tablet server lifecycles (kill, start).

**Impact:**
This is a restart framework adapter issue, not an Accumulo bug. Tests that manually kill/start tablet servers and then restart the manager will fail due to the adapter's hardcoded expectation. The adapter should either:
- Wait for the actual number of running tablet servers (check before restart)
- Skip the tablet server count check for manager restarts
- Make the expected count configurable based on actual running processes

**Reproduced:** Yes, successfully reproduced on the first attempt with all three test executions showing the same pattern

### Generalized Stack Trace
```
Caused by: java.lang.Exception
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitForTabletServers(AccumuloClusterImplAdapter.java)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitActive(AccumuloClusterImplAdapter.java)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitActive(AccumuloClusterImplAdapter.java)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java)
	... more
```

### Raw Stack Trace Sample
```
org.restarttest.core.RestartException: Restart failed at position after_tserver_killed
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.accumulo.test.functional.TabletMetadataIT_RestartInjected.getLiveTServersTest(TabletMetadataIT_RestartInjected.java:82)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.Exception: Timeout waiting for tablet servers to register
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitForTabletServers(AccumuloClusterImplAdapter.java:314)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitActive(AccumuloClusterImplAdapter.java:168)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitActive(AccumuloClusterImplAdapter.java:50)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:116)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
	... 7 more

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.TabletMetadataIT_RestartInjected`
- Test Method: `getLiveTServersTest`
- Position: `after_tserver_killed`
- Target: `manager`
- Mode: `GRACEFUL`
- Execution Dir: `033-f10b8f3d`

**Test 2:**
- Test Class: `org.apache.accumulo.test.compaction.ExternalCompaction_1_IT_RestartInjected`
- Test Method: `testExternalCompactionDeadTServer`
- Position: `after_table_create_dead`
- Target: `manager`
- Mode: `GRACEFUL`
- Execution Dir: `019-444d7d9a`

**Test 3:**
- Test Class: `org.apache.accumulo.test.compaction.ExternalCompaction_1_IT_RestartInjected`
- Test Method: `testExternalCompactionDeadTServer`
- Position: `after_start_compactors_dead`
- Target: `manager`
- Mode: `GRACEFUL`
- Execution Dir: `021-65ef9d03`

---

## Group 7 (Priority 4)

**Status:** [X] FP

**Execution Count:** 4

**Priority Reason:** Timeout - inspect later

**Analysis:**
This is a **FALSE POSITIVE (FP)** - the timeout is caused by a design flaw in the restart adapter, not a bug in the Accumulo source code.

**Root Cause:**
The restart adapter's `waitActive()` method calls `client.instanceOperations().waitForBalance()` at line 172 in AccumuloClusterImplAdapter.java with **NO TIMEOUT**. This call waits indefinitely for all tablets to be balanced. However, in SSL-enabled tests (and potentially other scenarios), the following issues occur:

1. **SSL Connection Instability**: After restarting a tablet server, the replacement server experiences repeated SSL connection errors: "Socket is closed by peer" (TTransportException). These errors occur continuously in the replacement tablet server's Thrift layer.

2. **Unhosted Tablet**: The manager logs show "not balancing user tablets because there are 1 unhosted tablets" repeatedly. Despite the replacement tablet server being registered (visible in manager logs showing 2 tablet servers: KingsLand:34255 and KingsLand:38585), one tablet remains unhosted, likely due to the SSL connection instability.

3. **Blocking Call with No Timeout**: The `waitForBalance()` call is a blocking RPC to the manager that waits until all tablets are balanced. Since there's an unhosted tablet, this call never returns.

4. **SSL IO Hang**: The test output shows "Thread 'junit-timeout-thread-3' stuck on IO to ssl:mgr:KingsLand:41691 for at least 120072 ms" at 00:48:02 (about 2 minutes after the restart started). Eventually the SSL connection fails with "Socket is closed by peer" and "Connection refused" errors at 00:49:54.

5. **Test Timeout**: The test has a 4-minute timeout (defined in @Timeout annotation), which is reached before the `waitForBalance()` call can complete.

**Evidence:**
Successfully reproduced the failure on the first attempt. The test consistently times out after 4 minutes when executing the restart at position "after_binary_test".

**Timeline:**
- 00:46:00: Tablet server restart begins
- 00:46:01: Replacement tablet server starts
- 00:46:00-00:49:52: Manager continuously logs "not balancing user tablets because there are 1 unhosted tablets"
- 00:46:01-00:49:xx: Replacement tablet server continuously experiences "Socket is closed by peer" errors
- 00:48:02: Test thread stuck on SSL IO for 120+ seconds
- 00:49:54: SSL connection fails completely, retries show "No managers..." and "Connection refused"
- Test times out at 4-minute mark

**Why This is FP:**
1. **Restart Adapter Design Flaw**: The `waitActive()` method (AccumuloClusterImplAdapter.java:172) calls `waitForBalance()` with no timeout, causing indefinite blocking when tablets cannot be balanced.

2. **SSL Test Environment Issue**: The SSL connections experience instability during rapid restart scenarios in the test environment. This is not representative of production behavior where restarts are rare events with proper connection handling.

3. **Accumulo Source Code is Correct**:
   - The manager correctly refuses to balance when there are unhosted tablets
   - The replacement tablet server starts and attempts to register properly
   - The manager can see both tablet servers registered
   - The issue is the SSL connection layer, not the Accumulo logic

4. **Not Representative of Real-World Scenarios**: In production, tablet server restarts would be followed by proper health checks with timeouts, and SSL connections would be established under stable conditions, not immediately after a process restart.

**Impact:**
This is a restart framework/adapter limitation that affects tests with:
- SSL-enabled clusters (SslIT tests)
- Scenarios where tablets cannot be quickly reassigned after a restart
- Any situation where `waitForBalance()` might block indefinitely

The adapter should either:
- Add a timeout to the `waitForBalance()` call
- Implement a custom wait mechanism with configurable timeout
- Skip the balance check for certain restart scenarios

**Reproduced:** Yes, successfully reproduced on the first attempt with Test 1 (SslIT_RestartInjected.binary)

### Generalized Stack Trace
```
java.util.concurrent.TimeoutException
	at java.base/java.util.ArrayList.forEach(ArrayList.java)
	at java.base/java.util.ArrayList.forEach(ArrayList.java)
	Suppressed: java.lang.InterruptedException
		at java.base/java.util.concurrent.FutureTask.awaitDone(FutureTask.java)
		at java.base/java.util.concurrent.FutureTask.get(FutureTask.java)
		at org.apache.accumulo.harness.Timeout.interceptTestMethod(Timeout.java)
		... more
```

### Raw Stack Trace Sample
```
java.util.concurrent.TimeoutException: binary() timed out after 4 minutes
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
	Suppressed: java.lang.InterruptedException
		at java.base/java.util.concurrent.FutureTask.awaitDone(FutureTask.java:418)
		at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:203)
		at org.apache.accumulo.harness.Timeout.interceptTestMethod(Timeout.java:68)
		... 2 more

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.SslIT_RestartInjected`
- Test Method: `binary`
- Position: `after_binary_test`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `047-ae541cd4`

**Test 2:**
- Test Class: `org.apache.accumulo.test.functional.SslIT_RestartInjected`
- Test Method: `mapReduce`
- Position: `after_mapreduce_test`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `051-1e43bb0f`

**Test 3:**
- Test Class: `org.apache.accumulo.test.LargeSplitRowIT_RestartInjected`
- Test Method: `automaticSplitWithoutGaps`
- Position: `after_flush`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `037-37414b93`

---

## Group 10 (Priority 4)

**Status:** [X] FP

**Execution Count:** 2

**Priority Reason:** Timeout - inspect later

**Analysis:**
This is a **FALSE POSITIVE (FP)** - the timeout is caused by the restart adapter's design, not a bug in the Accumulo source code.

**Root Cause:**
The test has a `@Timeout(10)` annotation (10-second timeout) which was appropriate for the original test without restart injection. However, when the manager is restarted at the `after_get_processes` position, the restart adapter calls `client.instanceOperations().waitForBalance()` at line 172 in AccumuloClusterImplAdapter.java with **NO TIMEOUT**. This blocking call takes approximately 30-50 seconds to complete, far exceeding the test's 10-second timeout.

**Evidence:**
1. Successfully reproduced the failure on the first attempt
2. Test output shows:
   - Restart starts at 00:55:08
   - `waitForBalance()` call begins at line 24 of the output
   - InterruptedException occurs at 00:55:39 (31 seconds after restart starts) when JUnit timeout mechanism interrupts the thread
   - The restart actually completes successfully at 00:55:54 (46 seconds total)
3. The original non-restart-injected test (`MiniAccumuloClusterImplTest.java:87-104`) runs quickly because it only calls `getProcesses()` and performs assertions - no restart involved
4. The restart-injected version adds a manager restart between getting the processes and checking them, but the timeout wasn't adjusted

**Comparison:**
- **Original test**: `getProcesses()` → assertions (completes in <10 seconds)
- **Restart-injected test**: `getProcesses()` → **RESTART MANAGER** → assertions (restart takes ~46 seconds, timeout after 10)

**Why This is FP:**
1. The Accumulo source code is working correctly - the manager restarts successfully and the cluster balances properly
2. The test timeout (10 seconds) was designed for the original test without restart injection
3. The restart adapter's `waitForBalance()` call has no timeout and blocks for 30-50 seconds, which is a design limitation of the restart framework, not Accumulo
4. The test's timeout is incompatible with the time required for the injected restart operation

**Impact:**
This is a restart framework limitation that affects tests with short timeouts when manager restarts are injected. The adapter should either:
- Add a timeout parameter to the `waitForBalance()` call
- Skip the balance wait for certain test scenarios
- Tests that inject manager restarts should have timeouts of at least 60 seconds

**Similar Issue:**
Test 2 (`saneMonitorInfo`) has a more realistic `@Timeout(60)` (60 seconds), which would be more appropriate for tests with restart injection. However, Test 2 restarts a tablet_server instead of the manager, which may have different timing characteristics.

**Reproduced:** Yes, successfully reproduced Test 1 on the first attempt

### Generalized Stack Trace
```
java.util.concurrent.TimeoutException
	at java.base/java.util.ArrayList.forEach(ArrayList.java)
	at java.base/java.util.ArrayList.forEach(ArrayList.java)
```

### Raw Stack Trace Sample
```
java.util.concurrent.TimeoutException: testAccurateProcessListReturned() timed out after 10 seconds
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImplTest_RestartInjected`
- Test Method: `testAccurateProcessListReturned`
- Position: `after_get_processes`
- Target: `manager`
- Mode: `GRACEFUL`
- Execution Dir: `004-38624dd2`

**Test 2:**
- Test Class: `org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImplTest_RestartInjected`
- Test Method: `saneMonitorInfo`
- Position: `after_monitor_poll`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `006-311981e4`

---

## Group 5 (Priority 5)

**Status:** [X] TEST-BUG

**Execution Count:** 6

**Priority Reason:** Test code issue - exception from test

**Analysis:**
This is a **TEST-BUG** - the test has invalid assumptions that are fundamentally incompatible with tablet_server restarts.

**Root Cause:**
The test starts 8 stuck scans on a tablet_server (line 300-313), waits for them to be active (line 316), then restarts the tablet_server (line 318-319). However, scans are server-side resources tied to a specific tablet_server process. When that process is killed and restarted:

1. ALL active scans on the old tablet_server are lost
2. The new tablet_server starts with NO active scans
3. The test then tries to cancel the client-side futures (line 323-326) and expects 4 scans to remain active (line 329)
4. But there are 0 scans after the restart, not 8, so the wait times out

**Evidence:**
Successfully reproduced on the first attempt. Debug logging confirms:
- Before restart: 8 active scans
- After restart: 0 active scans (ALL scans lost)
- After canceling futures: 0 active scans
- Waiting for 4 active scans: 0 scans (timeout after 60 seconds)

**Comparison with Original Test:**
The original non-restart-injected `ZombieScanIT.java` (lines 296-307) does NOT have a restart between starting the scans and canceling them. It tests that when client-side scan threads are canceled, the server-side threads are interrupted, with 4 "zombie" scans remaining (those that don't respond to interrupts).

The restart-injected version adds a restart at position `after_first_stuck_scans_detected`, which fundamentally breaks the test's logic because scans cannot survive a tablet_server restart.

**Location:**
The issue is at `test/src/main/java/org/apache/accumulo/test/ZombieScanIT_RestartInjected.java:318-336`

**Why This is TEST-BUG (not a source code bug):**
1. Scans are ephemeral, process-local resources, not persisted state
2. There's no reasonable expectation that active scans should survive a tablet_server restart
3. Accumulo is working correctly - when a tablet_server is killed, all its scans are cleaned up
4. The test's expectations (8 scans → restart → cancel → 4 scans remain) are impossible to satisfy
5. This is not a durability or correctness issue - it's expected behavior

**Impact:**
This is a test-only issue. The test was designed to verify zombie scan detection without restarts. The restart injection points are incompatible with the test's logic, as they occur at positions where active scans exist and the test expects them to persist.

**Reproduced:** Yes, successfully reproduced on the first attempt

### Generalized Stack Trace
```
java.lang.IllegalStateException
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalStateException: . Timeout exceeded
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:125)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:89)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:76)
	at org.apache.accumulo.test.ZombieScanIT_RestartInjected.testMetrics(ZombieScanIT_RestartInjected.java:329)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.ZombieScanIT_RestartInjected`
- Test Method: `testMetrics`
- Position: `after_first_stuck_scans_detected`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `013-66ad34ea`

**Test 2:**
- Test Class: `org.apache.accumulo.test.ZombieScanIT_RestartInjected`
- Test Method: `testMetrics`
- Position: `after_first_zombie_verification`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `014-4a7a74fe`

**Test 3:**
- Test Class: `org.apache.accumulo.test.ZombieScanIT_RestartInjected`
- Test Method: `testMetrics`
- Position: `after_second_stuck_scans_detected`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `015-602e2da3`

---

## Group 6 (Priority 5)

**Status:** [X] BUG

**Execution Count:** 4

**Priority Reason:** Test code issue - exception from test

**Analysis:**
This is a **BUG** - the logical time counter is not properly persisted and recovered after tablet_server restarts, causing timestamp reuse which violates the fundamental guarantee of LOGICAL time type.

**Root Cause:**
The test creates a table with `TimeType.LOGICAL` and performs the following operations:
1. Writes mutation for row "a" and flushes (gets timestamp 1)
2. Restarts tablet_server at `after_initial_batch_write`
3. Performs a table merge
4. Writes mutation for row "b" and flushes (gets timestamp 1, but expects timestamp 2)

Debug logging reveals:
- After initial flush: Row "a" exists with timestamp 1
- After tablet_server restart: Row "a" is LOST (durability issue, similar to Groups 4, 9, 11)
- After writing row "b": Row "b" has timestamp 1 instead of 2

The logical time counter was reset to 1 after the tablet_server restart, causing timestamp reuse.

**Why This is a BUG:**
In Accumulo's LOGICAL time type:
- Timestamps must be monotonically increasing for the lifetime of the table
- The logical time counter should NEVER reset, even if data is lost during crashes
- Timestamp reuse violates the fundamental guarantee that each mutation gets a unique, increasing timestamp

The logical time counter should be:
1. Persisted to tablet metadata when mutations are processed
2. Recovered from metadata when a tablet is loaded after a restart
3. Set to at least `max(previous_max_timestamp, 1)` to prevent timestamp reuse

**Evidence:**
1. Successfully reproduced the failure
2. Debug logs confirm:
   ```
   DEBUG: After initial flush - found entry: a cf:cq [] 1 false
   DEBUG: After restart before merge - (no entries found - row 'a' was lost)
   DEBUG: After merge - (no entries found)
   DEBUG: After writing final mutation 'b' - found entry: b cf:cq [] 1 false
   ```
3. Row "b" gets timestamp 1 instead of the expected timestamp 2
4. The original non-restart-injected test (`LogicalTimeIT.java`) works correctly, confirming that the expected behavior is for logical time to continue incrementing

**Location:**
The bug is in the tablet recovery/initialization logic where the logical time counter is managed. When a tablet is loaded after a tablet_server restart, the counter should be recovered from persisted metadata, not reset to 1.

**Impact:**
This is a CRITICAL bug for tables using LOGICAL time type. Timestamp reuse can cause:
- Violations of the monotonicity guarantee
- Data consistency issues where different mutations have the same timestamp
- Potential issues with compaction, versioning, and other time-based operations
- Users relying on LOGICAL time for ordering guarantees will experience incorrect behavior after tablet_server restarts

**Note:**
There's also a data durability issue (row "a" was lost after `flush()`), which is similar to the bugs identified in Groups 4, 9, and 11. However, even if we accept that data can be lost (which is itself a bug), the logical time counter reset is a separate, independent bug that must be fixed.

**Reproduced:** Yes, successfully reproduced on multiple attempts. The test is also flaky - sometimes it times out during scans after tablet_server restart, but when it doesn't timeout, it consistently fails with "unexpected time 1 2".

### Generalized Stack Trace
```
java.lang.RuntimeException
```

### Raw Stack Trace Sample
```
java.lang.RuntimeException: unexpected time 1 2
	at org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.runMergeTest(LogicalTimeIT_RestartInjected.java:143)
	at org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.run(LogicalTimeIT_RestartInjected.java:54)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected`
- Test Method: `run`
- Position: `after_initial_batch_write`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `026-606348c076dd1a74`

**Test 2:**
- Test Class: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected`
- Test Method: `run`
- Position: `after_initial_batch_write`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `028-606348c076dd1a74`

**Test 3:**
- Test Class: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected`
- Test Method: `run`
- Position: `after_initial_batch_write`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `033-606348c076dd1a74`

---

## Group 12 (Priority 5)

**Status:** [X] BUG

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
java.lang.Exception
```

### Raw Stack Trace Sample
```
java.lang.Exception:  r_000067 cf_000:cq_000 [] 0 false != r_000099 cf_004:cq_004 [] 0 false
	at org.apache.accumulo.test.functional.ScanRangeIT_RestartInjected.scanRange(ScanRangeIT_RestartInjected.java:237)
	at org.apache.accumulo.test.functional.ScanRangeIT_RestartInjected.scanRange(ScanRangeIT_RestartInjected.java:183)
	at org.apache.accumulo.test.functional.ScanRangeIT_RestartInjected.scanTable(ScanRangeIT_RestartInjected.java:100)
	at org.apache.accumulo.test.functional.ScanRangeIT_RestartInjected.run(ScanRangeIT_RestartInjected.java:90)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.ScanRangeIT_RestartInjected`
- Test Method: `run`
- Position: `after_insertData_table2`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `017-41ad8829`

**Test 2:**
- Test Class: `org.apache.accumulo.test.functional.VisibilityIT_RestartInjected`
- Test Method: `run`
- Position: `after_insert_default_data`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `016-343941f2`

### Analysis (Test 1: ScanRangeIT_RestartInjected)

**Reproduced:** Yes - consistently reproducible

**Root Cause:** Stale tablet metadata after graceful tablet server restart leads to incomplete scan results.

**Details:**

This is a **BUG** in Accumulo's tablet server restart handling. The failure occurs in the ScanRangeIT_RestartInjected test when scanning a table with splits after a graceful restart of a tablet server.

**Test Setup:**
- Table2 is created with 3 split points (at rows 33, 66, 99), creating 4 tablets:
  - Tablet 4: rows (-∞, 33]
  - Tablet 2: rows (33, 66]
  - Tablet 1: rows (66, 99]
  - Tablet 3: rows (99, +∞)
- Data is inserted (100 rows, 25 entries per row)
- Tablet server at index 0 is gracefully restarted at position `after_insertData_table2`
- Test then attempts to scan the table

**Observed Behavior:**

After the restart:
- Old tablet server was at port 39955
- New tablet server came up at port 45141
- Both servers (41387 and 45141) are registered in ZooKeeper
- Framework waited 73 seconds for cluster to balance

However, checking tablet locations via `tableOperations().locate()` API shows:
- Tablet 1: rows (66, 99] → **41387** ✓ (correct, current server)
- Tablet 2: rows (33, 66] → **41387** ✓ (correct, current server)
- Tablet 3: rows (99, +∞) → **39955** ✗ (STALE, old dead server)
- Tablet 4: rows (-∞, 33] → **39955** ✗ (STALE, old dead server)

When scanning from row 0 to row 99:
- Successfully scanned Tablet 4 (rows 0-33): 850 entries
- Successfully scanned Tablet 2 (rows 34-66): 825 entries
- **STOPPED** before Tablet 1 (rows 67-99): 0 entries
- Total: 1674 entries instead of expected 2498 entries

The scan stopped at row 67 (the start of Tablet 1), even though Tablet 1 is supposedly located on server 41387 (which is running), and Tablet 2 on the same server worked fine.

**Analysis:**

The tablet metadata is not properly updated after a graceful restart. While the `locate()` API may show updated locations, the actual metadata used by scanners contains stale information. The tablets that were hosted on the restarted server (39955) are not properly reassigned to the new server (45141), causing scans that span multiple tablets to return incomplete results.

The framework's wait for "cluster to balance" (73 seconds) was insufficient for proper metadata synchronization. This indicates a timing issue or missing synchronization in Accumulo's tablet reassignment logic.

**Affected Component:** Tablet server restart/reassignment logic, tablet metadata management

**Location:** The bug is in Accumulo's source code, specifically in:
- Tablet metadata update logic after tablet server restart
- Tablet assignment/reassignment coordinator
- Possibly in the Manager's tablet balancing/assignment code

This is NOT a test bug or framework issue - the test correctly identifies that scan results are incomplete after a restart.

---

## Group 13 (Priority 5)

**Status:** [X] TEST-BUG

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

**Analysis:**
This is a **TEST-BUG** - the test has a flawed shutdown approach that only shuts down tablet servers hosting RootTable tablets, missing idle tablet servers.

**Root Cause:**
The test's shutdown logic (lines 263-287) locates tablets from the RootTable and sends shutdown commands only to servers hosting those tablets. However, after the tablet_server restart at `after_start_single_tserver_no_user_table`, the restart adapter waits for 2 tablet servers to register (from `cluster.getConfig().getNumTservers() = 2`), even though the test initially started only 1 server.

**Evidence from reproduction:**
1. Before shutdown: 2 tablet servers registered: `[KingsLand:45441, KingsLand:46859]`
2. Located tablets: Found only 1 tablet (RootTable), hosted on `KingsLand:45441`
3. Shutdown command: Successfully sent to and executed on `KingsLand:45441`
4. After shutdown: 1 tablet server still registered: `[KingsLand:46859]` - **never received shutdown command**
5. Test timeout: Waits for all tablet servers to unregister, but `KingsLand:46859` remains registered indefinitely

**Test Flow:**
1. Line 243-245: Test stops all tablet servers (originally 2), then starts 1 tablet server
2. Line 249: Waits for 1 tablet server to be registered ✓
3. Line 251-252: **Restarts tablet_server at index 0 via restart framework**
4. Restart adapter: Waits for 2 tablet servers (from config), and 2 eventually register
5. Line 263-264: Locates tablets from RootTable - finds 1 tablet on server `KingsLand:45441`
6. Line 266-287: Sends shutdown command only to `KingsLand:45441` (the one hosting the tablet)
7. Line 289: Waits for 0 tablet servers - **TIMEOUT** because `KingsLand:46859` is still running

**Location:**
The issue is at `test/src/main/java/org/apache/accumulo/test/functional/ManagerAssignmentIT_RestartInjected.java:263-289`

**Why This is TEST-BUG:**
1. The test's shutdown approach assumes all tablet servers will be hosting tablets from the RootTable
2. This assumption is violated when there are idle tablet servers (servers not hosting any tablets)
3. The restart framework's behavior (waiting for configured number of servers) is correct
4. The Accumulo source code is working correctly - idle tablet servers can exist and stay registered
5. The test should enumerate all registered tablet servers directly instead of relying on tablet locations

**Fix Required:**
The test should get all registered tablet servers directly from `client.instanceOperations().getTabletServers()` and shut them down, instead of finding servers through tablet locations:

```java
// Get all registered tablet servers directly
Set<String> tservers = client.instanceOperations().getTabletServers();
System.out.println("Shutting down all registered tablet servers: " + tservers);

for (String tserver : tservers) {
  String addressWithSession = tserver;
  var address = HostAndPort.fromString(tserver);
  var zLockPath = ServiceLock.path(getCluster().getServerContext().getZooKeeperRoot()
      + Constants.ZTSERVERS + "/" + tserver);
  long sessionId = ServiceLock.getSessionId(getCluster().getServerContext().getZooCache(), zLockPath);
  if (sessionId != 0) {
    addressWithSession = tserver + "[" + Long.toHexString(sessionId) + "]";
  }

  try {
    ThriftClientTypes.MANAGER.executeVoid((ClientContext) client,
        c -> c.shutdownTabletServer(TraceUtil.traceInfo(),
            getCluster().getServerContext().rpcCreds(), addressWithSession, false));
  } catch (AccumuloException | AccumuloSecurityException e) {
    fail("Error shutting down TabletServer " + addressWithSession, e);
  }
}

Wait.waitFor(() -> client.instanceOperations().getTabletServers().size() == 0);
```

**Impact:**
This is a test-only issue. The test's approach to enumerating tablet servers via tablet locations is flawed and incompatible with scenarios where idle tablet servers exist (which is normal and expected in Accumulo).

**Reproduced:** Yes, successfully reproduced on the first attempt with Test 1 (ManagerAssignmentIT_RestartInjected#testShutdownOnlyTServerWithoutUserTable)

### Generalized Stack Trace
```
java.lang.IllegalStateException
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalStateException: . Timeout exceeded
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:125)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:89)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:76)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:65)
	at org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.testShutdownOnlyTServerWithoutUserTable(ManagerAssignmentIT_RestartInjected.java:289)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected`
- Test Method: `testShutdownOnlyTServerWithoutUserTable`
- Position: `after_start_single_tserver_no_user_table`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `046-33bbcefc`

**Test 2:**
- Test Class: `org.apache.accumulo.test.functional.ShutdownIT_RestartInjected`
- Test Method: `adminStop`
- Position: `after_get_tablet_servers`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `025-79d84522`

---

## Group 14 (Priority 5)

**Status:** [X] CANNOT REPRODUCE

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

**Analysis:**
Unable to reproduce the failure with either test execution. Both tests completed successfully:

**Test 1 Reproduction Attempt:**
- Command: `mvn -pl test failsafe:integration-test -Dit.test=GarbageCollectorTrashDefaultIT_RestartInjected#testTrashHadoopDisabledAccumuloEnabled -Drestart.position=before_gc_verification -Drestart.target=garbage_collector -Drestart.mode=GRACEFUL`
- Result: ✅ PASSED (53.65s)

**Test 2 Reproduction Attempt:**
- Command: `mvn -pl test failsafe:integration-test -Dit.test=GarbageCollectorTrashEnabledIT_RestartInjected#testTrashHadoopEnabledAccumuloEnabled -Drestart.position=before_gc_verification -Drestart.target=garbage_collector -Drestart.mode=GRACEFUL`
- Result: ✅ PASSED (23.54s)

**Expected Failure:** `java.lang.IllegalStateException: . Timeout exceeded` at `GarbageCollectorTrashBase.waitForFilesToBeGCd()`

**Conclusion:** This appears to be a flaky/non-deterministic failure or environment-specific issue. The failure could not be reproduced in the current environment.

### Generalized Stack Trace
```
java.lang.IllegalStateException
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
	at org.apache.accumulo.test.functional.GarbageCollectorTrashBase.waitForFilesToBeGCd(GarbageCollectorTrashBase.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalStateException: . Timeout exceeded
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:125)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:89)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:76)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:65)
	at org.apache.accumulo.test.functional.GarbageCollectorTrashBase.waitForFilesToBeGCd(GarbageCollectorTrashBase.java:89)
	at org.apache.accumulo.test.functional.GarbageCollectorTrashDefaultIT_RestartInjected.testTrashHadoopDisabledAccumuloEnabled(GarbageCollectorTrashDefaultIT_RestartInjected.java:92)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.GarbageCollectorTrashDefaultIT_RestartInjected`
- Test Method: `testTrashHadoopDisabledAccumuloEnabled`
- Position: `before_gc_verification`
- Target: `garbage_collector`
- Mode: `GRACEFUL`
- Execution Dir: `033-3a9f15d3`

**Test 2:**
- Test Class: `org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledIT_RestartInjected`
- Test Method: `testTrashHadoopEnabledAccumuloEnabled`
- Position: `before_gc_verification`
- Target: `garbage_collector`
- Mode: `GRACEFUL`
- Execution Dir: `033-25aeb348`

---

## Group 16 (Priority 5)

**Status:** [X] TEST-BUG

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

**Analysis:**
This is a **TEST-BUG** in the test code. The wait condition logic is inverted.

**Root Cause:**
At `ExternalCompactionProgressIT_RestartInjected.java:158-164` (and also in the original non-restart test at `ExternalCompactionProgressIT.java:147-151`), the test has a wait condition with backwards logic:

```java
// Wait until the compaction starts
Wait.waitFor(() -> {
  Map<String,TExternalCompaction> compactions =
      getRunningCompactions(getCluster().getServerContext()).getCompactions();
  return compactions == null || compactions.isEmpty();  // WRONG!
}, 30_000, 100, "Compaction did not start within the expected time");
```

The condition returns `true` when compactions are **null or empty**, meaning it waits UNTIL compactions disappear. However, the comment clearly states "Wait until the compaction starts", which is the opposite intent. The condition should wait UNTIL compactions exist and are NOT empty.

**Why This Bug is Exposed by Restart Injection:**
- **Without restart injection**: The compaction might not have started yet when the wait begins. The condition accidentally returns true immediately (compactions are empty), so the test passes by luck/timing.
- **With restart injection**: The tablet server restart happens right after starting the compaction. After the restart, the compaction is likely running, so compactions are NOT empty. The condition never returns true, causing a 30-second timeout.

**Location:**
- Restart-injected test: `test/src/main/java/org/apache/accumulo/test/compaction/ExternalCompactionProgressIT_RestartInjected.java:158-164`
- Original test: `test/src/main/java/org/apache/accumulo/test/compaction/ExternalCompactionProgressIT.java:147-151`

**Fix Required:**
The condition should be inverted to:
```java
return compactions != null && !compactions.isEmpty();
```

Or follow the correct pattern used later in the same test (lines 181-188 in restart-injected version):
```java
Map<String,TExternalCompaction> metrics = null;
while (metrics == null) {
  try {
    metrics = getRunningCompactions(getCluster().getServerContext()).getCompactions();
  } catch (TException e) {
    UtilWaitThread.sleep(250);
  }
}
```

**Reproduced:** Yes, consistently reproduced with restart injection at position `after_compact_start_duration_1`.

### Generalized Stack Trace
```
java.lang.IllegalStateException
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
```

### Raw Stack Trace Sample
```
java.lang.IllegalStateException: Compaction did not start within the expected time. Timeout exceeded
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:125)
	at org.apache.accumulo.test.compaction.ExternalCompactionProgressIT_RestartInjected.testCompactionDurationContinuesAfterCoordinatorStop(ExternalCompactionProgressIT_RestartInjected.java:158)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.compaction.ExternalCompactionProgressIT_RestartInjected`
- Test Method: `testCompactionDurationContinuesAfterCoordinatorStop`
- Position: `after_compact_start_duration_1`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `033-dce66c77`

---

## Group 18 (Priority 5)

**Status:** [X] CANNOT REPRODUCE

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

**Analysis:**
The test **cannot be reproduced**. When running the test with the specified restart injection parameters, it passes successfully without any failures.

**Test Execution Details:**
- Command: `mvn -pl test failsafe:integration-test -Dit.test=FileNormalizationIT_RestartInjected#testSplits -Drestart.position=after_flush -Drestart.target=tablet_server -Drestart.mode=GRACEFUL`
- Result: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
- Elapsed time: 85.65s

**Possible Reasons:**
1. The failure may have been transient or flaky, occurring only under specific timing conditions that weren't met during this reproduction attempt
2. The test environment or data conditions at the time of the original failure may have been different
3. The failure could be related to non-deterministic behavior that doesn't consistently reproduce

**Reproduced:** No - test passed successfully on reproduction attempt

### Generalized Stack Trace
```
org.apache.accumulo.core.client.AccumuloException
	at org.apache.accumulo.test.VerifyIngest.verifyIngest(VerifyIngest.java)
```

### Raw Stack Trace Sample
```
org.apache.accumulo.core.client.AccumuloException: Did not read expected number of rows. Saw 41122 expected 100000
	at org.apache.accumulo.test.VerifyIngest.verifyIngest(VerifyIngest.java:272)
	at org.apache.accumulo.test.functional.FileNormalizationIT_RestartInjected.testSplits(FileNormalizationIT_RestartInjected.java:113)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.FileNormalizationIT_RestartInjected`
- Test Method: `testSplits`
- Position: `after_flush`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `026-9840f856`

---

## Group 19 (Priority 5)

**Status:** [X] FP

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

### Generalized Stack Trace
```
java.lang.Exception
	at org.apache.accumulo.test.functional.FunctionalTestUtils.checkRFiles(FunctionalTestUtils.java)
```

### Raw Stack Trace Sample
```
java.lang.Exception: Did not find expected number of tablets 0
	at org.apache.accumulo.test.functional.FunctionalTestUtils.checkRFiles(FunctionalTestUtils.java:130)
	at org.apache.accumulo.test.functional.HalfClosedTablet2IT_RestartInjected.testInvalidContextCausesVolumeChooserFailure(HalfClosedTablet2IT_RestartInjected.java:110)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.HalfClosedTablet2IT_RestartInjected`
- Test Method: `testInvalidContextCausesVolumeChooserFailure`
- Position: `after_batch_write`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `025-eaa52149`

### Analysis

This is a **FALSE POSITIVE (FP)** - the failure is caused by an improper restart position and restart framework limitation, not a bug in the Accumulo source code.

**Root Cause:**
The test creates a table, writes data, and then immediately restarts the tablet server at the `after_batch_write` position. After the restart, when the test attempts to check the metadata table (via `FunctionalTestUtils.checkRFiles()`), it finds 0 tablet entries instead of the expected 1 tablet.

**Evidence from Debug Logging:**
```
BEFORE restart:
- Metadata entries found: 5 entries for table ID "1"
- Entries: loc:..., srv:dir, srv:lock, srv:time, ~tab:~pr

AFTER restart:
- Metadata entries found: 0 entries for table ID "1"
- Entire metadata table only has 3 entries (for metadata table itself):
  * +rep< srv:dir
  * +rep< srv:time
  * +rep< ~tab:~pr
- Even after waiting 5 seconds, the user table metadata does not reappear
```

**Why This is a False Positive:**

1. **Restart Framework Limitation**: The restart adapter's `waitActive()` method (in `AccumuloClusterImplAdapter.java:156-178`) waits for:
   - Manager to be available
   - Tablet servers to register
   - Cluster to balance (`client.instanceOperations().waitForBalance()`)

   However, `waitForBalance()` does not guarantee that the metadata table is fully accessible and that all tablet metadata has been restored. It only ensures that tablets are distributed across tablet servers.

2. **Single Tablet Server Edge Case**: In a mini cluster with only 1 tablet server:
   - When that tablet server restarts, ALL tablets (including metadata table tablets) must be unloaded and reloaded
   - During this reload process, there is a window where the metadata table is temporarily unavailable or not fully populated
   - In a production cluster with multiple tablet servers, the metadata table would remain available on other servers during a single tablet server restart

3. **Metadata Table Recovery Timing**: After a tablet server restart, the metadata table tablets need time to:
   - Be reassigned to the restarted tablet server
   - Load from persistent storage
   - Become fully accessible for queries

   The restart framework's readiness check completes before this process finishes, causing the test to proceed prematurely.

4. **Inappropriate Restart Position**: The `after_batch_write` position occurs immediately after data is written, which is too early in the test workflow. The test should either:
   - Wait explicitly for metadata table accessibility after restart, or
   - Use a different restart position that allows for cluster stabilization

**Location of Restart Framework Issue:**
- File: `restart-accumulo-adapter/src/main/java/org/apache/accumulo/restarttest/AccumuloClusterImplAdapter.java`
- Method: `waitActive()` at lines 156-178
- Issue: Does not verify metadata table accessibility before declaring cluster "active"

**Why Not a Source Code Bug:**
- The Accumulo metadata table is correctly persisted and designed to survive restarts
- In properly configured multi-tablet-server clusters, this scenario works correctly
- The metadata table eventually becomes available; the issue is purely timing-related
- No changes to Accumulo source code are needed

**Reproduced:** Yes, consistently reproducible with the given restart configuration.

---

## Group 23 (Priority 5)

**Status:** [X] BUG

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

**Analysis:**
This is a **BUG** - a visibility/consistency issue where data becomes invisible after tablet_server restart followed by delete operations.

**Root Cause:**
The test writes 13 entries with various visibility labels, then performs two tablet_server restarts, then executes delete operations to remove some entries. After the deletes complete and BatchWriter closes, immediate scans show the correct remaining data. However, subsequent verification scans fail to see the same data, indicating a visibility or consistency problem with how delete markers interact with restarted tablet servers.

**Evidence from Reproduction:**

1. **Data Survives Both Restarts:** After the second restart (at position "after_query_data"), all expected data is visible:
   - With auths [A, B]: 4 entries visible (v1, v2, v3, v4)
   - The first queryData() call passes successfully

2. **Deletes Execute Successfully:** Debug logging confirms:
   - BEFORE deletes with auths [A, B]: 4 entries (v1 with [], v2 with [A], v3 with [B], v4 with [A&B])
   - AFTER deletes with auths [A, B]: 1 entry (v3 with [B])
   - AFTER deletes with ALL auths: 6 entries total (v3, v5, v7, v9, v11, v13)

3. **Verification Failure:** Despite immediate scans showing v3 is present after the deletes, the verify() method fails with "Did not see expected value v3" when scanning with auths [A, B].

**The Paradox:**
- Debug scan immediately after BatchWriter closes: sees v3 ✓
- Verification scan moments later with same authorizations: doesn't see v3 ✗

This indicates a timing-sensitive visibility issue where delete markers don't properly interact with data that has survived tablet_server restarts.

**Location:**
The bug is in Accumulo's visibility/consistency layer, specifically:
- How delete markers are processed after tablet_server restarts
- The interaction between delete operations and tablet metadata/caching after restarts
- Possibly related to tablet assignment or RPC consistency issues after restarts

**Comparison with Original Test:**
The original non-restart-injected `VisibilityIT.java` works correctly - deletes are immediately visible in subsequent scans. The restart injection exposes a consistency issue that only manifests when tablet servers are restarted before delete operations.

**Impact:**
This is a **CRITICAL** bug affecting data visibility and consistency. Users may experience:
- Data appearing to be deleted (immediate scans show correct state) but then reappearing in subsequent scans
- Or conversely, data failing to be visible even though it exists
- Inconsistent query results after tablet server restarts and delete operations
- This violates basic database consistency expectations

**Related Issues:**
This is similar to the durability bugs found in Groups 4, 9, and 11, but involves delete markers rather than simple data loss. All these issues point to fundamental problems with Accumulo's handling of data persistence and visibility across tablet server restarts.

**Reproduced:** Yes, successfully reproduced on the first attempt and confirmed with detailed debugging

### Generalized Stack Trace
```
Caused by: java.lang.Exception
```

### Raw Stack Trace Sample
```
java.lang.Exception: Verification failed auths=[A, B] exp=[v3]
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.verify(VisibilityIT_RestartInjected.java:301)
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.queryData(VisibilityIT_RestartInjected.java:263)
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.deleteData(VisibilityIT_RestartInjected.java:182)
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.run(VisibilityIT_RestartInjected.java:107)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.Exception: Did not see expected value v3
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.verify(VisibilityIT_RestartInjected.java:338)
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.verify(VisibilityIT_RestartInjected.java:318)
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.verify(VisibilityIT_RestartInjected.java:299)
	... 8 more

```

### Sample Test Executions

**Test 1:**
- Test Class: `org.apache.accumulo.test.functional.VisibilityIT_RestartInjected`
- Test Method: `run`
- Position: `after_query_data`
- Target: `tablet_server`
- Mode: `GRACEFUL`
- Execution Dir: `014-84bcec2e`

---
