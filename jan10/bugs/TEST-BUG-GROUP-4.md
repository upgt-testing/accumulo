# TEST-BUG Report: Group 4 - Wait.waitFor Timeout

## Summary

The test `ExternalCompactionProgressIT.testCompactionDurationContinuesAfterCoordinatorStop` contains an inverted condition in `Wait.waitFor()` that causes timeouts when restart injection delays test execution. The condition waits for compactions to be EMPTY instead of waiting for compactions to EXIST.

## Failure Details

**Exception Type**: `java.lang.IllegalStateException`

**Error Message**:
```
Compaction did not start within the expected time. Timeout exceeded
```

**Affected Tests** (8 total in this group, analyzed case):
- `ExternalCompactionProgressIT_RestartInjected.testCompactionDurationContinuesAfterCoordinatorStop`

**Failure Count**: 8 test executions across multiple tests

## Root Cause Analysis

### The Buggy Code

**File**: `test/src/main/java/org/apache/accumulo/test/compaction/ExternalCompactionProgressIT.java`

**Lines**: 146-151

```java
      // Wait until the compaction starts
      Wait.waitFor(() -> {
        Map<String,TExternalCompaction> compactions =
            getRunningCompactions(getCluster().getServerContext()).getCompactions();
        return compactions == null || compactions.isEmpty();  // <-- BUG: Inverted condition
      }, 30_000, 100, "Compaction did not start within the expected time");
```

### What's Wrong

The comment says "Wait until the compaction starts", but the condition returns `true` when compactions are **null or empty** (i.e., when NO compactions are running). This is backwards - the condition should return `true` when compactions ARE running.

**How `Wait.waitFor()` works** (from `test/src/main/java/org/apache/accumulo/test/util/Wait.java`):
- Returns when the condition evaluates to `true`
- Throws `IllegalStateException` when timeout exceeded (condition never became `true`)

**Current buggy behavior**:
- `compactions == null || compactions.isEmpty()` → returns `true` when NO compactions
- Condition returns `true` immediately if checked before compaction starts
- Test "passes" incorrectly by exiting Wait early

**Expected behavior**:
- Wait should return when there ARE running compactions
- Condition should be: `compactions != null && !compactions.isEmpty()`

### Why Restart Injection Exposes This Bug

Without restart injection:
1. `compact()` is called with `wait=false` (non-blocking)
2. `Wait.waitFor()` is called immediately
3. Compaction hasn't started yet → compactions list is empty
4. Condition `isEmpty()` returns `true` → Wait exits immediately
5. Test continues (incorrectly assuming compaction started)

With restart injection at `after_compact_start_duration_1`:
1. `compact()` is called
2. Restart delays test execution by several seconds
3. During delay, the compaction actually starts (compactor picks it up)
4. When `Wait.waitFor()` runs, compactions list is NOT empty
5. Condition `isEmpty()` returns `false` → Wait keeps looping
6. Eventually timeout → `IllegalStateException` thrown

The restart injection exposes a latent race condition in the test code.

### Why This is a TEST-BUG (Not a Source Code Bug)

1. `ExternalCompactionProgressIT.java` is in the test module (`test/src/main/java/...`), not production code
2. The Accumulo compaction system is working correctly - compactions do start
3. The bug is in the test's wait condition logic, not in Accumulo's source code
4. The test would pass incorrectly without restart injection due to timing

## Suggested Fix

```java
      // Wait until the compaction starts
      Wait.waitFor(() -> {
        Map<String,TExternalCompaction> compactions =
            getRunningCompactions(getCluster().getServerContext()).getCompactions();
        return compactions != null && !compactions.isEmpty();  // FIX: Correct condition
      }, 30_000, 100, "Compaction did not start within the expected time");
```

## Reproduction Steps

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
cd /home/shuai/xlab/restart_testing/accumulo
mvn -pl test failsafe:integration-test \
  -Dit.test=ExternalCompactionProgressIT_RestartInjected#testCompactionDurationContinuesAfterCoordinatorStop \
  -Drestart.position=after_compact_start_duration_1 \
  -Drestart.target=tablet_server \
  -Drestart.mode=GRACEFUL \
  -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar
```

## Stack Trace

```
java.lang.IllegalStateException: Compaction did not start within the expected time. Timeout exceeded
    at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:125)
    at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:89)
    at org.apache.accumulo.test.util.Wait.waitFor(Wait.java:76)
    at org.apache.accumulo.test.compaction.ExternalCompactionProgressIT_RestartInjected.testCompactionDurationContinuesAfterCoordinatorStop(ExternalCompactionProgressIT_RestartInjected.java:158)
```

## Additional Notes

Group 4 contains 8 different test failures all involving `Wait.waitFor()` timeouts:
1. ShutdownIT_RestartInjected.adminStop (could not reproduce)
2. ExternalCompactionProgressIT_RestartInjected.testCompactionDurationContinuesAfterCoordinatorStop (analyzed - TEST-BUG)
3. GarbageCollectorTrashDefaultIT_RestartInjected.testTrashHadoopDisabledAccumuloEnabled
4. HalfClosedTabletIT_RestartInjected.testBadIteratorOnStack
5. ManagerAssignmentIT_RestartInjected.testShutdownOnlyTServerWithUserTable (2 positions)
6. ZombieScanIT_RestartInjected.testMetrics (2 positions)

The other failures in this group may have different root causes (could be test bugs, flaky tests, or legitimate timing issues). This report focuses on the reproducible case where a clear test bug was identified.

## Classification

- **Type**: TEST-BUG
- **Severity**: Medium
- **Component**: Test Code
- **File**: `test/src/main/java/org/apache/accumulo/test/compaction/ExternalCompactionProgressIT.java`
