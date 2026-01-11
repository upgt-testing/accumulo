# BUG Report: MiniAccumuloClusterImpl.getProcesses() Missing SCAN_SERVER and COMPACTOR

## Summary

`MiniAccumuloClusterImpl.getProcesses()` is incomplete and does not return SCAN_SERVER, COMPACTOR, COMPACTION_COORDINATOR, or MONITOR processes, even though these process types are fully supported by the cluster and are tracked by `MiniAccumuloClusterControl`.

## Classification

**Type**: BUG (Source Code Bug)

## Impact

- Any code that uses `MiniAccumuloClusterImpl.getProcesses()` to enumerate running processes will miss scan servers and compactors
- This affects test frameworks, monitoring tools, and any utilities that need to discover all running processes in a MiniAccumuloCluster

## Root Cause

The `getProcesses()` method in `MiniAccumuloClusterImpl` was likely written before SCAN_SERVER and COMPACTOR support was added, and was never updated to include these newer process types.

### Buggy Code Location

**File**: `minicluster/src/main/java/org/apache/accumulo/miniclusterImpl/MiniAccumuloClusterImpl.java`

**Lines**: 779-792

```java
public Map<ServerType,Collection<ProcessReference>> getProcesses() {
  Map<ServerType,Collection<ProcessReference>> result = new HashMap<>();
  MiniAccumuloClusterControl control = getClusterControl();
  result.put(ServerType.MANAGER, references(control.managerProcess));
  result.put(ServerType.TABLET_SERVER,
      references(control.tabletServerProcesses.toArray(new Process[0])));
  if (control.zooKeeperProcess != null) {
    result.put(ServerType.ZOOKEEPER, references(control.zooKeeperProcess));
  }
  if (control.gcProcess != null) {
    result.put(ServerType.GARBAGE_COLLECTOR, references(control.gcProcess));
  }
  return result;  // MISSING: SCAN_SERVER, COMPACTOR, COMPACTION_COORDINATOR, MONITOR
}
```

### Evidence

The `MiniAccumuloClusterControl` class DOES track these processes and has a method `getProcesses(ServerType type)` that returns them correctly:

**File**: `minicluster/src/main/java/org/apache/accumulo/miniclusterImpl/MiniAccumuloClusterControl.java`

**Lines**: 602-624

```java
@SuppressWarnings("removal")
public Set<Process> getProcesses(ServerType type) {
  switch (type) {
    case COMPACTION_COORDINATOR:
      return coordinatorProcess == null ? Set.of() : Set.of(coordinatorProcess);
    case COMPACTOR:
      return Set.copyOf(compactorProcesses);
    case GARBAGE_COLLECTOR:
      return gcProcess == null ? Set.of() : Set.of(gcProcess);
    case MANAGER:
    case MASTER:
      return managerProcess == null ? Set.of() : Set.of(managerProcess);
    case MONITOR:
      return monitor == null ? Set.of() : Set.of(monitor);
    case SCAN_SERVER:
      return Set.copyOf(scanServerProcesses);  // <-- This works!
    case TABLET_SERVER:
      return Set.copyOf(tabletServerProcesses);
    case ZOOKEEPER:
      return zooKeeperProcess == null ? Set.of() : Set.of(zooKeeperProcess);
    default:
      throw new IllegalArgumentException("Unhandled type: " + type);
  }
}
```

The `MiniAccumuloClusterControl` tracks scan servers in `scanServerProcesses` (line 73) and compactors in `compactorProcesses` (line 74).

## Reproduction

1. Start a MiniAccumuloCluster with `setNumScanServers(0)`
2. Manually start a scan server using `getCluster().getClusterControl().start(ServerType.SCAN_SERVER, ...)`
3. Call `cluster.getProcesses().get(ServerType.SCAN_SERVER)`
4. Result: `null` (even though the scan server is running and registered)

## Proposed Fix

Update `MiniAccumuloClusterImpl.getProcesses()` to include all process types:

```java
public Map<ServerType,Collection<ProcessReference>> getProcesses() {
  Map<ServerType,Collection<ProcessReference>> result = new HashMap<>();
  MiniAccumuloClusterControl control = getClusterControl();

  result.put(ServerType.MANAGER, references(control.managerProcess));
  result.put(ServerType.TABLET_SERVER,
      references(control.tabletServerProcesses.toArray(new Process[0])));

  if (control.zooKeeperProcess != null) {
    result.put(ServerType.ZOOKEEPER, references(control.zooKeeperProcess));
  }
  if (control.gcProcess != null) {
    result.put(ServerType.GARBAGE_COLLECTOR, references(control.gcProcess));
  }

  // Add scan server processes
  synchronized (control.scanServerProcesses) {
    if (!control.scanServerProcesses.isEmpty()) {
      result.put(ServerType.SCAN_SERVER,
          references(control.scanServerProcesses.toArray(new Process[0])));
    }
  }

  // Add compactor processes
  synchronized (control.compactorProcesses) {
    if (!control.compactorProcesses.isEmpty()) {
      result.put(ServerType.COMPACTOR,
          references(control.compactorProcesses.toArray(new Process[0])));
    }
  }

  // Add coordinator process
  if (control.coordinatorProcess != null) {
    result.put(ServerType.COMPACTION_COORDINATOR, references(control.coordinatorProcess));
  }

  // Add monitor process
  if (control.monitor != null) {
    result.put(ServerType.MONITOR, references(control.monitor));
  }

  return result;
}
```

## Failed Test Executions

This bug causes failures in 46 test executions across multiple tests:

| Test Class | Test Method | Position | Target |
|------------|-------------|----------|--------|
| ScanServerGroupConfigurationIT | testClientConfiguration | after_default_scan_server_start | scan_server |
| ScanServerGroupConfigurationIT | testClientConfiguration | after_group1_scan_server_start | scan_server |
| HalfDeadTServerIT | testRecover | after_kill_regular_tserver | tablet_server |
| IdleProcessMetricsIT | testIdleStopMetrics | after_start_compactors | compactor |
| IdleProcessMetricsIT | idleCompactorTest | after_start_coordinator_and_compactors | compactor |
| IdleProcessMetricsIT | idleScanServerTest | after_start_scan_server | scan_server |
| ScanServerIT | testBatchScannerTimeout | after_first_scan | scan_server |
| ... and 39 more executions | | | |

## Stack Trace

```
java.lang.IllegalArgumentException: No processes found for role: scan_server
    at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:87)
    at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.restartNode(AccumuloClusterImplAdapter.java:50)
    at org.restarttest.core.RestartExecutor.restartSingleNode(RestartExecutor.java:190)
    ...
```

## Related Files

- `minicluster/src/main/java/org/apache/accumulo/miniclusterImpl/MiniAccumuloClusterImpl.java:779-792`
- `minicluster/src/main/java/org/apache/accumulo/miniclusterImpl/MiniAccumuloClusterControl.java:602-624`
- `minicluster/src/main/java/org/apache/accumulo/miniclusterImpl/MiniAccumuloClusterControl.java:73-74` (process lists)
