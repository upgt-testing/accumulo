# BUG-GROUP-3: NullPointerException in MiniAccumuloClusterImpl.getProcesses()

## Summary

`MiniAccumuloClusterImpl.getProcesses()` throws `NullPointerException` when `managerProcess` is null due to missing null check, while other similar fields (`zooKeeperProcess`, `gcProcess`) have proper null checks.

## Affected Component

- **File**: `minicluster/src/main/java/org/apache/accumulo/miniclusterImpl/MiniAccumuloClusterImpl.java`
- **Method**: `getProcesses()` (lines 779-792)
- **Related Class**: `ProcessReference.java` (line 30)

## Root Cause Analysis

The `getProcesses()` method has inconsistent null handling for process references:

```java
public Map<ServerType,Collection<ProcessReference>> getProcesses() {
    Map<ServerType,Collection<ProcessReference>> result = new HashMap<>();
    MiniAccumuloClusterControl control = getClusterControl();
    result.put(ServerType.MANAGER, references(control.managerProcess));  // BUG: No null check!
    result.put(ServerType.TABLET_SERVER,
        references(control.tabletServerProcesses.toArray(new Process[0])));
    if (control.zooKeeperProcess != null) {  // Has null check
      result.put(ServerType.ZOOKEEPER, references(control.zooKeeperProcess));
    }
    if (control.gcProcess != null) {  // Has null check
      result.put(ServerType.GARBAGE_COLLECTOR, references(control.gcProcess));
    }
    return result;
}
```

When `managerProcess` is null (e.g., before cluster starts or after manager is stopped), it gets passed to `references()` which creates a `ProcessReference`:

```java
List<ProcessReference> references(Process... procs) {
    return Stream.of(procs).map(ProcessReference::new).collect(toList());
}
```

The `ProcessReference` constructor enforces non-null:

```java
ProcessReference(Process process) {
    this.process = Objects.requireNonNull(process);  // Throws NPE if null
}
```

## Stack Trace

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
```

## Affected Tests (8 failures)

1. `CleanShutdownMacTest_RestartInjected.testExecutorServiceShutdown` - position: after_cluster_create
2. `UpgradeUtilIT_RestartInjected.testPrepareFailsDueToFateTransactions` - position: after_manager_stop
3. `UpgradeUtilIT_RestartInjected.testPrepareSucceeds` - position: after_manager_stopped
4. `VolumeIT_RestartInjected.testRemoveVolumes` - position: after_stopall
5. `VolumeIT_RestartInjected.testNonConfiguredVolumes` - position: after_verify_and_shutdown
6. `VolumeIT_RestartInjected.testNonConfiguredVolumes` - position: after_initialize_volumes
7. `VolumeIT_RestartInjected.testAddVolumes` - position: after_verify_and_shutdown
8. `VolumeIT_RestartInjected.testAddVolumes` - position: after_initialize_volumes

All failures occur when `getProcesses()` is called and the manager process is null.

## Proposed Fix

Add null check for `managerProcess` consistent with handling of `zooKeeperProcess` and `gcProcess`:

```java
public Map<ServerType,Collection<ProcessReference>> getProcesses() {
    Map<ServerType,Collection<ProcessReference>> result = new HashMap<>();
    MiniAccumuloClusterControl control = getClusterControl();
    if (control.managerProcess != null) {  // ADD NULL CHECK
      result.put(ServerType.MANAGER, references(control.managerProcess));
    }
    result.put(ServerType.TABLET_SERVER,
        references(control.tabletServerProcesses.toArray(new Process[0])));
    if (control.zooKeeperProcess != null) {
      result.put(ServerType.ZOOKEEPER, references(control.zooKeeperProcess));
    }
    if (control.gcProcess != null) {
      result.put(ServerType.GARBAGE_COLLECTOR, references(control.gcProcess));
    }
    return result;
}
```

## Severity

**Medium** - This bug causes NPE when querying processes before cluster start or after manager shutdown. It affects the robustness of the MiniAccumuloCluster API.

## Reproduction Command

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
mvn -pl minicluster failsafe:integration-test \
    -Dit.test=CleanShutdownMacTest_RestartInjected#testExecutorServiceShutdown \
    -Drestart.position=after_cluster_create \
    -Drestart.target=manager \
    -Drestart.mode=GRACEFUL \
    -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar
```
