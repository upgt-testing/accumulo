# DEBUG_TRACKER.md
This file tracks the debugging progress for grouped test failures.
Groups are ordered by priority: HIGHEST (most likely bug) to LOWEST (most likely false positive).

**Total Groups:** 18

---

## Group 9 - HIGHEST PRIORITY - Likely Actual Bug

- [x] **TEST CONFIGURATION ISSUE (VERIFIED FIX)** - Summary retrieval returns empty after tablet server restart. Initial diagnosis (stale metadata cache) was INCORRECT. Actual root cause: same as Group 3 - missing `RawLocalFileSystem` configuration. Fixed by adding `RawLocalFileSystem` to base classes. Verified 10/10 passes with fix. See `bugs/BUG-GROUP-9.md` for details.

**Group ID:** 9

**Number of test executions:** 2

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.SummaryIT_RestartInjected.testPermissions`
   - "position": "after_flush_permissions"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "018-6e2ba0c3"

2. Test: `org.apache.accumulo.test.FindMaxIT_RestartInjected.test1`
   - "position": "after_batch_write"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "023-a813d558"

---

## Group 3 - MEDIUM PRIORITY

- [x] **TEST CONFIGURATION ISSUE (VERIFIED FIX)** - Data loss after tablet server restart. Root cause: `LogicalTimeIT` is missing `RawLocalFileSystem` configuration required for proper WAL sync behavior. Hadoop's default `LocalFileSystem` does not properly honor fsync/flush, causing intermittent data loss during WAL recovery. Fix: Add `hadoopCoreSite.set("fs.file.impl", RawLocalFileSystem.class.getName())` to the test. Verified 10/10 passes with fix vs ~33% without. See `bugs/BUG-GROUP-3.md` for details.

**Group ID:** 3

**Number of test executions:** 12

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.run`
   - "position": "after_final_mutation"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "059-da903cd7"

2. Test: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.run`
   - "position": "after_final_mutation"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "063-da903cd7c9a47e5d"

3. Test: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.run`
   - "position": "after_final_mutation"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "064-da903cd7c9a47e5d"

*... and 9 more test execution(s)*

---

## Group 6 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 6

**Number of test executions:** 4

**Raw Stacktrace Sample:**
```
java.lang.IllegalArgumentException: Missing or invalid part of tablet file metadata entry: hdfs://localhost:42415/user/root/.Trash/Current/accumulo/recovery/70f49fc9-54e7-4084-b263-70d93fac8271/finished
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled`
   - "position": "after_first_compact"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "013-06694d5b"

2. Test: `org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled`
   - "position": "after_second_compact"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "014-15076740"

3. Test: `org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled`
   - "position": "after_load_data"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "017-91c40fb4"

*... and 1 more test execution(s)*

---

## Group 7 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 7

**Number of test executions:** 3

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.run`
   - "position": "after_initial_batch_write"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "029-606348c076dd1a74"

2. Test: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.run`
   - "position": "after_initial_batch_write"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "032-606348c076dd1a74"

3. Test: `org.apache.accumulo.test.functional.LogicalTimeIT_RestartInjected.run`
   - "position": "after_initial_batch_write"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "035-606348c076dd1a74"

---

## Group 8 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 8

**Number of test executions:** 2

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.hadoop.its.mapred.AccumuloOutputFormatIT_RestartInjected.testMR`
   - "position": "after_batch_close"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "005-d9f05bcf"

2. Test: `org.apache.accumulo.hadoop.its.mapred.TokenFileIT_RestartInjected.testMR`
   - "position": "after_batch_close"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "005-d0e2aa75"

---

## Group 10 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 10

**Number of test executions:** 2

**Raw Stacktrace Sample:**
```
java.util.NoSuchElementException
	at com.google.common.collect.MoreCollectors$ToOptionalState.getElement(MoreCollectors.java:161)
	at com.google.common.collect.MoreCollectors.lambda$static$1(MoreCollectors.java:73)
	at java.base/java.util.stream.ReferencePipeline.collect(ReferencePipeline.java:686)
	at org.apache.accumulo.harness.AccumuloITBase.getOnlyElement(AccumuloITBase.java:60)
	at org.apache.accumulo.test.ConditionalWriterIT_RestartInjected.testIterators(ConditionalWriterIT_RestartInjected.java:606)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.ConditionalWriterIT_RestartInjected.testIterators`
   - "position": "after_batch_write"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "023-019eaee2"

2. Test: `org.apache.accumulo.test.compaction.BadCompactionServiceConfigIT_RestartInjected.testUsingNonExistentService`
   - "position": "after_batch_write_nonexistent"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "012-70e7f1f5"

---

## Group 11 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 11

**Number of test executions:** 1

**Raw Stacktrace Sample:**
```
org.apache.accumulo.core.client.AccumuloException: saw 1 errors 
	at org.apache.accumulo.test.VerifyIngest.verifyIngest(VerifyIngest.java:262)
	at org.apache.accumulo.test.functional.FileNormalizationIT_RestartInjected.testSplits(FileNormalizationIT_RestartInjected.java:113)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.FileNormalizationIT_RestartInjected.testSplits`
   - "position": "after_ingest"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "012-ffa0c177"

---

## Group 12 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 12

**Number of test executions:** 1

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.HalfClosedTablet2IT_RestartInjected.testInvalidContextCausesVolumeChooserFailure`
   - "position": "after_batch_write"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "012-eaa52149"

