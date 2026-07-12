# Sable Scale

**Scale [Sable](https://modrinth.com/mod/sable) sub-levels (vehicles / physics objects) up and down with commands.**

Ever wanted a giant airship or a tiny model boat? Sable's transform stack secretly supports per-sub-level scaling —
rendering, client interpolation, entity collision and the world↔vehicle coordinate mapping all honour it. What's
missing is any way to *set* it, and Sable's network protocol and save format both drop the value. Sable Scale fills
exactly those gaps.

## Commands (permission level 2)

| Command | Effect |
|---------|--------|
| `/sablescale set <sub_level> <scale>` | Set a uniform scale (0.01 – 100) |
| `/sablescale reset <sub_level>` | Back to ×1 |
| `/sablescale get <sub_level>` | Print the current scale |
| `/sablescale looking <scale>` | Scale the sub-level you are **looking at** |
| `/sablescale looking` | Print the scale of the sub-level you are looking at |

`<sub_level>` is Sable's own selector argument — `@e` (all), `@n` (nearest), `@v` (viewed), `@i` (inside),
`@l` (latest), plus name/limit/sort filters like `@e[name=ferry]`.

Scaling is centered on the sub-level's rotation point (its center of mass), and clients animate the resize smoothly
through Sable's normal pose interpolation.

## Install notes

- **Install on BOTH server and client.** The mod extends Sable's pose wire format (two tiny mixins), so both sides
  must agree.
- **Saves are safe in both directions**: worlds open fine without the mod (scaled sub-levels just return to ×1, and
  the stored scale is ignored, not corrupted).
- Sable is an *optional* dependency — the jar loads cleanly in packs without it and simply stays dormant.

## How deep does it go?

Everything is scale-aware: rendering, entity collision, picking/breaking outlines, **and terrain collision** — the
mod rebuilds the vehicle's Rapier voxel collider on a resampled lattice so the physical hull matches the scaled
visuals (block collision boxes are re-placed around the center of mass and fragmented per cell, so even ×0.75-style
scales collide correctly).

Known limits: mass and inertia stay those of the unscaled vehicle, and impact block-callbacks (e.g. blocks that
explode on collision) don't fire on scaled vehicles.

---

Built from the same repo as **Make it Compatible: Voxy / Sable** and **Better Simple Clouds**, and fully compatible
with them. MIT licensed, unaffiliated with Sable — get Sable from RyanHCode.
