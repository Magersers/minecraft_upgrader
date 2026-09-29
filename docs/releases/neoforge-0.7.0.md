# Modded Item Upgrader 0.7.0 — NeoForge

The current Upgrader gameplay is now available natively on NeoForge. The port uses the same economy, wheel UI, translations, and server validation as Fabric 0.6.5.

## Downloads and installation

| Minecraft | Loader | Java | File |
|---|---|---|---|
| **1.21.1 (recommended)** | NeoForge 21.1.252+ | 21+ | `upgrade-neoforge-1.21.1-0.7.0.jar` |
| 1.21 | NeoForge 21.0.167+ | 21+ | `upgrade-neoforge-1.21-0.7.0.jar` |

Install exactly one matching JAR in `mods` on both the client and server. Single-player is supported. All players must use the same Upgrader version as the server. Fabric API is not required. The files are not interchangeable with Fabric or Forge files, or with other Minecraft versions.

1.21.1 is the primary target because it is an established NeoForge modding baseline, as described by the [NeoForged project](https://neoforged.net/news/26.1release/). 1.21 is provided for existing installations. This is not a claim of measured download-market share.

## Included

- Inventory upgrade button and `/upgrade`, with reward search, stack quantities, item details, and wheel animation.
- Recipe-aware economy, durability pricing, hard mode, datapack balance profiles, and `/upgrade audit`.
- Server-authoritative requests and persistent delayed rewards, including recovery after reconnects and player replacement.
- Native NeoForge networking, player data, mod display names, configuration paths, Forge Energy queries, and fluid-container detection.
- All 22 languages from Fabric 0.6.5, including Russian and English.

## Verification and limits

Publication is gated on builds, economy and localization checks, dedicated-server integration assertions, and client rendering checks for both Minecraft versions. Archive checks verify loader metadata, all translations, common tags, and exclusion of test classes. `SHA256SUMS.txt` covers both release files.

The automated client test renders the catalog, inventory, tooltips, win/loss results, and rejection messages at normal and compact sizes. It is not a manual multiplayer playthrough. Third-party NeoForge modpacks are not certified: unusual machinery, custom item data, and acquisition chains can still require datapack profiles. Compatibility testing previously documented for Fabric does not imply equivalent NeoForge compatibility.

The existing Fabric 0.6.5 and historical Forge releases remain available separately. Back up worlds before changing loaders; this release does not migrate Fabric player data into NeoForge player data.
