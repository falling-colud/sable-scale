# Sable Scale 1.1.5

**Scaled vehicles no longer go black under Sable's dynamic shading.**

## Fixed
- **A shrunk vehicle no longer turns fully dark (the real cause).** Sable's dynamic directional shading re-derives
  each face's brightness from its world normal so a rotated vehicle shades correctly — but it gets that normal by
  pushing the vertex normal through `ModelViewMat`, which is exactly the matrix Sable Scale bakes the pose scale
  into. Normals must never be scaled, and Veil's `block_brightness` neither normalises nor responds linearly: it
  raises the face components to the 3rd power (top/bottom) and 2nd power (sides). So the shading multiplier landed
  at `s³` and `s²` — a half-scale vehicle rendered at 0.125×/0.25× brightness, and by quarter scale it was
  effectively solid black. (Scaling *up* saturated instead of blacking out.) That is why turning dynamic shading off
  in Sable's options "fixed" it — that switch removes the multiply entirely. The normal is now normalised before
  shading, so a scaled vehicle shades exactly like an un-scaled one at any size, with dynamic shading left on.

- **Secondary: sky light no longer depends on scale.** Sable measures how much sky reaches a sub-level by sampling
  the world over the vehicle's bounding box, which carries the scale — so a shrunk vehicle's samples collapsed onto
  one spot and could read "no sky". That measurement is now scale-invariant. (Shipped in 1.1.2; it was not the cause
  of the blackouts.)

## Requires
- NeoForge 1.21.1, and **Sable**. Everything self-gates, so the jar is safe to install with any subset of the bridged
  mods. MixinSquared is bundled.
