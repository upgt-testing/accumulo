# FP-GROUP-2: Timeout Waiting for Tablet Servers

## Classification: FALSE POSITIVE

## Summary

This failure group represents 23 test failures caused by the restart adapter's `waitForTabletServers()` method timing out. The timeout occurs because the adapter incorrectly expects the configured number of tablet servers to always be running, even when tests intentionally kill servers as part of their test logic.

## Error Details

**Exception Type:** `java.lang.Exception: Timeout waiting for tablet servers to register`

**Stack Trace:**
```
org.restarttest.core.RestartException: Restart failed at position after_tserver_killed
Caused by: java.lang.Exception: Timeout waiting for tablet servers to register
    at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitForTabletServers(AccumuloClusterImplAdapter.java:322)
    at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitActive(AccumuloClusterImplAdapter.java:168)
    at org.apache.accumulo.restarttest.AccumuloClusterImplAdapter.waitActive(AccumuloClusterImplAdapter.java:50)
    at org.restarttest.core.RestartExecutor.executeRestart(RestartExecutor.java:116)
    ...
```

**Observed Error:**
```
DEBUG: TIMEOUT - Expected 3 tablet servers but only 2 registered after 60000ms
```

## Root Cause Analysis

### The Restart Adapter Logic

The restart adapter's `waitForTabletServers()` method in `AccumuloClusterImplAdapter.java` uses the **configured** number of tablet servers to determine how many to wait for:

```java
// AccumuloClusterImplAdapter.java:295-314
private void waitForTabletServers(MiniAccumuloClusterImpl cluster, AccumuloClient client,
                                  long timeoutMs) throws Exception {
  int expected = cluster.getConfig().getNumTservers(); // Gets 3 (configured)
  log.info("Waiting for {} tablet servers to register", expected);

  long start = System.currentTimeMillis();
  while (System.currentTimeMillis() - start < timeoutMs) {
    try {
      List<String> registered = client.instanceOperations().getTabletServers();
      if (registered.size() >= expected) {
        log.info("{} tablet servers registered: {}", registered.size(), registered);
        return;
      }
      log.debug("Waiting for tablet servers: {}/{}", registered.size(), expected);
    } catch (Exception e) {
      // Not ready yet
    }
    Thread.sleep(100);
  }
  throw new Exception("Timeout waiting for tablet servers to register");
}
```

### Test Context Example: TabletMetadataIT_RestartInjected.getLiveTServersTest

The test is configured with `NUM_TSERVERS = 3` and intentionally kills a tablet server:

```java
// TabletMetadataIT_RestartInjected.java:60-88
public void getLiveTServersTest() throws Exception {
  try (AccumuloClient c = Accumulo.newClient().from(getClientProperties()).build()) {
    // Wait for all 3 tservers to start
    while (c.instanceOperations().getTabletServers().size() != NUM_TSERVERS) {
      log.info("Waiting for tservers to start up...");
      sleepUninterruptibly(5, TimeUnit.SECONDS);
    }

    // ... test logic ...

    // Kill a tserver as part of the test logic
    getCluster().killProcess(TABLET_SERVER,
        getCluster().getProcesses().get(TABLET_SERVER).iterator().next());

    // Wait for tserver count to drop
    while (c.instanceOperations().getTabletServers().size() == NUM_TSERVERS) {
      log.info("Waiting for a tserver to die...");
      sleepUninterruptibly(5, TimeUnit.SECONDS);
    }

    // Restart point is AFTER the tserver is killed (only 2 tservers running)
    RestartFramework.at("after_tserver_killed").on(getCluster()).restart("manager")
        .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

    // Test expects only 2 tservers
    servers = TabletMetadata.getLiveTServers((ClientContext) c);
    assertEquals(NUM_TSERVERS - 1, servers.size());
  }
}
```

### The Problem

1. Test is configured with 3 tablet servers
2. Test intentionally kills 1 tablet server as part of its test logic
3. The restart position `after_tserver_killed` is placed AFTER the kill
4. At this position, only 2 tablet servers are running (by design)
5. When the restart adapter restarts the manager and calls `waitActive()`:
   - It reads `cluster.getConfig().getNumTservers()` which returns **3**
   - But only **2** tablet servers are actually running
   - After 60 seconds, it times out

