# Forge 1.20.1 backport status

## Current checkpoint — 2026-10-12

This is an unofficial, experimental Forge 47.4.10 / Minecraft 1.20.1 backport,
using Java 17. It is not a finished or visually qualified release.

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
The distant view still uses synthetic fallback for terrain not sampled by the
client; it is not a complete server-derived atlas.

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
