# Minecraft 1.20.1 custom backport status

Source baseline: RingWorld 1.21.1 Beta 2 lineage at commit `848b9cc5982ab473f84f91a4301ffb4176222ad6`.

Target: Forge 1.20.1, Java 17, custom three-dimension ringworld.

## Project contract

The port must preserve the original behavioral idea rather than simulate wrapping with visible corrective teleports:

- X is a periodic circumference with one canonical server storage interval.
- Z is a finite playable band.
- Nearest periodic images must be used for distance-sensitive gameplay near the seam.
- The Overworld, Nether, and End each receive their own accepted circumference.
- Ring dimensions are immutable once saved.
- Side limits will use a bedrock foundation/wall plus an invisible barrier behind it; the visible wall rises ten blocks above local terrain.
- Gameplay-critical vanilla progression must remain possible.

## Accepted dimensions

| Dimension | Circumference | Width | Circumference chunks | Width chunks |
| --- | ---: | ---: | ---: | ---: |
| Overworld | 61,440 | 2,000 | 3,840 | 125 |
| Nether | 30,720 | 2,000 | 1,920 | 125 |
| End | 61,440 | 2,000 | 3,840 | 125 |

## Landmark design baseline

The Overworld is divided into fifteen major sectors at roughly 4,096-block cadence with deterministic jitter. Current high-level order:

1. Grand start city
2. Abandoned city
3. Military base
4. Giant dam
5. Fortified castle
6. Industrial complex
7. Destroyed airport
8. Pyramid city — unique in the entire world
9. Port city
10. Open-pit mine
11. Underground laboratory
12. Flooded city
13. Mountain observatory
14. RingWorld installation
15. Final city

The pyramid city is intended as a large desert settlement centered on one monumental pyramid, with no second mega-pyramid elsewhere.

## Implemented on this branch

- Dedicated `port/mc-1.20.1-custom` branch created from the Beta 2 source baseline.
- Isolated `forge1201/` ForgeGradle workspace added so the validated 1.21.1 build remains untouched.
- Minecraft 1.20.1 / Forge 47.4.10 / Java 17 toolchain declared.
- Minimal Forge mod bootstrap added.
- Immutable three-dimension layout constants added.
- Periodic X wrapping, shortest seam delta, and nearest-image math added.
- 1.20.1-style `SavedData` implementation added for immutable dimensions.
- Deterministic fifteen-sector mega-landmark schedule added.
- Unit tests added for dimensions, seam math, deterministic landmark placement, and unique pyramid city.
- GitHub Actions build/test gate added for the isolated workspace.

## Next implementation layers

1. Make the Forge 1.20.1 bootstrap compile cleanly in CI and lock the dependency graph.
2. Port canonical chunk-coordinate topology and finite-Z chunk filtering.
3. Port terrain/noise coordinate remapping so terrain itself is seamless at X=0/circumference.
4. Port natural player/entity seam folding without a visible teleport correction.
5. Port block/entity lookup, collision, projectile, AI/pathfinding, explosions, interaction reach, and tracking across the seam.
6. Port structures, strongholds, portals, raids, maps/compasses, ticks, fluids/redstone edge behavior, and multiplayer login ordering.
7. Generate the custom bedrock/barrier side boundaries.
8. Add deterministic roads and small POIs between mega-landmarks.
9. Author and integrate the mega-structure library, including the unique pyramid city.
10. Port/replace the curved renderer and Atlas path for 1.20.1, then qualify Embeddium/TacZ compatibility.
