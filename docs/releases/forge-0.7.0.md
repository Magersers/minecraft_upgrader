# Modded Item Upgrader 0.7.0 — Forge

The current upgrade wheel, economy and 22 translations are now available natively on Forge.

| Minecraft | Forge | Java | File |
| --- | --- | --- | --- |
| 1.20.1 | 47.4.10+ | 17 | upgrade-forge-1.20.1-0.7.0.jar |
| 1.19.2 | 43.5.0+ | 17 | upgrade-forge-1.19.2-0.7.0.jar |

Install one matching JAR in `mods` on both client and server. Single-player is supported. Fabric API and NeoForge are not required. These files are specific to their Minecraft version.

- Inventory upgrade arrow and `/upgrade`, reward search, stack quantities, detailed item tooltips and animated wheel.
- Recipe-aware economy, durability pricing, hard mode, datapack balance profiles and `/upgrade audit`.
- Server-authoritative networking, delayed rewards and persistent recovery after reconnects or player replacement.
- Native Forge energy/fluid capability checks. The 1.19.2 adapter uses its original recipe, registry, loot and rendering APIs.

Publication requires both builds to pass core and localization checks, dedicated-server integration tests, rendering in all 22 languages, and real connected-client spins. The live tests click the inventory arrow and upgrade button, observe animation, and verify actual stake consumption and outcome-dependent inventory changes. Separate deterministic server tests cover wins, losses, replay rejection and recovery.

These are popular established Forge targets, selected for existing modpack ecosystems; no exact active-player market share is claimed. Third-party modpacks are not certified by the vanilla test suite and may require balance profiles.

SHA256SUMS.txt covers both release files. Previous Fabric, NeoForge and historical Forge releases remain available separately.
