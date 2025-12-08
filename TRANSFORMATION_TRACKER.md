# Test Transformation Tracker

This file tracks the progress of test transformations for Accumulo restart testing.

**Legend:**
- `[ ]` - Transformation not started
- `[x]` - Transformation completed

## Hadoop Integration Tests - mapred (6 tests)

- [ ] org.apache.accumulo.hadoop.its.mapred.AccumuloFileOutputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapred.AccumuloInputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapred.AccumuloOutputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapred.AccumuloRowInputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapred.MultiTableInputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapred.TokenFileIT

## Hadoop Integration Tests - mapreduce (8 tests)

- [ ] org.apache.accumulo.hadoop.its.mapreduce.AccumuloFileOutputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapreduce.AccumuloInputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapreduce.AccumuloOutputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapreduce.AccumuloRowInputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapreduce.MapReduceIT
- [ ] org.apache.accumulo.hadoop.its.mapreduce.MultiTableInputFormatIT
- [ ] org.apache.accumulo.hadoop.its.mapreduce.RowHashIT
- [ ] org.apache.accumulo.hadoop.its.mapreduce.TokenFileIT

## Mini Cluster Tests (2 tests)

- [ ] org.apache.accumulo.miniclusterImpl.CleanShutdownMacTest
- [ ] org.apache.accumulo.miniclusterImpl.MiniAccumuloClusterImplTest

## Core Tests (45 tests)

- [ ] org.apache.accumulo.test.AdminCheckIT_SimpleSuite
- [ ] org.apache.accumulo.test.AmpleIT
- [ ] org.apache.accumulo.test.ample.TestAmpleIT_SimpleSuite
- [ ] org.apache.accumulo.test.ample.usage.TabletFileUpdateIT_SimpleSuite
- [ ] org.apache.accumulo.test.AuditMessageIT
- [ ] org.apache.accumulo.test.BadDeleteMarkersCreatedIT
- [ ] org.apache.accumulo.test.BalanceIT
- [ ] org.apache.accumulo.test.BalanceWithOfflineTableIT
- [ ] org.apache.accumulo.test.BatchWriterInTabletServerIT
- [ ] org.apache.accumulo.test.BatchWriterIT
- [ ] org.apache.accumulo.test.BrokenBalancerIT
- [ ] org.apache.accumulo.test.BulkImportSequentialRowsIT
- [ ] org.apache.accumulo.test.CleanWalIT
- [ ] org.apache.accumulo.test.ClientSideIteratorIT
- [ ] org.apache.accumulo.test.CloneIT_SimpleSuite
- [ ] org.apache.accumulo.test.CloseScannerIT
- [ ] org.apache.accumulo.test.compaction.BadCompactionServiceConfigIT
- [ ] org.apache.accumulo.test.compaction.ClassLoaderContextCompactionIT
- [ ] org.apache.accumulo.test.compaction.CompactionConfigChangeIT
- [ ] org.apache.accumulo.test.compaction.CompactionExecutorIT
- [ ] org.apache.accumulo.test.compaction.CompactionPriorityQueueMetricsIT
- [ ] org.apache.accumulo.test.compaction.ErasureCodeIT
- [ ] org.apache.accumulo.test.compaction.ExternalCompaction_1_IT
- [ ] org.apache.accumulo.test.compaction.ExternalCompaction_2_IT
- [ ] org.apache.accumulo.test.compaction.ExternalCompaction2ITBase
- [ ] org.apache.accumulo.test.compaction.ExternalCompaction_3_IT
- [ ] org.apache.accumulo.test.compaction.ExternalCompaction4_IT
- [ ] org.apache.accumulo.test.compaction.ExternalCompactionMetricsIT
- [ ] org.apache.accumulo.test.compaction.ExternalCompactionProgressIT
- [ ] org.apache.accumulo.test.compaction.FlakyExternalCompaction2IT
- [ ] org.apache.accumulo.test.compaction.SplitCancelsMajCIT
- [ ] org.apache.accumulo.test.ComprehensiveFlakyAmpleIT
- [ ] org.apache.accumulo.test.ComprehensiveFlakyFateIT
- [ ] org.apache.accumulo.test.ComprehensiveIT
- [ ] org.apache.accumulo.test.ComprehensiveITBase
- [ ] org.apache.accumulo.test.ComprehensiveTableOperationsIT
- [ ] org.apache.accumulo.test.ConditionalWriterIT
- [ ] org.apache.accumulo.test.conf.PropStoreConfigIT_SimpleSuite
- [ ] org.apache.accumulo.test.conf.ResourceGroupConfigIT
- [ ] org.apache.accumulo.test.conf.util.ZooPropEditorIT_SimpleSuite
- [ ] org.apache.accumulo.test.CorruptMutationIT
- [ ] org.apache.accumulo.test.CountNameNodeOpsBulkIT
- [ ] org.apache.accumulo.test.CreateTableIT_SimpleSuite
- [ ] org.apache.accumulo.test.DeprecatedPropertyUtilIT
- [ ] org.apache.accumulo.test.DetectDeadTabletServersIT

