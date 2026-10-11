# RingWorld Forge 1.20.1 custom backport

This directory is an isolated Forge 1.20.1 / Java 17 workspace. It intentionally does not mutate the validated 1.21.1 Fabric/NeoForge build while the older Minecraft ABI is being restored.

## Current milestone

The first milestone is deliberately small and testable:

- boot as a Forge 1.20.1 mod;
- persist immutable custom ring dimensions with the 1.20.1 `SavedData` API;
- restore the periodic-X / finite-Z mathematical contract;
- encode the accepted custom dimensions and landmark cadence;
- keep the original 1.21.1 branch available as behavioral reference.

Accepted project dimensions are:

- Overworld: `61,440 x 2,000` blocks (`3,840 x 125` chunks)
- Nether: `30,720 x 2,000` blocks (`1,920 x 125` chunks)
- End: `61,440 x 2,000` blocks (`3,840 x 125` chunks)

The Overworld mega-landmark scheduler has exactly fifteen ~4,096-block sectors. `PYRAMID_CITY` is intentionally unique.

## Build

Use Java 17 and Gradle 8.1.1:

```bash
gradle -p forge1201 test
gradle -p forge1201 build
```

GitHub Actions runs both commands for pushes affecting this workspace.

## Important boundary

This alpha scaffold is not yet a playable RingWorld. Natural seam travel, canonical chunk ownership, terrain periodicity, structures, portals, entities/projectiles, multiplayer synchronization, side-wall generation, and curved rendering are the next porting layers.
