# FP-GROUP-7: ThriftTableOperationException - Compaction Conflict

## Classification: FALSE POSITIVE

## Summary

This failure is a false positive because the error message is accurate and the behavior is intentional. The restart injection exposes a timing window that wouldn't normally be hit, but the underlying behavior (rejecting a new compaction while an existing FATE transaction with custom config is still running) is by design.

## Why This Is Not A Bug

### 1. The Error Message Is Accurate

The error "Another compaction with iterators and/or a compaction strategy is running" is technically correct. When the test calls:

```java
client.tableOperations().compact("dut2", new CompactionConfig()
    .setWait(false)
    .setExecutionHints(Map.of("compaction_type", "special")));
```

A FATE transaction is created that stores the compaction config in ZooKeeper. This FATE transaction IS still running when the test later tries to start a new compaction. The transaction is in its `isReady()` loop, waiting for tablets to update their metadata.

### 2. Asynchronous Cancellation Is Documented Behavior

The `cancelCompaction()` javadoc states:

> "Cancels a user initiated major compaction... Compactions of tablets that are currently running may finish, but **new compactions of tablets will not start**."

This describes the tablet-level behavior, not the FATE transaction lifecycle. The cancellation:
- Sets a cancel ID in ZooKeeper
- Does NOT wait for the original FATE transaction to complete
- Does NOT immediately clean up the compaction config from ZooKeeper

### 3. Intentional Design Decision

Accumulo deliberately prevents multiple user compactions with custom configurations (iterators, hints, selectors, configurers) on the same table simultaneously. This prevents potential conflicts where two compactions might try to apply different transformations to the same data.

### 4. The Restart Exposes An Unlikely Timing Window

Without the restart injection:
- The tablet-level compaction completes quickly
- The FATE transaction's `isReady()` sees tablets have updated compactId
- `call()` runs, cleaning up the config from ZooKeeper
- By the time cancel is called, the FATE transaction may already be completing

With restart injection:
- The tablet server restart causes delays
- The FATE transaction takes longer to complete
- A race condition becomes visible where:
  1. Cancel sets the cancel ID
  2. Original FATE transaction hasn't noticed the cancel yet
  3. New compaction sees the old config and fails

## Test Flow Analysis

```java
// Start async compaction with execution hints
client.tableOperations().compact("dut2", new CompactionConfig()
    .setWait(false)
    .setExecutionHints(Map.of("compaction_type", "special")));

// RESTART tablet server here - causes delays

// Wait for files to converge (compaction work completes at tablet level)
while (getFiles(client, "dut2").size() > 3) {
    Thread.sleep(100);
}

// Cancel compaction - sets cancel ID, returns immediately
client.tableOperations().cancelCompaction("dut2");

// Immediately start new compaction - FAILS because old FATE transaction
// still has config in ZooKeeper
client.tableOperations().compact("dut2",
    new CompactionConfig().setWait(true).setExecutionHints(Map.of("compact_all", "true")));
```

## Why The Restart Position Is Problematic

The restart at position `after_compact_dispatch_user` occurs right after async compactions are started. This:
1. Disrupts the tablet server while compaction is in progress
2. Causes the FATE transaction to wait longer for tablets to report completion
3. Creates a larger window for the race condition

## Proper Test Behavior

If the test needs to be robust against this timing, it should:

1. **Add a wait after cancel**:
   ```java
   client.tableOperations().cancelCompaction("dut2");
   Thread.sleep(5000);  // Wait for FATE cleanup
   client.tableOperations().compact("dut2", ...);
   ```

2. **Or retry with backoff**:
   ```java
   client.tableOperations().cancelCompaction("dut2");
   while (true) {
       try {
           client.tableOperations().compact("dut2", ...);
           break;
       } catch (AccumuloException e) {
           if (e.getMessage().contains("Another compaction")) {
               Thread.sleep(1000);
               continue;
           }
           throw e;
       }
   }
   ```

## Conclusion

This is a **false positive** because:
1. The error message accurately reflects the system state
2. The behavior is intentional and documented (implicitly)
3. The restart injection creates an abnormal timing scenario
4. The test makes an incorrect assumption about `cancelCompaction()` being synchronous

The restart at this position is not appropriate for testing because it disrupts an in-flight operation and exposes internal timing that wouldn't normally be visible to users.
