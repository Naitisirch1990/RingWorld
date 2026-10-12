# Forge 1.20.1 backport status

## Current checkpoint — 2026-10-12

This is an unofficial, experimental Forge 47.4.10 / Minecraft 1.20.1 backport,
using Java 17. It is not a finished or visually qualified release. Alpha 3 visual
changes are being checked against a copied save before any visual claim.

At commit `26dce48`, Actions run `38281886797`, attempt 2, passed the JAR
build/content checks, periodic chunk arithmetic and dedicated-server startup
with the Overworld (61,440 × 2,000), Nether (30,720 × 2,000) and End
(61,440 × 2,000) profiles. The client smoke gate failed on that run. After adding retained client
diagnostics, run `38291123200` at `8eea334` passed every gate, including client
bootstrap, without changing runtime sources. No RingWorld fatal/Mixin signature
or crash report was present in that rerun. The original failure was not
reproduced and its cause remains unconfirmed. This is development-client
bootstrap evidence, not an in-world visual or packaged-client qualification.

The latest source includes a fixed-Y=71 road with mountain tunnels, client
terrain-atlas interpolation and distant-ring visual changes. Automatic custom
cities, pyramids and monuments are outside the user's current requested scope.
Alpha 2 retained observed client terrain and showed a pale silhouette for
unsampled areas. Alpha 3 extends the distant mesh fade from the vanilla render
range to the ring horizon, applies a periodic stronger-curvature profile to
live terrain, entities and the distant mesh, and doubles the visible fascia
width. Those changes do not yet add unknown opposite-side terrain to the atlas.

## Diagnostic workflow

The client smoke retains its required startup markers, 90-second observation
window and fatal-error checks. Its console, client logs and crash reports are
now included in the diagnostic artifact even when the smoke fails. Pipeline
exit handling preserves the Gradle/timeout status rather than the tee status.
These files distinguish missing runtime preparation, Mixin failures and shader
failures without converting an unsuccessful launch into a pass.

The CI diagnostic build currently disables dependency verification explicitly.
The original 1.21.1 dependency inventory does not qualify the Forge graph;
independently reviewed Forge dependency pins remain a release requirement.

## Remaining validation

- Preserve client diagnostics if the earlier intermittent failure reappears.
- Inspect real in-world upward views, terrain/atlas handoff, both rims and seam
  movement; menu/bootstrap checks do not establish visual correctness.
- Verify all-dimension portal/seam behavior and save/reconnect.
- Validate TacZ and Embeddium separately; compatibility is not established.
- Retain exact build/source identity and MPL-2.0 notices with test deliveries.

## Alpha 2 rendering repair — limited in-world verification complete

User screenshots show the sky-scale proxy detached from live terrain and
invisible finite-band edges. The Forge adapter now uses the shared physical
cylinder for both surface representations, with a dedicated bundled far-depth
shader. The radius-240 miniature cylinder and synthetic biome stripes are
removed. Real client samples use eight-block spacing and world-instance-owned
storage; unloaded observations survive within that world, while a world switch
clears them. Unknown areas remain a pale silhouette, not claimed real terrain.
A complete persisted/server-backed atlas is still pending.

Finite-Z exterior neighbours no longer block rim meshing; real interior
neighbours retain vanilla readiness checks. Curved bounding-box visibility,
non-occluding ring traversal, and entity/block-entity tangent poses are ported
from the reference architecture. The atlas mesh now fades near half the ring
circumference instead of the vanilla render-distance boundary.

The road generator and all world layouts are unchanged. No external shader
pack is required. Embeddium and shader-replacing mods remain unqualified.
CI adds an opt-in copied-world capture fixture for road, upward, rim and seam
views. Captures require manual inspection and are not multiplayer or movement
qualification. It never opens a user's existing save.

### First in-world capture findings

Run `38296473785` built alpha 2 and passed dedicated-server/client bootstrap.
The capture process timed out on shutdown. Its first camera was inside stone
(Y=92 at a column whose surface was Y=125); later views did not verify arrival
and therefore cannot qualify rim/seam visuals. Do not ship this checkpoint as
a visually validated improvement.

The Forge edge-load handler also mutated chunks inside `ChunkEvent.Load`,
which Forge explicitly warns can run before FULL promotion and deadlock level
interactions. New chunks now keep the rim already installed during generation.
Existing chunks queue repairs for END ticks with a loaded 3×3 neighbourhood,
at most two repairs per tick, with unload cleanup. The updated visual fixture
uses a road-tunnel camera and an above-terrain camera, requires server teleport
completion plus actual client position/orientation, and records a server stack
on arrival timeout. Clean shutdown remains required. Runtime recheck pending.

### Verified alpha-2 test delivery

Runtime/source commit `a8fca8dfcc5fb9670e7e0f477855436b58791913`,
Actions run `38298445431`, passed build/content, periodic arithmetic, server
and client bootstrap, all four position-verified screenshots, and clean
integrated-server save/shutdown. The rim transfer completed at Z=-980; the
seam target X=61430 appeared in the nearest client chart at X=-10.

Manual inspection confirms the road design, a visible physical bedrock rim,
and a coherent ring silhouette using the same radius as the live terrain.
The upward/seam images also clearly expose the remaining limitation: unknown
atlas regions are plain pale surfaces, and the known-to-unknown boundary can
still look soft/obvious. This is not complete distant-map fidelity, natural
walking/seam qualification, multiplayer qualification, or renderer-mod
compatibility qualification. No percentage improvement or FPS gain is claimed.
The four screenshots are included in the test package.


## Alpha 3 visual candidate — runtime review pending

The alpha-3 curve uses `theta + 0.65 sin(theta)` consistently in the Forge
terrain shader, curved entity and block-entity presentation, frustum envelope,
and distant-atlas mesh. It remains exactly periodic at one full circumference;
world generation and the fixed road are unchanged. The far mesh's fade distance
is now half the circumference, and its rim strip is 10 blocks wide rather than
5. The alpha-3 candidate still uses only real terrain samples observed by the
client. The reference mod's complete far-side relief comes from its
server-generated, persisted and synchronized terrain atlas; this Forge 1.20.1
port does not yet include that atlas transport. No external shader pack is
required. The renderer changes still need a fresh build and inspected captures.