## FATE Tests (24 tests)

- [ ] org.apache.accumulo.test.DumpConfigIT
- [ ] org.apache.accumulo.test.ECAdminIT
- [ ] org.apache.accumulo.test.ExistingMacIT
- [ ] org.apache.accumulo.test.fate.FateExecutionOrderITBase
- [ ] org.apache.accumulo.test.fate.FateITBase
- [ ] org.apache.accumulo.test.fate.FateOpsCommandsITBase
- [ ] org.apache.accumulo.test.fate.FatePoolsWatcherITBase
- [ ] org.apache.accumulo.test.fate.FateStatusEnforcementITBase
- [ ] org.apache.accumulo.test.fate.FateStoreITBase
- [ ] org.apache.accumulo.test.fate.ManagerRepoIT_SimpleSuite
- [ ] org.apache.accumulo.test.fate.meta.MetaFateExecutionOrderIT_SimpleSuite
- [ ] org.apache.accumulo.test.fate.meta.MetaFateIT
- [ ] org.apache.accumulo.test.fate.meta.MetaFateOpsCommandsIT
- [ ] org.apache.accumulo.test.fate.meta.MetaFatePoolsWatcherIT
- [ ] org.apache.accumulo.test.fate.meta.MetaFateStatusEnforcementIT
- [ ] org.apache.accumulo.test.fate.meta.MetaFateStoreFateIT
- [ ] org.apache.accumulo.test.fate.meta.MetaMultipleStoresIT
- [ ] org.apache.accumulo.test.fate.MultipleStoresITBase
- [ ] org.apache.accumulo.test.fate.RangedTableLocksIT
- [ ] org.apache.accumulo.test.fate.user.FateMutatorImplIT_SimpleSuite
- [ ] org.apache.accumulo.test.fate.user.UserFateExecutionOrderIT_SimpleSuite
- [ ] org.apache.accumulo.test.fate.user.UserFateIT_SimpleSuite
- [ ] org.apache.accumulo.test.fate.user.UserFateOpsCommandsIT
- [ ] org.apache.accumulo.test.fate.user.UserFatePoolsWatcherIT_SimpleSuite

## User FATE Tests (4 tests)

- [ ] org.apache.accumulo.test.fate.user.UserFateStatusEnforcementIT_SimpleSuite
- [ ] org.apache.accumulo.test.fate.user.UserFateStoreFateIT_SimpleSuite
- [ ] org.apache.accumulo.test.fate.user.UserMultipleStoresIT_SimpleSuite
- [ ] org.apache.accumulo.test.FindMaxIT

## Functional Tests (128 tests)

