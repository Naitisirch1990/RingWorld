# DETALLE-2B VISUAL-3 — resource-only rendering trial

This trial applies only to the user-supplied unofficial Forge 1.20.1 JAR
`ringworld-forge-1.20.1-atlas-v4-DETALLE-2B-PRUEBA.jar`, SHA-256
`7a51364cc29dee112695d46f62157bb72bd29ca668aab3e9694bce837497bf12`.
The fork's Forge branch does not contain the additional `dev.ringworld.detail`
sources present in this binary. A normal branch rebuild would lose that feature.
Therefore the trial preserves every original class and changes one GLSL resource.
It is not a new full-source build or a visually qualified release.

## Behavior

The common Atlas/detail fragment shader now excludes proxy surfaces from the
interior of the effective view radius, revealing them over its final 16 blocks.
For 6/8/10 effective chunks this is 80–96 / 112–128 / 144–160 blocks.
Coverage is evaluated at each fragment using periodic world coordinates,
including vertical separation for cameras above/below real section coverage.
Material/detail distances remain horizontal. The distant projection, the
256–320-block detailed/coarse transition, and its existing dither are retained.
No additional screen-space dither is introduced.

The alpha transition keeps proxy depth writes enabled but blends fragment depth
from the OpenGL forward-depth far plane to the true surface depth as opacity
increases. This prevents almost transparent proxy faces from writing their full
surface depth and rejecting real terrain rendered afterward. Fully invisible
fragments are discarded before writing depth. Fully visible proxies keep exact
depth ordering.

This is a distance-based overlap reduction, not a real-chunk-readiness mask.
It does not improve the shape/texture of simplified trees or buildings beyond
the real view range. Fast travel or chunks awaiting compilation can expose the
late transition. Runtime review remains necessary, particularly foliage,
night/rain, high cameras, rim/seam travel, and shader/renderer replacement mods.

## Build and verify

Requires Python 3; no Java compilation is needed for this resource-only change.

```sh
python3 build_patch.py /path/to/original.jar /path/to/visual3.jar
# Optional isolated OpenGL tests: Python packages moderngl and numpy, EGL Mesa
python3 test_shader_gpu.py /path/to/original.jar
```

The builder rejects a wrong baseline, signed JARs, duplicate entries, an existing
output, and attempts to overwrite the original. It verifies that every original
entry except the fragment shader remains byte-identical. Loader version,
MATCH_VERSION declaration, packet classes and persistence are preserved.
The original mod version remains alpha.6; identify this trial by its filename,
SHA-256 and `META-INF/ringworld-visual3` sources, not by a claimed version bump.

## Install / rollback

Close Minecraft. Replace the previous RingWorld JAR in the **client** mods folder
with the VISUAL-3 trial, keeping only one RingWorld JAR installed. The server may
continue using the original DETALLE-2B binary because no classes, protocol or
version metadata changed. Putting the trial on the server is unnecessary for
this rendering change. Preserve the previous JAR outside the mods folder for
rollback. Do not install both copies together. No Atlas rebuild or world reset
is required by this patch.

Suggested first check: server view-distance=10, simulation-distance=6; client
render distance=10. Walk the same road toward the affected trees and buildings,
then compare looking along the road, upward, both rims and the periodic seam.
This environment can verify actual GLSL compilation and isolated fragment/depth
behavior with Mesa, but cannot qualify the user's complete Minecraft modpack.

## Source and licence

All changed shader source and the exact patch builder are included inside the
trial JAR at `META-INF/ringworld-visual3/`, and in this repository directory.
The unmodified original `LICENSE-RINGWORLD.txt` remains included. Changes to
RingWorld resources are MPL-2.0. Original project source is maintained in this
fork; recovering the baseline detail extension's corresponding source remains
separate work. Do not distribute this trial as an official RingWorld release.
