# Accumulo Debug Tracker - Jan10

## Priority Order Rationale

Groups are ordered by likelihood of being actual bugs:
1. **Highest Priority**: NPE/IOOB/IllegalArgumentException from non-test, non-restarttest code
2. **Medium Priority**: Exceptions from test utility code or assertion-like checks
3. **Lowest Priority**: Timeouts and exceptions directly from restarttest framework (FALSE POSITIVES)

---

### Group 5: IllegalArgumentException in TabletFile.parsePath

[ ] Not started

**Test Executions**: 5 failures

**Generalized Stack Trace**:
```
java.lang.IllegalArgumentException
	at org.apache.accumulo.core.metadata.TabletFile.parsePath(TabletFile.java)
```

**Raw Stack Trace Sample**:
```
java.lang.IllegalArgumentException: Missing or invalid part of tablet file metadata entry: hdfs://localhost:36851/user/root/.Trash/Current/accumulo/recovery/05bd72db-c862-4015-8bb7-56e04fda0dda/finished
	at org.apache.accumulo.core.metadata.TabletFile.parsePath(TabletFile.java:124)
	at org.apache.accumulo.core.metadata.TabletFile.<init>(TabletFile.java:162)
	at org.apache.accumulo.test.functional.GarbageCollectorTrashBase.countFilesInTrash(GarbageCollectorTrashBase.java:115)
	at org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled(GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.java:123)
```

**Test Executions (Examples)**:

1. Test: `GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled`
   - "position": "after_initial_flush"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "012-a2e7e10a"

2. Test: `GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled`
   - "position": "after_first_compact"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "013-06694d5b"

3. Test: `GarbageCollectorTrashEnabledIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled`
   - "position": "after_load_data"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "017-91c40fb4"

---

### Group 3: NullPointerException in ProcessReference

[ ] Not started

**Test Executions**: 8 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.NullPointerException
	at java.base/java.util.Objects.requireNonNull(Objects.java)
```

**Raw Stack Trace Sample**:
```
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
```

**Test Executions (Examples)**:

1. Test: `CleanShutdownMacTest_RestartInjected.testExecutorServiceShutdown`
   - "position": "after_cluster_create"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "002-be7271aa"

2. Test: `UpgradeUtilIT_RestartInjected.testPrepareFailsDueToFateTransactions`
   - "position": "after_manager_stop"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "027-36e37442"

3. Test: `VolumeIT_RestartInjected.testRemoveVolumes`
   - "position": "after_stopall"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "003-bddea203"

---

### Group 7: ThriftTableOperationException - Compaction Conflict

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
Caused by: ThriftTableOperationException(tableId
	at org.apache.accumulo.core.manager.thrift.FateService$waitForFateOperation_result$waitForFateOperation_resultStandardScheme.read(FateService.java)
```

**Raw Stack Trace Sample**:
```
org.apache.accumulo.core.client.AccumuloException: Another compaction with iterators and/or a compaction strategy is running
	at org.apache.accumulo.core.clientImpl.TableOperationsImpl.doFateOperation(TableOperationsImpl.java:409)
	at org.apache.accumulo.core.clientImpl.TableOperationsImpl.compact(TableOperationsImpl.java:895)
	at org.apache.accumulo.test.compaction.CompactionExecutorIT_RestartInjected.testDispatchUser(CompactionExecutorIT_RestartInjected.java:348)
Caused by: ThriftTableOperationException(tableId:2, tableName:null, op:COMPACT, type:OTHER, description:Another compaction with iterators and/or a compaction strategy is running)
	at org.apache.accumulo.core.manager.thrift.FateService$waitForFateOperation_result$waitForFateOperation_resultStandardScheme.read(FateService.java:5045)
```

**Test Executions (Examples)**:

1. Test: `CompactionExecutorIT_RestartInjected.testDispatchUser`
   - "position": "after_compact_dispatch_user"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "023-533f6c6c"

---

### Group 8: ZooKeeper NoNodeException

[ ] Not started

**Test Executions**: 1 failure

**Generalized Stack Trace**:
```
org.apache.zookeeper.KeeperException$NoNodeException
	at org.apache.zookeeper.KeeperException.create(KeeperException.java)
```

**Raw Stack Trace Sample**:
```
org.apache.zookeeper.KeeperException$NoNodeException: KeeperErrorCode = NoNode for /accumulo/f0a4b0ee-be10-40fc-b23e-aae98f425e70/managers/lock/zlock#25defc49-b2b1-4ba0-ad39-7185981ffe52#0000000000
	at org.apache.zookeeper.KeeperException.create(KeeperException.java:117)
	at org.apache.zookeeper.KeeperException.create(KeeperException.java:53)
	at org.apache.zookeeper.ZooKeeper.getData(ZooKeeper.java:1972)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.lambda$getData$0(ZooReader.java:79)
	at org.apache.accumulo.core.fate.zookeeper.ZooReader.retryLoopMutator(ZooReader.java:184)
	at org.apache.accumulo.test.functional.BackupManagerIT_RestartInjected.test(BackupManagerIT_RestartInjected.java:67)
```

**Test Executions (Examples)**:

1. Test: `BackupManagerIT_RestartInjected.test`
   - "position": "after_backup_manager_established"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "016-0ead26df"

---

### Group 6: FunctionalTestUtils.checkRFiles - Missing Map Files

[ ] Not started

**Test Executions**: 2 failures

