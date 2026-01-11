# FP-GROUP-8: ZooKeeper NoNodeException in BackupManagerIT

## Classification: FALSE POSITIVE

## Summary
The failure occurs because the restart position "after_backup_manager_established" is incompatible with the test's logic. Restarting the manager invalidates the previously captured ZooKeeper lock path, causing a NoNodeException when the test tries to access the now-deleted ephemeral node.

## Exception Details
```
org.apache.zookeeper.KeeperException$NoNodeException: KeeperErrorCode = NoNode for /accumulo/.../managers/lock/zlock#...-...-...#0000000000
    at org.apache.zookeeper.KeeperException.create(KeeperException.java:117)
    at org.apache.accumulo.core.fate.zookeeper.ZooReader.getData(ZooReader.java:79)
    at org.apache.accumulo.test.functional.BackupManagerIT_RestartInjected.test(BackupManagerIT_RestartInjected.java:67)
```

## Test Execution Details
- **Test**: `BackupManagerIT_RestartInjected.test`
- **Position**: `after_backup_manager_established`
- **Target**: `manager`
- **Mode**: `GRACEFUL`
- **Index**: `0`

## Root Cause Analysis

### Test Logic Flow
1. **Lines 48-58**: Start a backup manager and wait for 2 lock entries in ZooKeeper. Store lock names in `children` list:
   - `children.get(0)` = Primary manager's lock (ephemeral ZooKeeper node)
   - `children.get(1)` = Backup manager's lock

2. **Lines 62-63**: Restart point triggers manager restart

3. **Lines 66-67**: Test tries to access `children.get(0)` (the OLD lock path)

### Why This Is a False Positive

**ZooKeeper Ephemeral Node Behavior:**
- Manager locks are ephemeral ZooKeeper nodes tied to the manager's session
- When the manager is restarted, its session ends and the ephemeral node is automatically deleted
- After restart, the manager creates a NEW lock with a different ephemeral node name
- The `children` list captured before the restart contains the OLD (now deleted) lock path

**Incompatible Restart Position:**
- The restart position "after_backup_manager_established" is placed immediately before code that uses `children.get(0)`
- The restart invalidates the captured state, making the subsequent code fail
- This is not a bug in Accumulo - it's an incompatible restart injection point

**Original Test Intent:**
- The original test (`BackupManagerIT`) verifies backup manager promotion when the primary manager's lock is **manually deleted** via ZooKeeper
- The test logic assumes the manager remains running while its lock is manipulated
- Restarting the manager creates a fundamentally different scenario that the test was not designed to handle

### Relevant Code

```java
// BackupManagerIT_RestartInjected.java

// Lines 54-58: Capture lock entries BEFORE restart
do {
  UtilWaitThread.sleep(100);
  var path = ServiceLock.path(root + Constants.ZMANAGER_LOCK);
  children = ServiceLock.validateAndSort(path, writer.getChildren(path.toString()));
} while (children.size() != 2);

// Lines 62-63: RESTART - This invalidates children.get(0)
RestartFramework.at("after_backup_manager_established").on(getCluster()).restart("manager")
    .withIndex(0).withMode(RestartMode.GRACEFUL).execute();

// Lines 66-67: Use OLD lock path - FAILS because the ephemeral node was deleted
String lockPath = root + Constants.ZMANAGER_LOCK + "/" + children.get(0);
byte[] data = writer.getData(lockPath);  // <-- NoNodeException here
```

## Conclusion

This is a **False Positive** because:
1. The ZooKeeper ephemeral node behavior is correct - locks should be deleted when the session ends
2. The restart position invalidates captured state that the test depends on
3. There is no bug in Accumulo's source code
4. The failure is caused by an incompatible restart injection point that conflicts with the test's assumptions

The restart framework should not inject restarts at positions that invalidate captured state used by subsequent test code.
