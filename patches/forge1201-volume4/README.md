# RingWorld Overworld-only VOLUME-4 — medium-distance trial

Minecraft Java 1.20.1, Forge 47.4.10. Unofficial MPL-2.0 test build.
Exact input: the user-supplied `RingWorld-Atlas-Forge-1.20.1.jar`, SHA-256
`340e43efd072d50fd3ed0a4afbf1d28a512a8a7fc8a847d238fd9a1b396db3fc`.
This binary already contains VISUAL-3 and the 2026-11-06 Overworld-only patch.
The repository's ordinary Forge build lacks those additional binary features;
this is a reproducible patch of that exact input, not a normal Gradle release.

## What the screenshots establish

| Screenshot | Camera X, Y, Z | Displacement from previous camera |
| --- | --- | --- |
| 16:11:30 | 17391, 75, -239 | — |
| 16:11:02 | 17355, 95, -239 | 41.18 blocks |
| 16:11:11 | 17329, 122, -236 | 37.60 blocks |
| 16:11:21 | 17305, 153, -258 | 44.96 blocks |

The total first-to-last displacement is 117.65 blocks, including ascent.
These are camera movements, **not measured distances to the airships**. Their
exact positions cannot be recovered from the minimap alone. The pictures show
real geometry progressively replacing colored vertical curtains.

With nine effective render chunks, the nominal radius is 144 blocks. Actual
delivery/readiness also depends on client settings, section compilation and
streaming. VISUAL-3's protected interior and 128–144-block near fade remain.
VOLUME-4 improves the session detail beyond this: fully detailed through 288
horizontal blocks, then a 288–320-block transition into the coarse Atlas.
It does not increase the real-chunk radius or simulation distance.

## Geometry and data flow

The old medium-distance representation kept one top/ground pair per two-block
column and joined neighboring heights with walls. It cannot distinguish a
floating hull from filled terrain beneath it. This trial instead reads saved
block occupancy and greedily meshes exterior faces, including undersides and
separated vertical layers. Air is retained. The initial mesh uses one-block
occupancy. Complex chunks fall back to two-/four-block horizontal sampling
within hard budgets, retaining individual vertical gaps at every tier.

Saved region files are read on one worker. The main server thread still owns
players, Atlas/state identity, cache publication and network sends. No live
chunks, generation requests or world mutations are added. Reads remain
Overworld-only through the existing runtime hooks. The completed Atlas, saved
geometry, terrain shaders, road, generation, physics, and Nether/End exclusion
are preserved. Original Overworld-only resources/classes are byte-identical.

Session detail now carries compressed rectangles, bounded to 4,096 quads,
49,166 uncompressed bytes and 24,576 compressed bytes per chunk. Palette and
coordinates are checked. The network channel is deliberately upgraded to
`ringworld-atlas-v7-volume4-forge1201`; old/new clients cannot silently mix.
The disk Atlas format, region files and saved settings do not change.

The existing 20-chunk request radius, dirty groups, GPU teardown, finite-width
coverage texture, and periodic coordinates are retained. There is at most one
eight-request disk job outstanding. Detail sends are capped at two chunks and
24 KiB per player per tick, plus 64 KiB globally per tick with rotating player
priority. The shared packet cache is bounded to 4,096 entries and 64 MiB.
Per-player send history retains revision numbers, not copies of mesh payloads.
Identical resamples retain a revision and do not resend. Null/missing saved
chunks retain coarse fallback rather than forcing world generation.

## Visual limits

This preserves occupied block silhouettes, not Minecraft's full block models
and textures. Stairs, fences, chains, leaves, glass and fluids remain colored
voxel approximations at medium distance; transparent models are not reproduced.
Named wool/concrete/terracotta/glass colors and common wood variants have
distinct representative colors. Resource packs/modded materials may differ.
Beyond 320 blocks, the original coarse Atlas and its heightfield limitations
remain. Nine chunks cannot produce full vanilla textures at 300 blocks.

The near transition is still distance-based, not a compiled-section-readiness
mask. Fast streaming may expose a gap. Complex chunks can hit detail budgets
and retain the coarse fallback. More geometry uses more bandwidth, client
memory and GPU upload time than the previous height-only representation.
Hardware frame pacing, complete modpack compatibility, night/rain and actual
server/client behavior require in-game testing; no FPS improvement is claimed.

Region capture reflects the last saved chunks. Newly placed structures may
need a normal save plus the existing 60-second recapture interval. Reconnect
clears the session detail. Do not delete world regions or regenerate the Atlas
to try this patch.

## Install and rollback

1. Stop the server normally and close Minecraft. Keep the previous JAR outside
   `mods` so rollback is simple.
2. Put the **same VOLUME-4 JAR on the server and every client**. Keep one
   RingWorld JAR per mods folder. Update the distributed AutoModpack copy too.
3. Keep server `view-distance=9` and the current simulation distance. Set the
   client render distance to at least nine chunks. Let the session Atlas and
   saved detail arrive before comparing the airships at the four positions.
4. Check road/upward/rim/seam views, a floating build with visible air below,
   Overworld → Nether → End → Overworld, and disconnect/rejoin. For rollback,
   stop normally and restore the previous JAR on both server and clients.

## Build and tests

Requires Java 17 with `jdk.compiler`, Python 3. This script calls javac through
its module; standalone javac executables are not required.

```sh
python3 build.py /path/to/RingWorld-Atlas-Forge-1.20.1.jar /new/path/to/VOLUME-4.jar
python3 test.py /new/path/to/VOLUME-4.jar
```

Tests additionally use Python `moderngl`, `numpy`, EGL/Mesa. They cover pure
geometry, saved MCA/NBT readers, real modified packet bytecode with API doubles,
render adapters, scheduler budgets and the actual packaged GLSL drawing a
volumetric gap at a nine-chunk camera setting. API doubles are explicitly
isolated test/build inputs; no `net.minecraft`, Forge, Netty or rendering
placeholder classes are packaged in the mod.

Evidence is development testing, **not a real Minecraft/Forge launch, multiplayer,
performance or in-world visual qualification**. The existing release gates
remain pending. Keep this as a trial until the owner checks the modpack.

All modified sources, bytecode adapter, build script, test doubles/tests and
these instructions are embedded in `META-INF/volume4/`. The original MPL-2.0
licence remains included; no unrelated binary classes are rebuilt. The adapter
checks exact original method signatures and edit counts before producing output.