**Generalized Stack Trace**:
```
java.lang.Exception
	at org.apache.accumulo.test.functional.FunctionalTestUtils.checkRFiles(FunctionalTestUtils.java)
```

**Raw Stack Trace Sample**:
```
java.lang.Exception: tablet 1< has 0 map files
	at org.apache.accumulo.test.functional.FunctionalTestUtils.checkRFiles(FunctionalTestUtils.java:137)
	at org.apache.accumulo.test.functional.BadIteratorMincIT_RestartInjected.test(BadIteratorMincIT_RestartInjected.java:93)
```

**Test Executions (Examples)**:

1. Test: `BadIteratorMincIT_RestartInjected.test`
   - "position": "after_first_flush"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "005-1fe0e18f"

2. Test: `HalfClosedTablet2IT_RestartInjected.testInvalidContextCausesVolumeChooserFailure`
   - "position": "after_batch_write"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "012-eaa52149"

---

### Group 4: Wait.waitFor Timeout

[ ] Not started

**Test Executions**: 8 failures

**Generalized Stack Trace**:
```
java.lang.IllegalStateException
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java)
```

**Raw Stack Trace Sample**:
```
java.lang.IllegalStateException: . Timeout exceeded
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:125)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:89)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:76)
	at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:65)
	at org.apache.accumulo.test.functional.ShutdownIT_RestartInjected.runAdminStopTest(ShutdownIT_RestartInjected.java:205)
```

**Test Executions (Examples)**:

1. Test: `ShutdownIT_RestartInjected.adminStop`
   - "position": "after_get_tablet_servers"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "013-79d84522"

2. Test: `ExternalCompactionProgressIT_RestartInjected.testCompactionDurationContinuesAfterCoordinatorStop`
   - "position": "after_compact_start_duration_1"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "019-dce66c77"

3. Test: `GarbageCollectorTrashDefaultIT_RestartInjected.testTrashHadoopDisabledAccumuloEnabled`
   - "position": "before_gc_verification"
   - "target": "garbage_collector"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "019-3a9f15d3"

---

### Group 2: Timeout Waiting for Tablet Servers (FALSE POSITIVE - Restart Framework)

[ ] Not started

**Test Executions**: 23 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.Exception
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitForTabletServers(AccumuloClusterImplAdapter.java)
```

**Raw Stack Trace Sample**:
```
org.restarttest.core.RestartException: Restart failed at position after_tserver_killed
Caused by: java.lang.Exception: Timeout waiting for tablet servers to register
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitForTabletServers(AccumuloClusterImplAdapter.java:314)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitActive(AccumuloClusterImplAdapter.java:168)
```

**Test Executions (Examples)**:

1. Test: `TabletMetadataIT_RestartInjected.getLiveTServersTest`
   - "position": "after_tserver_killed"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "019-f10b8f3d"

2. Test: `ManagerAssignmentIT_RestartInjected.testShutdownOnlyTServerWithoutUserTable`
   - "position": "after_wait_for_balance"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "026-052a13e6"

3. Test: `ExternalCompaction_1_IT_RestartInjected.testExternalCompactionDeadTServer`
   - "position": "after_table_create_dead"
   - "target": "manager"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "010-444d7d9a"

---

### Group 1: No Processes Found for Role (FALSE POSITIVE - Restart Framework)

[ ] Not started

**Test Executions**: 46 failures

**Generalized Stack Trace**:
```
Caused by: java.lang.IllegalArgumentException
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java)
```

**Raw Stack Trace Sample**:
```
org.restarttest.core.RestartException: Restart failed at position after_default_scan_server_start
Caused by: java.lang.IllegalArgumentException: No processes found for role: scan_server
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:87)
	at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:50)
	at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:190)
```

**Test Executions (Examples)**:

1. Test: `ScanServerGroupConfigurationIT_RestartInjected.testClientConfiguration`
   - "position": "after_default_scan_server_start"
   - "target": "scan_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "004-d757482c"

2. Test: `ScanServerGroupConfigurationIT_RestartInjected.testClientConfiguration`
   - "position": "after_group1_scan_server_start"
   - "target": "scan_server"
   - "mode": "GRACEFUL"
   - "index": "1"
   - "executionDir": "006-0da44148"

3. Test: `HalfDeadTServerIT_RestartInjected.testRecover`
   - "position": "after_kill_regular_tserver"
   - "target": "tablet_server"
   - "mode": "GRACEFUL"
   - "index": "0"
   - "executionDir": "011-3e53094c"

---

## Summary Statistics

| Priority | Group ID | Exception Type | Count | Verdict |
|----------|----------|----------------|-------|---------|
| HIGH | 5 | IllegalArgumentException (TabletFile.parsePath) | 5 | Potential Bug |
| HIGH | 3 | NullPointerException (ProcessReference) | 8 | Potential Bug |
| HIGH | 7 | ThriftTableOperationException (Compaction) | 1 | Potential Bug |
| MEDIUM | 8 | ZooKeeper NoNodeException | 1 | Needs Inspection |
| MEDIUM | 6 | Exception (checkRFiles) | 2 | Assertion-like |
| LOW | 4 | Timeout (Wait.waitFor) | 8 | Test Timeout |
| FALSE POSITIVE | 2 | Timeout (restarttest) | 23 | Framework Issue |
| FALSE POSITIVE | 1 | IllegalArgumentException (restarttest) | 46 | Framework Issue |

**Total Groups**: 8
**Total Failures**: 94 (non-assertion)
