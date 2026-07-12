# Sable Scale

**Scale Sable vehicles up or down with a command.** *(NeoForge 1.21.1)*

Want a giant airship or a tiny model boat? Sable already renders, collides and interpolates scaled
sub-levels correctly — it just never gave you a way to set the scale, and dropped the value on save
and sync. Sable Scale fills exactly those gaps, so whatever size you pick sticks. Requires Sable.

## Building

Requires JDK 21 (Gradle auto-provisions the toolchain).

```bash
./gradlew scaleJar     # builds the jar -> build/libs/sable-scale-<version>.jar
./gradlew runClient    # launches a dev client (put bridged mods in run/mods/)
```

The mods this bridges are compile-only and **not** bundled — they're provided at runtime by your modpack.
To build, drop the matching jars into `libs/` (see [libs/README.md](libs/README.md)); they are gitignored
and never redistributed here.

## Compatibility model

Every patch **self-gates**: it activates only when the mods it bridges are installed, and stays dormant
(and harmless) otherwise. Safe to keep loaded with any subset of the target mods.

## License

[MIT](LICENSE) © leon.raineri