- [ ] org.apache.accumulo.test.functional.AccumuloClientIT
- [ ] org.apache.accumulo.test.functional.AccumuloConfigurationIT
- [ ] org.apache.accumulo.test.functional.AddSplitIT_SimpleSuite
- [ ] org.apache.accumulo.test.functional.AdvertiseAndBindIT
- [ ] org.apache.accumulo.test.functional.AmpleConditionalWriterIT
- [ ] org.apache.accumulo.test.functional.BackupManagerIT
- [ ] org.apache.accumulo.test.functional.BadIteratorMincIT
- [ ] org.apache.accumulo.test.functional.BadLocalityGroupMincIT
- [ ] org.apache.accumulo.test.functional.BalanceAfterCommsFailureIT
- [ ] org.apache.accumulo.test.functional.BalanceInPresenceOfOfflineTableIT
- [ ] org.apache.accumulo.test.functional.BatchScanSplitIT
- [ ] org.apache.accumulo.test.functional.BatchWriterFlushIT
- [ ] org.apache.accumulo.test.functional.BigRootTabletIT
- [ ] org.apache.accumulo.test.functional.BinaryIT
- [ ] org.apache.accumulo.test.functional.BinaryStressIT
- [ ] org.apache.accumulo.test.functional.BloomFilterIT
- [ ] org.apache.accumulo.test.functional.BulkIT
- [ ] org.apache.accumulo.test.functional.BulkNewIT
- [ ] org.apache.accumulo.test.functional.BulkNewMetadataSkipIT
- [ ] org.apache.accumulo.test.functional.BulkSplitOptimizationIT
- [ ] org.apache.accumulo.test.functional.ChaoticBalancerIT
- [ ] org.apache.accumulo.test.functional.CloneTestIT_SimpleSuite
- [ ] org.apache.accumulo.test.functional.CombinerIT
- [ ] org.apache.accumulo.test.functional.CompactionFlakyAmpleIT
- [ ] org.apache.accumulo.test.functional.CompactionIT
- [ ] org.apache.accumulo.test.functional.ConcurrencyIT
- [ ] org.apache.accumulo.test.functional.ConcurrentDeleteTableIT
- [ ] org.apache.accumulo.test.functional.ConcurrentTableNameOperationsIT
- [ ] org.apache.accumulo.test.functional.ConstraintIT
- [ ] org.apache.accumulo.test.functional.CreateAndUseIT
- [ ] org.apache.accumulo.test.functional.CreateInitialSplitsIT
- [ ] org.apache.accumulo.test.functional.CreateManyScannersIT
- [ ] org.apache.accumulo.test.functional.CredentialsIT
- [ ] org.apache.accumulo.test.functional.DebugClientConnectionIT
- [ ] org.apache.accumulo.test.functional.DeletedTablesDontFlushIT_SimpleSuite
- [ ] org.apache.accumulo.test.functional.DeleteEverythingIT
- [ ] org.apache.accumulo.test.functional.DeleteFailIT
- [ ] org.apache.accumulo.test.functional.DeleteIT
- [ ] org.apache.accumulo.test.functional.DeleteRowsIT
- [ ] org.apache.accumulo.test.functional.DeleteRowsSplitIT
- [ ] org.apache.accumulo.test.functional.DurabilityIT
- [ ] org.apache.accumulo.test.functional.ExitCodesIT
- [ ] org.apache.accumulo.test.functional.FateConcurrencyIT
- [ ] org.apache.accumulo.test.functional.FateStarvationIT
- [ ] org.apache.accumulo.test.functional.FileMetadataIT
- [ ] org.apache.accumulo.test.functional.FileNormalizationIT_SimpleSuite
- [ ] org.apache.accumulo.test.functional.FindCompactionTmpFilesIT_SimpleSuite
- [ ] org.apache.accumulo.test.functional.FlushNoFileIT
- [ ] org.apache.accumulo.test.functional.GarbageCollectorIT
- [ ] org.apache.accumulo.test.functional.GarbageCollectorTrashBase
- [ ] org.apache.accumulo.test.functional.GarbageCollectorTrashDefaultIT
- [ ] org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledIT
- [ ] org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledWithCustomPolicyIT
- [ ] org.apache.accumulo.test.functional.GracefulShutdownIT
- [ ] org.apache.accumulo.test.functional.HalfClosedTablet2IT
- [ ] org.apache.accumulo.test.functional.HalfClosedTabletIT
- [ ] org.apache.accumulo.test.functional.HalfDeadServerWatcherIT
- [ ] org.apache.accumulo.test.functional.HalfDeadTServerIT
- [ ] org.apache.accumulo.test.functional.IdleProcessMetricsIT
- [ ] org.apache.accumulo.test.functional.IteratorMincClassCastBugIT
- [ ] org.apache.accumulo.test.functional.LargeRowIT
- [ ] org.apache.accumulo.test.functional.LastLocationIT
- [ ] org.apache.accumulo.test.functional.LateLastContactIT
- [ ] org.apache.accumulo.test.functional.LocalityGroupIT
- [ ] org.apache.accumulo.test.functional.LogicalTimeIT
- [ ] org.apache.accumulo.test.functional.ManagerApiIT
- [ ] org.apache.accumulo.test.functional.ManagerAssignmentIT
- [ ] org.apache.accumulo.test.functional.ManagerFailoverIT
- [ ] org.apache.accumulo.test.functional.ManyWriteAheadLogsIT
- [ ] org.apache.accumulo.test.functional.MaxOpenIT
- [ ] org.apache.accumulo.test.functional.MemoryStarvedMajCIT
- [ ] org.apache.accumulo.test.functional.MemoryStarvedMinCIT
- [ ] org.apache.accumulo.test.functional.MemoryStarvedScanIT
- [ ] org.apache.accumulo.test.functional.MergeTabletsFlakyFateIT
- [ ] org.apache.accumulo.test.functional.MergeTabletsITBase
- [ ] org.apache.accumulo.test.functional.MergeTabletsIT_SimpleSuite
- [ ] org.apache.accumulo.test.functional.MetadataIT
- [ ] org.apache.accumulo.test.functional.MetadataMaxFilesIT
- [ ] org.apache.accumulo.test.functional.MetadataSplitIT
- [ ] org.apache.accumulo.test.functional.MonitorSslIT
- [ ] org.apache.accumulo.test.functional.OnDemandTabletUnloadingFlakyAmpleIT
- [ ] org.apache.accumulo.test.functional.OnDemandTabletUnloadingIT
- [ ] org.apache.accumulo.test.functional.PermissionsIT
- [ ] org.apache.accumulo.test.functional.PerTableCryptoIT
- [ ] org.apache.accumulo.test.functional.ReadWriteIT
- [ ] org.apache.accumulo.test.functional.RecoveryWithEmptyRFileIT
- [ ] org.apache.accumulo.test.functional.RegexGroupBalanceIT
- [ ] org.apache.accumulo.test.functional.RenameIT
- [ ] org.apache.accumulo.test.functional.RestartIT
- [ ] org.apache.accumulo.test.functional.RestartStressIT
- [ ] org.apache.accumulo.test.functional.RowDeleteIT
- [ ] org.apache.accumulo.test.functional.ScanIdIT
- [ ] org.apache.accumulo.test.functional.ScanIteratorIT
- [ ] org.apache.accumulo.test.functional.ScannerContextIT
- [ ] org.apache.accumulo.test.functional.ScannerIT
- [ ] org.apache.accumulo.test.functional.ScanRangeIT
- [ ] org.apache.accumulo.test.functional.ScanSessionTimeOutIT
- [ ] org.apache.accumulo.test.functional.ServerSideErrorIT
- [ ] org.apache.accumulo.test.functional.SessionDurabilityIT
- [ ] org.apache.accumulo.test.functional.ShutdownIT
- [ ] org.apache.accumulo.test.functional.SimpleBalancerFairnessIT
- [ ] org.apache.accumulo.test.functional.SparseColumnFamilyIT
- [ ] org.apache.accumulo.test.functional.SplitIT
- [ ] org.apache.accumulo.test.functional.SplitMillionIT
- [ ] org.apache.accumulo.test.functional.SplitRecoveryIT
- [ ] org.apache.accumulo.test.functional.SslIT
- [ ] org.apache.accumulo.test.functional.StartIT
- [ ] org.apache.accumulo.test.functional.SummaryIT
- [ ] org.apache.accumulo.test.functional.SuspendMarkerIT
- [ ] org.apache.accumulo.test.functional.TableIT
- [ ] org.apache.accumulo.test.functional.TabletAvailabilityIT
- [ ] org.apache.accumulo.test.functional.TabletIT
- [ ] org.apache.accumulo.test.functional.TabletManagementIteratorIT
- [ ] org.apache.accumulo.test.functional.TabletMergeabilityIT
- [ ] org.apache.accumulo.test.functional.TabletMetadataIT
- [ ] org.apache.accumulo.test.functional.TabletResourceGroupBalanceIT
- [ ] org.apache.accumulo.test.functional.TabletsMetadataIT_SimpleSuite
- [ ] org.apache.accumulo.test.functional.ThriftMaxFrameSizeIT
- [ ] org.apache.accumulo.test.functional.TimeoutIT
- [ ] org.apache.accumulo.test.functional.VisibilityIT
- [ ] org.apache.accumulo.test.functional.WALFlakyAmpleIT
- [ ] org.apache.accumulo.test.functional.WALSunnyDayIT
- [ ] org.apache.accumulo.test.functional.WatchTheWatchCountIT
- [ ] org.apache.accumulo.test.functional.WriteAheadLogEncryptedIT
- [ ] org.apache.accumulo.test.functional.WriteAheadLogIT
- [ ] org.apache.accumulo.test.functional.WriteLotsIT
- [ ] org.apache.accumulo.test.functional.ZooCacheIT
- [ ] org.apache.accumulo.test.functional.ZookeeperRestartIT

