# Mini Cluster Test Usage Analysis

## Summary of Mini Cluster Test Usage

Analysis of test classes using different mini cluster patterns in the Accumulo codebase.

### SharedMiniClusterBase: 86 test classes

**Most widely used pattern for sharing clusters across tests**

Used extensively across functional, compaction, fate, shell, and scan server tests.

**Examples include:**
- Most scan server tests (ScanServerIT, ScanServerMultipleScansIT, ScanServerShutdownIT, etc.)
- Shell tests (ShellIT, ShellServerIT, ShellCreateTableIT, ShellCreateNamespaceIT, etc.)
- Fate tests (FateITBase, FateStoreITBase, FateOpsCommandsITBase, FateExecutionOrderITBase, etc.)
- Compaction tests (ExternalCompaction2ITBase, CompactionExecutorIT, ExternalCompactionMetricsIT, etc.)
- Functional tests (MetadataIT, MemoryStarvedScanIT, TabletMergeabilityIT, etc.)

### ConfigurableMacBase: 61 test classes

**Second most common pattern for configurable mini clusters**

Used for tests requiring custom cluster configurations.

**Examples include:**
- Volume tests (VolumeITBase, VolumeChooserIT, VolumeManagerIT)
- WAL/recovery tests (GarbageCollectWALIT, VerifySerialRecoveryIT, MissingWalHeaderCompletesRecoveryIT, etc.)
- Balance and tablet server tests (BalanceIT, HalfDeadTServerIT, BalanceWithOfflineTableIT, etc.)
- SSL/security tests (SslIT, MonitorSslIT, AuditMessageIT)
- Metadata tests (MetadataSplitIT, MetadataMaxFilesIT)

### Direct MiniAccumuloCluster usage: ~37 test files

**Tests that directly reference MiniAccumuloCluster (not through base classes)**

Includes both tests in `/test/src/main/java` and `/minicluster/src/test/java`.

**Examples include:**
- MiniCluster's own tests (MiniAccumuloClusterTest, MiniAccumuloClusterStartStopTest, MiniAccumuloClusterExistingZooKeepersTest, etc.)
- Some functional tests (KerberosIT, GarbageCollectorIT, ShutdownIT, ExitCodesIT, etc.)
- MapReduce integration tests (MapReduceIT, RowHashIT)

## Total Impact

**Approximately 147 test classes** use one of these mini cluster patterns (86 + 61), making this a significant transformation effort.

**Note:** There may be some overlap between tests that extend a base class but also directly reference MiniAccumuloCluster.

## Recommendations for Transformation

1. **Start with SharedMiniClusterBase tests (86)** as they're the largest group
2. **Then tackle ConfigurableMacBase tests (61)**
3. **Finally handle direct MiniAccumuloCluster usage cases (37)**
4. **Consider creating an upgradable version of each base class** to minimize code changes across all tests

## Analysis Date

Generated: 2025-11-24
