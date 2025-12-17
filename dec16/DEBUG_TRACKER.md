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

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Application exception - org.apache.zookeeper.KeeperException$NoNodeException

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

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Application exception - org.apache.accumulo.core.client.SampleNotPresentException

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

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Application exception - java.util.NoSuchElementException

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

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Application exception - java.util.NoSuchElementException

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

**Status:** [ ] Not started

**Execution Count:** 46

**Priority Reason:** Exception with restart framework involvement

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

**Status:** [ ] Not started

**Execution Count:** 22

**Priority Reason:** Exception with restart framework involvement

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

**Status:** [ ] Not started

**Execution Count:** 4

**Priority Reason:** Timeout - inspect later

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

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Timeout - inspect later

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

**Status:** [ ] Not started

**Execution Count:** 6

**Priority Reason:** Test code issue - exception from test

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

**Status:** [ ] Not started

**Execution Count:** 4

**Priority Reason:** Test code issue - exception from test

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

**Status:** [ ] Not started

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

---

## Group 13 (Priority 5)

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

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

**Status:** [ ] Not started

**Execution Count:** 2

**Priority Reason:** Test code issue - exception from test

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

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

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

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

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

**Status:** [ ] Not started

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

---

## Group 23 (Priority 5)

**Status:** [ ] Not started

**Execution Count:** 1

**Priority Reason:** Test code issue - exception from test

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
