# TEST-BUG Report: Group 5 - IllegalArgumentException in TabletFile.parsePath

## Summary

The test utility method `GarbageCollectorTrashBase.countFilesInTrash()` fails when non-tablet files (e.g., recovery files) are present in the HDFS trash directory. This occurs because the method incorrectly assumes all files in the trash are valid tablet files.

## Failure Details

**Exception Type**: `java.lang.IllegalArgumentException`

**Error Message**:
```
Missing or invalid part of tablet file metadata entry: hdfs://localhost:35153/user/shuai/.Trash/Current/accumulo/recovery/3e971ece-650e-4bb6-8fa8-4b33c2ca42bb/finished
```

**Affected Tests**:
- `GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled`
- `GarbageCollectorTrashEnabledIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled`

**Failure Count**: 5 test executions

## Root Cause Analysis

### What Happens

1. When a tablet server is restarted during test execution, WAL (Write-Ahead Log) recovery files may be created
2. These recovery files can be moved to the HDFS trash by the garbage collector
3. The test method `countFilesInTrash()` iterates over ALL files in the trash directory
4. For each file found, it unconditionally tries to create a `TabletFile` object
5. Recovery files have paths like: `hdfs://localhost/user/root/.Trash/Current/accumulo/recovery/<uuid>/finished`
6. This path structure doesn't match the expected tablet file format: `<volume>/tables/<tableId>/<tablet>/<file>`
7. `TabletFile.parsePath()` correctly rejects this invalid path with an `IllegalArgumentException`

### Buggy Code Location

**File**: `test/src/main/java/org/apache/accumulo/test/functional/GarbageCollectorTrashBase.java`

**Lines**: 98-123

```java
protected long countFilesInTrash(FileSystem fs, TableId tid)
    throws FileNotFoundException, IOException {
  Collection<FileStatus> dirs = fs.getTrashRoots(true);
  if (dirs.isEmpty()) {
    return -1;
  }
  long count = 0;
  Iterator<FileStatus> iter = dirs.iterator();
  while (iter.hasNext()) {
    FileStatus stat = iter.next();
    LOG.debug("Trash root: {}", stat.getPath());
    RemoteIterator<LocatedFileStatus> riter = fs.listFiles(stat.getPath(), true);
    while (riter.hasNext()) {
      LocatedFileStatus lfs = riter.next();
      if (lfs.isDirectory()) {
        continue;
      }
      // BUG: This line assumes ALL files are valid tablet files
      TabletFile tf = new TabletFile(lfs.getPath());  // <-- LINE 115: FAILS HERE
      LOG.debug("File in trash: {}, tableId: {}", lfs.getPath(), tf.getTableId());
      if (tid.equals(tf.getTableId())) {
        count++;
      }
    }
  }
  return count;
}
```

### Why This is a TEST-BUG (Not a Source Code Bug)

1. `GarbageCollectorTrashBase.java` is located in the test module (`test/src/main/java/...`), not in production code
2. The production class `TabletFile.parsePath()` is working correctly - it properly validates and rejects paths that don't match the expected tablet file format
3. The test utility code makes an invalid assumption that all files in the trash are tablet files

## Suggested Fix

Add a check to skip non-tablet files before attempting to parse them:

```java
protected long countFilesInTrash(FileSystem fs, TableId tid)
    throws FileNotFoundException, IOException {
  Collection<FileStatus> dirs = fs.getTrashRoots(true);
  if (dirs.isEmpty()) {
    return -1;
  }
  long count = 0;
  Iterator<FileStatus> iter = dirs.iterator();
  while (iter.hasNext()) {
    FileStatus stat = iter.next();
    LOG.debug("Trash root: {}", stat.getPath());
    RemoteIterator<LocatedFileStatus> riter = fs.listFiles(stat.getPath(), true);
    while (riter.hasNext()) {
      LocatedFileStatus lfs = riter.next();
      if (lfs.isDirectory()) {
        continue;
      }

      // FIX: Skip files that are not in the /tables/ directory structure
      String pathStr = lfs.getPath().toString();
      if (!pathStr.contains("/tables/")) {
        LOG.debug("Skipping non-tablet file in trash: {}", lfs.getPath());
        continue;
      }

      try {
        TabletFile tf = new TabletFile(lfs.getPath());
        LOG.debug("File in trash: {}, tableId: {}", lfs.getPath(), tf.getTableId());
        if (tid.equals(tf.getTableId())) {
          count++;
        }
      } catch (IllegalArgumentException e) {
        // Skip files that are not valid tablet files
        LOG.debug("Skipping invalid tablet file in trash: {}", lfs.getPath());
      }
    }
  }
  return count;
}
```

## Reproduction Steps

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
cd /home/shuai/xlab/restart_testing/accumulo
mvn -pl test failsafe:integration-test \
  -Dit.test=GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected#testTrashHadoopEnabledAccumuloEnabled \
  -Drestart.position=after_initial_flush \
  -Drestart.target=tablet_server \
  -Drestart.mode=GRACEFUL \
  -Drestart.tracking.agent=/home/shuai/xlab/restart_testing/RestartTestingFramework/restart-tracking-agent/target/restart-tracking-agent-1.0.0-SNAPSHOT.jar
```

## Stack Trace

```
java.lang.IllegalArgumentException: Missing or invalid part of tablet file metadata entry: hdfs://localhost:35153/user/shuai/.Trash/Current/accumulo/recovery/3e971ece-650e-4bb6-8fa8-4b33c2ca42bb/finished
    at org.apache.accumulo.core.metadata.TabletFile.parsePath(TabletFile.java:124)
    at org.apache.accumulo.core.metadata.TabletFile.<init>(TabletFile.java:162)
    at org.apache.accumulo.test.functional.GarbageCollectorTrashBase.countFilesInTrash(GarbageCollectorTrashBase.java:115)
    at org.apache.accumulo.test.functional.GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.testTrashHadoopEnabledAccumuloEnabled(GarbageCollectorTrashEnabledCustomPolicyIT_RestartInjected.java:123)
```

## Classification

- **Type**: TEST-BUG
- **Severity**: Medium
- **Component**: Test Utility Code
- **File**: `test/src/main/java/org/apache/accumulo/test/functional/GarbageCollectorTrashBase.java`
