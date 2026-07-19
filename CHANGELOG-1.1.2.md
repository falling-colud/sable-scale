# Sable Scale 1.1.2

**Scaled sub-levels no longer black out.**

## Fixed
- **A scaled vehicle no longer randomly turns fully dark.** Sable dims a whole sub-level's sky light by a single
  factor that it works out by sampling how much sky reaches the vehicle — measured over the vehicle's bounding box.
  That box carries the scale, so a shrunk vehicle was measured over a tiny box: the sample points collapsed onto one
  spot (often under its own deck, or a block into the water as it bobbed), read "no sky", and every sky-lit surface on
  the vehicle snapped to black — flickering in and out as it moved. Only sky light was affected, so torch-lit interiors
  stayed lit while everything in daylight went dark. The sky-light measurement is now scale-invariant: a vehicle is lit
  exactly as an un-scaled copy of itself would be, at any size. Un-scaled vehicles are untouched.

## Requires
- NeoForge 1.21.1, and **Sable**. Everything self-gates, so the jar is safe to install with any subset of the bridged
  mods. MixinSquared is bundled.