## Additional Tests (75 tests)

- [ ] org.apache.accumulo.test.GarbageCollectWALIT
- [ ] org.apache.accumulo.test.GCRunIT_SimpleSuite
- [ ] org.apache.accumulo.test.ImportExportIT
- [ ] org.apache.accumulo.test.InstanceOperationsIT
- [ ] org.apache.accumulo.test.InterruptibleScannersIT
- [ ] org.apache.accumulo.test.IsolationAndDeepCopyIT
- [ ] org.apache.accumulo.test.IteratorEnvIT
- [ ] org.apache.accumulo.test.KeyValueEqualityIT
- [ ] org.apache.accumulo.test.LargeReadIT
- [ ] org.apache.accumulo.test.LargeSplitRowIT
- [ ] org.apache.accumulo.test.LocatorIT
- [ ] org.apache.accumulo.test.lock.ServiceLockPathsIT
- [ ] org.apache.accumulo.test.manager.SuspendedTabletsIT
- [ ] org.apache.accumulo.test.MaxWalReferencedIT
- [ ] org.apache.accumulo.test.MetaConstraintRetryIT
- [ ] org.apache.accumulo.test.MetaGetsReadersIT
- [ ] org.apache.accumulo.test.MetaRecoveryIT
- [ ] org.apache.accumulo.test.MetaSplitIT
- [ ] org.apache.accumulo.test.metrics.MetricsIT
- [ ] org.apache.accumulo.test.metrics.MetricsThriftRpcIT
- [ ] org.apache.accumulo.test.MissingWalHeaderCompletesRecoveryIT
- [ ] org.apache.accumulo.test.MultiTableBatchWriterIT_SimpleSuite
- [ ] org.apache.accumulo.test.MultiTableRecoveryIT
- [ ] org.apache.accumulo.test.NamespacesIT_SimpleSuite
- [ ] org.apache.accumulo.test.NewTableConfigurationIT_SimpleSuite
- [ ] org.apache.accumulo.test.OfflineTableIT
- [ ] org.apache.accumulo.test.OrIteratorIT_SimpleSuite
- [ ] org.apache.accumulo.test.PrintInfoIT_SimpleSuite
- [ ] org.apache.accumulo.test.RecoveryCompactionsAreFlushesIT
- [ ] org.apache.accumulo.test.RecoveryIT
- [ ] org.apache.accumulo.test.RootRecoveryIT
- [ ] org.apache.accumulo.test.SampleIT_SimpleSuite
- [ ] org.apache.accumulo.test.ScanConsistencyIT
- [ ] org.apache.accumulo.test.ScanFlushWithTimeIT
- [ ] org.apache.accumulo.test.ScanServerConcurrentTabletScanIT
- [ ] org.apache.accumulo.test.ScanServerGroupConfigurationIT
- [ ] org.apache.accumulo.test.ScanServerIT
- [ ] org.apache.accumulo.test.ScanServerMaxLatencyIT
- [ ] org.apache.accumulo.test.ScanServerMetadataEntriesCleanIT_SimpleSuite
- [ ] org.apache.accumulo.test.ScanServerMetadataEntriesIT
- [ ] org.apache.accumulo.test.ScanServerMultipleScansIT
- [ ] org.apache.accumulo.test.ScanServer_NoServersIT
- [ ] org.apache.accumulo.test.ScanServerShutdownIT
- [ ] org.apache.accumulo.test.server.security.SystemCredentialsIT
- [ ] org.apache.accumulo.test.shell.ConfigSetIT_SimpleSuite
- [ ] org.apache.accumulo.test.shell.ShellAuthenticatorIT_SimpleSuite
- [ ] org.apache.accumulo.test.shell.ShellConfigIT
- [ ] org.apache.accumulo.test.shell.ShellCreateNamespaceIT
- [ ] org.apache.accumulo.test.shell.ShellCreateTableIT
- [ ] org.apache.accumulo.test.shell.ShellIT
- [ ] org.apache.accumulo.test.shell.ShellServerIT
- [ ] org.apache.accumulo.test.suites.SimpleSharedMacTestSuiteIT
- [ ] org.apache.accumulo.test.TableConfigurationUpdateIT
- [ ] org.apache.accumulo.test.TableOperationsIT
- [ ] org.apache.accumulo.test.TabletServerGivesUpIT
- [ ] org.apache.accumulo.test.TabletServerHdfsRestartIT
- [ ] org.apache.accumulo.test.TestDualAssignment
- [ ] org.apache.accumulo.test.ThriftServerBindsBeforeZooKeeperLockIT
- [ ] org.apache.accumulo.test.TotalQueuedIT
- [ ] org.apache.accumulo.test.TransportCachingIT
- [ ] org.apache.accumulo.test.UniqueNameAllocatorIT
- [ ] org.apache.accumulo.test.UnusedWALIT
- [ ] org.apache.accumulo.test.upgrade.ScanServerUpgrade11to12TestIT
- [ ] org.apache.accumulo.test.upgrade.UpgradeIT
- [ ] org.apache.accumulo.test.upgrade.UpgradeUtilIT
- [ ] org.apache.accumulo.test.UsersIT
- [ ] org.apache.accumulo.test.VerifySerialRecoveryIT
- [ ] org.apache.accumulo.test.VolumeChooserIT
- [ ] org.apache.accumulo.test.VolumeFlakyAmpleIT
- [ ] org.apache.accumulo.test.VolumeIT
- [ ] org.apache.accumulo.test.VolumeManagerIT
- [ ] org.apache.accumulo.test.WaitForBalanceIT
- [ ] org.apache.accumulo.test.WriteAfterCloseIT
- [ ] org.apache.accumulo.test.YieldScannersIT
- [ ] org.apache.accumulo.test.ZombieScanIT
- [ ] org.apache.accumulo.test.ZooKeeperPropertiesIT_SimpleSuite

---

**Total Tests:** 293
**Completed:** 0
**Remaining:** 293
**Progress:** 0%
