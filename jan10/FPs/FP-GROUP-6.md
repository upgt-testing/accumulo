# False Positive Report - Group 6: FunctionalTestUtils.checkRFiles - Missing Map Files

## Classification: FALSE POSITIVE (FP)

## Summary
The failures in Group 6 are caused by restart-induced state changes that violate test assumptions, not by bugs in Accumulo source code.

## Test Cases Affected

### 1. BadIteratorMincIT_RestartInjected.test
- **Position**: `after_first_flush`
- **Target**: `tablet_server`
- **Mode**: `GRACEFUL`
- **Error**: `java.lang.Exception: tablet 1< has 0 map files` at line 93

### 2. HalfClosedTablet2IT_RestartInjected.testInvalidContextCausesVolumeChooserFailure
- **Position**: `after_batch_write`
- **Target**: `tablet_server`
- **Mode**: `GRACEFUL`
- **Error**: `java.lang.Exception: tablet 1< has 1 map files` at line 110 (reproduced)

## Root Cause Analysis

### BadIteratorMincIT Test Flow
1. Create table with BadIterator attached for minc scope
2. Write 1 row of data
3. Call `flush(tableName, null, null, false)` - async flush
4. **RESTART at after_first_flush** - tablet_server graceful restart
5. Sleep 1 second
6. `checkRFiles(c, tableName, 1, 1, 0, 0)` - expects 0 files (passes because minc failed due to BadIterator)
7. Remove BadIterator
8. Sleep 5 seconds
9. `checkRFiles(c, tableName, 1, 1, 1, 1)` - **FAILS with 0 files** (expects 1)

**Why this is an FP:**
- The async `flush()` call at line 71 triggers a minc request that the tablet server remembers
- The BadIterator causes minc to fail, but the tablet server should retry periodically
- After the graceful restart, the tablet server loses the pending minc state
- When the BadIterator is removed, there's no pending minc to retry
- The small data volume (1 row) doesn't trigger automatic minc based on memstore thresholds
- The test doesn't call `flush()` again after removing the bad iterator
- This is a **test methodology issue**: the test assumes minc state persists across restarts

### HalfClosedTablet2IT Test Flow
1. Create table
2. Write 2 rows of data
3. **RESTART at after_batch_write** - tablet_server graceful restart
4. Set invalid ClassLoader context on table
5. Sleep 3 seconds
6. Call `flush(tableName)`
7. `checkRFiles(client, tableName, 1, 1, 0, 0)` - **FAILS with 1 file** (expects 0)

**Why this is an FP:**
- The restart happens at `after_batch_write` before the invalid context is set
- During graceful shutdown, the tablet server flushes all in-memory data to RFiles
- By the time the invalid context is set, the data has already been flushed
- The subsequent flush() call has nothing to flush
- The test expects 0 files (assuming minc would fail due to invalid context), but there's already 1 file from the graceful shutdown
- This is **expected behavior** for graceful restarts - they should flush data before shutting down

## Reproduction Results

### HalfClosedTablet2IT (Successfully Reproduced)
```
java.lang.Exception: tablet 1< has 1 map files
    at org.apache.accumulo.test.functional.FunctionalTestUtils.checkRFiles(FunctionalTestUtils.java:137)
    at org.apache.accumulo.test.functional.HalfClosedTablet2IT_RestartInjected.testInvalidContextCausesVolumeChooserFailure(HalfClosedTablet2IT_RestartInjected.java:110)
```

### BadIteratorMincIT (Test timeout during reproduction)
- The test timed out during the `waitForBalance` phase of the restart
- The original cloudlab failure shows the test completing but with wrong RFile count
- Analysis based on test logic and original failure pattern

## Why Not a Bug

### Graceful Restart Behavior is Correct
1. **Data Safety**: Graceful restarts are designed to flush data before shutdown - this is correct behavior
2. **State Not Persistent**: The minc retry state is not designed to survive restarts
3. **WAL Recovery**: Data is recovered from WAL after restart, but minc scheduling is based on current state

### Test Assumptions Violated
1. **BadIteratorMincIT**: Assumes minc will automatically retry after bad iterator removal, but doesn't account for restart clearing the minc queue
2. **HalfClosedTablet2IT**: Assumes data is still in memory after batch write, but graceful restart flushes it

## Conclusion

Both failures are caused by the restart injection affecting test-specific timing and state assumptions:
- The restarts change the tablet server's internal state in expected ways
- The tests make assumptions about state that don't hold after restart
- These are not bugs in Accumulo but rather test methodology issues with restart injection

The test code would need to be modified to account for state changes during restart to properly test these scenarios.