## Why This is a False Positive

1. **Not a Bug in Accumulo Source Code**: The exception originates entirely from the restart adapter code (`AccumuloClusterImplAdapter.java`), not from Accumulo's core or test code.

2. **Test Logic is Correct**: The test intentionally kills a tablet server to verify that `getLiveTServers()` correctly reports the reduced count. The test expects only 2 tablet servers after the kill.

3. **Restart Adapter Limitation**: The adapter assumes the cluster should always have the originally configured number of servers, which is incorrect for tests that intentionally modify cluster state.

4. **Improper Restart Position**: The restart position `after_tserver_killed` is placed at a point where the cluster state intentionally differs from the initial configuration.

## Common Patterns in All 23 Failures

All failures share a common pattern:
- Tests that intentionally kill or stop servers as part of their test logic
- Restart positions placed after such intentional modifications
- The restart adapter expecting the original server count

Example test positions:
- `after_tserver_killed` - After a tablet server is killed
- `after_stopall` - After cluster.stop() is called
- `after_kill_tservers` - After multiple tservers are killed
- `after_suspended_verified` - After verifying suspended state

## Recommendation

This is a **framework limitation**, not a bug in Accumulo. Potential fixes:
1. The restart adapter should track actual running processes instead of configured count
2. Skip `waitForTabletServers()` when restarting after intentional server kills
3. These restart positions should be excluded from restart injection testing

## Affected Tests (23 total)

1. TabletMetadataIT_RestartInjected.getLiveTServersTest
2. ManagerAssignmentIT_RestartInjected.testShutdownOnlyTServerWithoutUserTable
3. ExternalCompaction_1_IT_RestartInjected.testExternalCompactionDeadTServer
4. ShutdownIT_RestartInjected.stopDuringStart
5. SslIT_RestartInjected.adminStop
6. SuspendedTabletsIT_RestartInjected.crashAndOffline
7. SuspendedTabletsIT_RestartInjected.crashAndResumeTserver
8. SuspendedTabletsIT_RestartInjected.shutdownAndOffline
9. SuspendedTabletsIT_RestartInjected.shutdownAndResumeTserver
10. ShutdownIT_RestartInjected.shutdownDuringDelete
11. ShutdownIT_RestartInjected.shutdownDuringIngest
12. ShutdownIT_RestartInjected.shutdownDuringDeleteTable
13. ShutdownIT_RestartInjected.shutdownDuringQuery
14. And others with similar patterns...

## Reproduction Steps

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

mvn -pl test failsafe:integration-test \
  -Dit.test=TabletMetadataIT_RestartInjected#getLiveTServersTest \
  -Drestart.position=after_tserver_killed \
  -Drestart.target=manager \
  -Drestart.mode=GRACEFUL \
  -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar
```

## Fix Applied

The restart adapter (`AccumuloClusterImplAdapter.java`) was updated to capture the actual tablet server count **before** the restart and use that as the expected count:

1. Added a field to track expected count:
```java
private int expectedTabletServerCount = -1;
```

2. Capture count before restart in `restartNode()`:
```java
Collection<ProcessReference> tserverProcesses = cluster.getProcesses().get(ServerType.TABLET_SERVER);
expectedTabletServerCount = (tserverProcesses != null) ? tserverProcesses.size() : 0;
log.info("Captured current tablet server process count before restart: {}", expectedTabletServerCount);
```

3. Use captured count in `waitForTabletServers()`:
```java
int expected = expectedTabletServerCount > 0
    ? expectedTabletServerCount
    : cluster.getConfig().getNumTservers();
```

After this fix, the test `TabletMetadataIT_RestartInjected.getLiveTServersTest` passes successfully.

## Conclusion

This is a **FALSE POSITIVE**. The failures are caused by the restart adapter's incorrect assumption that all configured servers should always be running. Tests that intentionally kill servers will trigger this timeout when restart injection happens at positions where the server count is reduced. This is a limitation of the restart testing framework, not a bug in Accumulo.

**Status: FIXED** - The restart adapter now correctly tracks the actual tablet server count.