---

## Group 13 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 13

**Number of test executions:** 1

**Raw Stacktrace Sample:**
```
java.lang.RuntimeException: org.apache.accumulo.core.metadata.TabletLocationState$BadLocationStateException: No prev-row for key extent {1< last:1001c074b090005 [] 1 false=38c86656dde0:45199}
	at org.apache.accumulo.server.manager.state.MetaDataTableScanner.next(MetaDataTableScanner.java:160)
	at org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.getTabletLocationState(ManagerAssignmentIT_RestartInjected.java:297)
	at org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.test(ManagerAssignmentIT_RestartInjected.java:105)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: org.apache.accumulo.core.metadata.TabletLocationState$BadLocationStateException: No prev-row for key extent {1< last:1001c074b090005 [] 1 false=38c86656dde0:45199}
	at org.apache.accumulo.server.manager.state.MetaDataTableScanner.createTabletLocationState(MetaDataTableScanner.java:216)
	at org.apache.accumulo.server.manager.state.MetaDataTableScanner.next(MetaDataTableScanner.java:158)
	... 7 more

```

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.test`
   - "position": "after_batch_write"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "012-01f15091"

---

## Group 14 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 14

**Number of test executions:** 1

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.test`
   - "position": "after_flush"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "013-730f839e"

---

## Group 15 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 15

**Number of test executions:** 1

**Raw Stacktrace Sample:**
```
org.apache.zookeeper.KeeperException$NoNodeException: KeeperErrorCode = NoNode for /accumulo/ed9f16d2-edbf-4f78-9d9b-9afc513a6335/managers/lock/zlock#c2c340d2-7b9b-465c-86c9-bf0808086a43#0000000000
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.BackupManagerIT_RestartInjected.test`
   - "position": "after_backup_manager_established"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "016-0ead26df"

---

## Group 16 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 16

**Number of test executions:** 1

**Raw Stacktrace Sample:**
```
java.lang.Exception: actual count 0 != expected count 1
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.verifyDefault(VisibilityIT_RestartInjected.java:290)
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.queryDefaultData(VisibilityIT_RestartInjected.java:278)
	at org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.run(VisibilityIT_RestartInjected.java:114)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.VisibilityIT_RestartInjected.run`
   - "position": "after_insert_default_data"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "008-343941f2"

---

## Group 17 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 17

**Number of test executions:** 1

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.ScanRangeIT_RestartInjected.run`
   - "position": "after_insertData_table2"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "013-41ad8829"

---

## Group 18 - MEDIUM PRIORITY

- [ ] Not started

**Group ID:** 18

**Number of test executions:** 1

**Raw Stacktrace Sample:**
```
org.apache.accumulo.core.clientImpl.AccumuloServerException: Error on server 710e562630ff:45851
	at org.apache.accumulo.core.rpc.clients.TServerClient.execute(TServerClient.java:130)
	at org.apache.accumulo.core.rpc.clients.TabletServerThriftClient.execute(TabletServerThriftClient.java:52)
	at org.apache.accumulo.core.clientImpl.TableOperationsImpl$1.retrieve(TableOperationsImpl.java:2009)
	at org.apache.accumulo.test.functional.SummaryIT_RestartInjected.selectionTest(SummaryIT_RestartInjected.java:358)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: org.apache.thrift.TApplicationException: Internal error processing startGetSummaries
	at org.apache.thrift.TServiceClient.receiveBase(TServiceClient.java:81)
	at org.apache.accumulo.core.tabletserver.thrift.TabletClientService$Client.recv_startGetSummaries(TabletClientService.java:850)
	at org.apache.accumulo.core.tabletserver.thrift.TabletClientService$Client.startGetSummaries(TabletClientService.java:835)
	at org.apache.accumulo.core.clientImpl.TableOperationsImpl$1.lambda$retrieve$0(TableOperationsImpl.java:2011)
	at org.apache.accumulo.core.rpc.clients.TServerClient.execute(TServerClient.java:126)
	... 8 more

