# Forge 1.20.1 backport status

This branch is experimental and must not be distributed as a playable mod yet.

## Build gate

The first GitHub Actions compile attempt failed during Gradle dependency verification, before any Java sources were compiled. The existing verification metadata belongs to the 1.21.1 dependency graph and needs independently checked SHA-256 entries for new ForgeGradle transitive metadata. Preserve strict dependency verification rather than disabling it.

## Remaining work

- Compile with Forge 47.x on Java 17, correcting mappings and API differences from Minecraft 1.21.1.
- Port the common world generator, network protocol, entity seam crossing, chunk generation, world saves, and client render hooks.
- Add server/client integration tests for continuous X wrapping, multiplayer, Nether and End, and boundary containment.
- Add the requested 61,440-by-2,000 / 30,720-by-2,000 / 61,440-by-2,000 layouts and terrain/structures only after the core is stable.
- Validate a built JAR in a clean Forge 1.20.1 client and dedicated server before declaring readiness.

## Geometry constraint discovered

The existing RingGeometry requires widthBlocks to be divisible by 16, whereas the requested width is exactly 2,000 blocks. This must be addressed deliberately (including partial edge chunks) before shipping. The requested circumferences 61,440 and 30,720 are divisible by 16.
