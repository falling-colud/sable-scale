# Sable Scale 1.1.1

**Scaled-down vehicles are stable in water again.**

## Fixed
- **A shrunken vehicle no longer glitches, flies away, or drops into the void when it touches water.** Sable applies water drag per *hull surface cell*, so the drag a vehicle feels follows its surface area — but its weight follows its volume. Shrink a vehicle and the drag it feels per tonne climbs as `1/S`, until it is strong enough that the physics step overshoots it every tick: the velocity flips sign and grows instead of settling, and the vehicle leaves on a geometric ramp — sideways, or straight down through the bottom of the world. The jitter you saw first was the same thing, just below the threshold. Drag is now scaled to track the vehicle's `S³` mass the way buoyancy already did, so a scaled vehicle handles in water exactly as it does at scale 1, at any size.

## Requires
- NeoForge 1.21.1, and **Sable**. Everything self-gates, so the jar is safe to install with any subset of the bridged mods. MixinSquared is bundled.