```

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.SummaryIT_RestartInjected.selectionTest`
   - "position": "after_write_close"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "022-9181d8d6"

---

## Group 1 - LOWER PRIORITY - Likely False Positive or Test Infrastructure Issue

- [ ] Not started

**Group ID:** 1

**Number of test executions:** 46

**Raw Stacktrace Sample:**
```
org.restarttest.core.RestartException: Restart failed at position after_default_scan_server_start
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:85)
	at org.restarttest.api.RestartPointBuilder.execute(RestartPointBuilder.java:172)
	at org.apache.accumulo.test.ScanServerGroupConfigurationIT_RestartInjected.testClientConfiguration(ScanServerGroupConfigurationIT_RestartInjected.java:167)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.IllegalArgumentException: No processes found for role: scan_server
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:87)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:50)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:186)
	at org.restarttest.core.RestartExecutor.performRestart(RestartExecutor.java:170)
	at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:112)
	at org.restarttest.core.RestartExecutor.execute(RestartExecutor.java:83)
	... 7 more

```

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.ScanServerGroupConfigurationIT_RestartInjected.testClientConfiguration`
   - "position": "after_default_scan_server_start"
   - "target": "scan_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "004-d757482c"

2. Test: `org.apache.accumulo.test.ScanServerGroupConfigurationIT_RestartInjected.testClientConfiguration`
   - "position": "after_group1_scan_server_start"
   - "target": "scan_server"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "006-0da44148"

3. Test: `org.apache.accumulo.test.ScanServerGroupConfigurationIT_RestartInjected.testClientConfiguration`
   - "position": "after_group1_scan"
   - "target": "scan_server"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "007-c6d3721b"

*... and 43 more test execution(s)*

---

## Group 2 - LOWER PRIORITY - Likely False Positive or Test Infrastructure Issue

- [ ] Not started

**Group ID:** 2

**Number of test executions:** 22

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.TabletMetadataIT_RestartInjected.getLiveTServersTest`
   - "position": "after_tserver_killed"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "019-f10b8f3d"

2. Test: `org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.testShutdownOnlyTServerWithoutUserTable`
   - "position": "after_wait_for_balance"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "026-052a13e6"

3. Test: `org.apache.accumulo.test.compaction.ExternalCompaction_1_IT_RestartInjected.testExternalCompactionDeadTServer`
   - "position": "after_table_create_dead"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "010-444d7d9a"

*... and 19 more test execution(s)*

---

## Group 4 - LOWER PRIORITY - Likely False Positive or Test Infrastructure Issue

- [ ] Not started

**Group ID:** 4

**Number of test executions:** 9

**Raw Stacktrace Sample:**
```
java.lang.IllegalStateException: . Timeout exceeded
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:125)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:89)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:76)
	at org.apache.accumulo.test.functional.HalfClosedTablet2IT_RestartInjected.testInvalidContextCausesVolumeChooserFailure(HalfClosedTablet2IT_RestartInjected.java:117)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```

**Sample Test Executions:**

1. Test: `org.apache.accumulo.test.functional.HalfClosedTablet2IT_RestartInjected.testInvalidContextCausesVolumeChooserFailure`
   - "position": "after_set_invalid_context"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "013-856ec432"

2. Test: `org.apache.accumulo.test.functional.HalfClosedTablet2IT_RestartInjected.testInvalidContextCausesVolumeChooserFailure`
   - "position": "after_flush_with_invalid_context"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "014-e642e7a5"

3. Test: `org.apache.accumulo.test.functional.ManagerAssignmentIT_RestartInjected.testShutdownOnlyTServerWithoutUserTable`
   - "position": "after_start_single_tserver_no_user_table"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "025-33bbcefc"

*... and 6 more test execution(s)*

---

## Group 5 - LOWER PRIORITY - Likely False Positive or Test Infrastructure Issue

- [ ] Not started

**Group ID:** 5

**Number of test executions:** 8

**Raw Stacktrace Sample:**
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

**Sample Test Executions:**

1. Test: `org.apache.accumulo.miniclusterImpl.CleanShutdownMacTest_RestartInjected.testExecutorServiceShutdown`
   - "position": "after_cluster_create"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "002-be7271aa"

2. Test: `org.apache.accumulo.test.upgrade.UpgradeUtilIT_RestartInjected.testPrepareFailsDueToFateTransactions`
   - "position": "after_manager_stop"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "027-36e37442"

3. Test: `org.apache.accumulo.test.upgrade.UpgradeUtilIT_RestartInjected.testPrepareSucceeds`
   - "position": "after_manager_stopped"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "027-db9152ba"

*... and 5 more test execution(s)*

---

