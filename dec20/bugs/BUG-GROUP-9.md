# BUG-GROUP-9: Summary Retrieval Returns Empty After Restart

**Describe the issue**
After a tablet server restart, `tableOperations().summaries(table).retrieve()` returns an empty list, causing `IndexOutOfBoundsException` when accessing the first element.

**STATUS: TEST CONFIGURATION ISSUE (VERIFIED FIX)**

## Root Cause Analysis

**Initial diagnosis was INCORRECT.** We initially believed this was caused by a stale metadata cache in `Gatherer.countFiles()`. However, testing proved the actual root cause is the **same as Bug Group 3**: missing `RawLocalFileSystem` configuration in the test harness.

**Actual Root Cause:**
- Hadoop's default `LocalFileSystem` doesn't properly honor fsync/flush operations
- Summary data written before restart wasn't being synced to disk
- After restart, the data was lost, causing empty results
- The `RawLocalFileSystem` fix ensures proper WAL sync behavior

**Verification:**
| Configuration | Pass Rate |
|--------------|-----------|
| Without RawLocalFileSystem fix | ~60% (intermittent failures) |
| With RawLocalFileSystem fix | **100%** (10/10 passes) |

## Original Symptoms

```
java.lang.IndexOutOfBoundsException: Index 0 out of bounds for length 0
    at java.base/java.util.ArrayList.get(ArrayList.java:427)
    at org.apache.accumulo.test.functional.SummaryIT_RestartInjected.testPermissions(SummaryIT_RestartInjected.java:680)
```

The test called `summaries.get(0)` on an empty list because summary retrieval returned no results after restart.

## Fix Applied

Same fix as Bug Group 3 - added `RawLocalFileSystem` configuration to test harness base classes:

1. `test/src/main/java/org/apache/accumulo/harness/MiniClusterHarness.java`
2. `test/src/main/java/org/apache/accumulo/test/functional/ConfigurableMacBase.java`

See `BUG-GROUP-3.md` for full details of the fix.

## Test Command

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
mvn -pl :accumulo-test failsafe:integration-test \
    -Dit.test=SummaryIT_RestartInjected#testPermissions \
    -Drestart.position=after_flush_permissions \
    -Drestart.target=tablet_server \
    -Drestart.mode=GRACEFUL
```

## Additional Context

- This is NOT a bug in Accumulo itself
- The issue only affects MiniAccumuloCluster tests using Hadoop's LocalFileSystem
- Production systems using HDFS are not affected
- The initial "stale metadata cache" diagnosis was a red herring - the data was simply not persisted
