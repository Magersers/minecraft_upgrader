# Modded Item Upgrader 0.9.0

![Magersers Upgrader](https://media.forgecdn.net/avatars/2076/303/639261725510696238.png)

Choose a stake, compare a reward and its exact success chance, then spin the upgrade wheel. Uses Minecraft items only; no purchases or real-money currency.

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/magersers-upgrader) · [GitHub releases](https://github.com/Magersers/minecraft_upgrader/releases) · [Report an issue](https://github.com/Magersers/minecraft_upgrader/issues)

## What's new

- A charcoal and champagne interface with clearer selection, value and outcome states.
- Drag the wheel marker around its circumference. Its position is remembered; keyboard controls also rotate and reset it. Moving the marker changes presentation only, never the server's probability or result.
- **1.5×, 2×, 5× and 10×** shortcuts beneath the reward find a feasible reward stack near the requested value ratio. Check the actual reward and probability before pressing Upgrade.
- Expanded registry coverage for ordinary vanilla and modded items, including pristine fluid containers. Recipe and acquisition prices remain the preferred source. Items without supported acquisition data receive conservative fallback reward prices and low stake values.
- Balance fixes for expensive crafted blocks, filled buckets and rare vanilla rewards, including the modern 26.3 ports.

[Release details and all nine downloads](https://github.com/Magersers/minecraft_upgrader/releases/tag/v0.9.0).

![Upgrader 0.9.0: reward multiplier shortcuts and movable wheel marker](https://media.forgecdn.net/attachments/2147/350/interface-en-png.png)

## Supported editions

All nine editions use version **0.9.0**. Install the exact matching JAR on both client and server.

| Minecraft | Loader | Java | Additional requirement |
| --- | --- | --- | --- |
| 1.19.2 | Forge 43.5.0+ | 17 | — |
| 1.20.1 | Forge 47.4.10+ | 17 | — |
| 1.20.1 | Fabric 0.17.2+ | 17 | Fabric API 0.92.12+1.20.1 |
| 1.21 | NeoForge 21.0.167+ | 21 | — |
| 1.21.1 | NeoForge 21.1.252+ | 21 | — |
| 1.21.1 | Fabric 0.17.2+ | 21 | Fabric API 0.116.17+1.21.1 |
| 26.3 | Forge 66.0.9+ | 25 | — |
| 26.3 | NeoForge 26.3.0.48-beta | 25 | This loader version is beta |
| 26.3 | Fabric 0.19.5+ | 25 | Fabric API 0.161.0+26.3 |

## Play

1. Press the upgrade arrow in the inventory, or run `/upgrade` in Survival.
2. Select a stake from your main inventory, hotbar or off-hand and choose its quantity.
3. Search the reward catalog by translated item name or registry ID. Choose a reward and quantity, or use a multiplier shortcut.
4. Review the displayed values and probability, then press Upgrade.

A failed upgrade consumes the stake. A successful upgrade delivers the reward after the animation. Accepted rewards survive reconnects and deaths until delivered. The server checks inventory contents, quantities, item data and progression gates.

The probability remains `min(95%, 85% × stake value / reward value)`. The reward must cost more than the stake; a 100 E stake against a 200 E reward has a 42.5% chance. Changing the wheel marker has no effect on this calculation.

The interface follows Minecraft's language setting, with 22 locales and English fallback. Item names use the installed game and mods' translations.

## Catalog and modpack balance

The catalog evaluates registered item IDs using supported recipes, tags, attributes and acquisition sources. Unresolved ordinary items can now appear with conservative fallback pricing. Fallback estimates are visible in `/upgrade audit` output and can be replaced through datapack profiles; their stake value is deliberately limited and that limit propagates through supported recipes.

Administrative blocks, spawn eggs, explicitly excluded items and quarantined unsafe recipe cycles remain excluded. A default registered item is different from every possible enchantment, potion, custom NBT or component combination: unusual variants and unsupported machines may still need a dedicated adapter or value profile. Containers with stored inventories or custom item data are not treated as pristine items; ordinary fluid buckets remain supported. Test your modpack's economy before changing an existing server's profiles.

Run `/upgrade audit` to write `logs/upgrade-economy.json`; use `/reload` after changing datapack profiles. Optional hard mode reduces the stake value of abundant resources.

## License and credits

Created by **Magersers**. Releases starting with 0.6.4 use the **Magersers Proprietary License — All Rights Reserved**. Gameplay and private use are allowed; reuploads and distribution of modified versions require written permission. Modpack manifests may reference the official download.

Development, translations, and page text used substantial AI assistance. The square project artwork is AI-generated promotional art. The 0.9.0 interface image is an in-game screenshot; older gallery captures show earlier releases. The mod does not call an AI service during gameplay.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
