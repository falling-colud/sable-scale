# Sable Scale 1.1.0

**Full Create-family compatibility for scaled sub-levels** (Create, Create: Aeronautics, Create Simulated, and Offroad).

## Fixed
- **Wheels/actuators no longer misbehave at low scale.** Offroad wheels bounced violently when a vehicle was scaled down, and propeller thrust / balloon lift / springs / magnets / recoil under-drove or over-drove a scaled vehicle. Sable computes those forces for the *unscaled* mass, but a scaled body weighs `S³` as much — so every actuator impulse is now mass- and inertia-corrected to give the scaled vehicle the same behaviour it has at scale 1. One fix covers Offroad, Create: Aeronautics, Create Simulated and Sable's own reaction wheels / lift / drag / friction.
- **Create shafts (and other Flywheel-rendered kinetic blocks) now scale with the vehicle.** Create 6 draws kinetic blocks — shafts, cogs, belts, Offroad wheels — through Flywheel, whose instanced geometry ignored the sub-level scale and rendered at full size inside a shrunk/grown hull. It now honours the pose scale, matching the rest of the vehicle. (Uses MixinSquared, bundled.)

- **Stickers, super glue and every other joint now attach to the right spot.** Constraint anchors are given in unscaled block coordinates, but a scaled vehicle's blocks physically sit somewhere else — so a sticker on a scaled vehicle pinned itself to a phantom point and dragged the hull toward it. Anchors are now mapped onto the scaled geometry, fixing Create stickers/super glue and Create Simulated's merging glue, docking connectors, ropes and physics staff.
- **Swivel bearings (and handles / physics staff / ropes) no longer fight a scaled vehicle.** Their motors size their stiffness/damping against the *unscaled* inertia, so at low scale a bearing tuned for the full-size build slammed a much lighter one around, and at high scale it was too weak to move it. Motor gains and force limits now track the scaled body's real mass and inertia.

## Known limitations
- Joint anchors are mapped using the vehicle's centre of mass at the moment the joint is made. Heavily rebuilding a scaled vehicle *while* it's stuck to something can drift the joint slightly until it's re-made (stickers re-make theirs automatically on re-attach).

## Requires
- NeoForge 1.21.1, and **Sable**. The Create/Flywheel rendering patch activates only when Create/Flywheel are present; everything self-gates, so the jar is safe to install with any subset of these mods. MixinSquared is bundled.
