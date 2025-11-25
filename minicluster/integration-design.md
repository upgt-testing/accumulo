# ProcessBasedMiniAccumuloCluster Integration Design

## Current Architecture Analysis

### MiniAccumuloCluster Flow
```
MiniAccumuloClusterImpl
  └─ start() method
      ├─ control.start(ZOOKEEPER)
      ├─ control.start(TABLET_SERVER)
      ├─ control.start(MANAGER)
      └─ control.start(GARBAGE_COLLECTOR)

MiniAccumuloClusterControl
  └─ start(ServerType server)
      └─ cluster._exec(ServerClass, serverType, configOverrides)
          └─ Spawns process with CURRENT JVM classpath
```

### Our ProcessBased Architecture
```
ProcessBasedMiniAccumuloCluster
  ├─ AccumuloVersionRegistry (manages distributions)
  ├─ PortManager (persists ports)
  ├─ ProcessExecutor (version-specific process spawning)
  ├─ ServerProcessManager (server lifecycle with version support)
  └─ ProcessBasedClusterControl (upgrade orchestration)
```

## Integration Strategy

### Option 1: Extend MiniAccumuloClusterControl (RECOMMENDED)
Create `VersionAwareMiniAccumuloClusterControl extends MiniAccumuloClusterControl`:
- Override `start()` to use ServerProcessManager
- Reuse all other methods (stop, admin, etc.)
- Minimal code duplication

### Option 2: Completely Custom Control
ProcessBasedClusterControl replaces MiniAccumuloClusterControl:
- Must implement all methods
- More control but more code duplication

## Implementation Plan (Option 1)

### Step 1: Create VersionAwareMiniAccumuloClusterControl

```java
package org.apache.accumulo.minicluster.upgrade;

public class VersionAwareMiniAccumuloClusterControl
    extends MiniAccumuloClusterControl {

  private final ServerProcessManager serverProcessManager;
  private final AccumuloVersionRegistry versionRegistry;
  private final ProcessBasedClusterControl upgradeControl;

  @Override
  public synchronized void start(ServerType server,
                                  Map<String,String> configOverrides,
                                  int limit) throws IOException {
    // Use ServerProcessManager instead of cluster._exec()

    switch (server) {
      case TABLET_SERVER:
        for (int i = 0; i < limit; i++) {
          ProcessInfo pi = serverProcessManager.startServer(
              server,
              versionRegistry.getStartDistribution(),
              i,
              configOverrides);
          tabletServerProcesses.add(pi.getProcess());
          upgradeControl.setTabletServerProcess(i, pi.getProcess(), version);
        }
        break;
      // ... similar for other server types
    }
  }
}
```

### Step 2: Wire into ProcessBasedMiniAccumuloCluster

```java
public class ProcessBasedMiniAccumuloCluster extends MiniAccumuloCluster {

  private MiniAccumuloClusterImpl impl;
  private VersionAwareMiniAccumuloClusterControl versionAwareControl;

  @Override
  public synchronized void start() throws IOException, InterruptedException {
    // Initialize version-aware control
    versionAwareControl = new VersionAwareMiniAccumuloClusterControl(
        impl, serverProcessManager, versionRegistry, processBasedClusterControl);

    // Replace the control in impl (via reflection or setter if available)
    setControl(impl, versionAwareControl);

    // Delegate to base implementation - will use our control
    impl.start();
  }
}
```

### Step 3: Integrate ProcessBasedClusterControl

The existing `ProcessBasedClusterControl` tracks processes for upgrades:
- Keep it separate from `VersionAwareMiniAccumuloClusterControl`
- Use it to track version metadata
- Bridge between the two via process tracking

```java
// When starting a server
ProcessInfo pi = serverProcessManager.startServer(...);
tabletServerProcesses.add(pi.getProcess());
processBasedClusterControl.setTabletServerProcess(i, pi, version);
```

## Key Integration Points

1. **Server Startup**
   - `VersionAwareMiniAccumuloClusterControl.start()` calls `ServerProcessManager.startServer()`
   - ServerProcessManager handles version-specific classpath
   - Process registered in both controls (for stop) and ProcessBasedClusterControl (for upgrade)

2. **Process Tracking**
   - `MiniAccumuloClusterControl` tracks processes for start/stop
   - `ProcessBasedClusterControl` tracks processes + versions for upgrade
   - Keep both in sync

3. **Port Management**
   - ServerProcessManager uses PortManager
   - Ports persist across restarts
   - Node identity preserved

## Testing Strategy

1. **Unit Test**: VersionAwareMiniAccumuloClusterControl
   - Mock ServerProcessManager
   - Verify start() calls ServerProcessManager

2. **Integration Test**: Full cluster startup
   - Start ProcessBasedMiniAccumuloCluster
   - Verify all servers running
   - Create table, write data

3. **Upgrade Test**: Rolling upgrade
   - Start cluster with version A
   - Call upgrade()
   - Verify all servers running version B
   - Verify data preserved

## Implementation Checklist

- [ ] Create VersionAwareMiniAccumuloClusterControl
- [ ] Override start() method for all ServerTypes
- [ ] Wire into ProcessBasedMiniAccumuloCluster.start()
- [ ] Integrate ProcessBasedClusterControl tracking
- [ ] Test cluster startup
- [ ] Test rolling upgrade
- [ ] Update documentation
