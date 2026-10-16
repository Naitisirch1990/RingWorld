# RingWorld 14 Biomes (Forge 1.20.1)

This add-on is being developed for the `port/mc-1.20.1-forge` RingWorld
branch. It assigns the agreed 14 Overworld biomes in equal-width sectors around
the 61,440-block circumference and adds one colored divider at each transition.
The dividers are 40 blocks tall above local terrain and leave a 13-block opening
around the existing 7-block road.

## Current validation

The chunk-to-sector arithmetic has a standalone Java check. The Forge/Mixin
build and server smoke test run through the repository's dedicated
`14-biome add-on validation` GitHub Actions workflow. Until those gates pass,
this source is not an installable mod or a finished world ZIP.

## World-generation note

The selected biome is applied during `doCreateBiomes`, before vanilla surface
rules and biome decoration. Existing chunks are not retroactively regenerated.
Use a new RingWorld save (or remove only disposable test chunks) to see the
biome layout consistently. The add-on intentionally does not add the later
structures requested for the separate Barttolo Estructuras phase.
