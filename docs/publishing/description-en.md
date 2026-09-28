# Magersers’ Upgrader

Turn spare items into a chance at a better reward. Choose a stake from your inventory, pick an item and quantity, check the odds, and spin the upgrade wheel.

**The stake is consumed on both success and failure.** A successful reward is delivered after the animation ends. Everything uses in-game items; there are no purchases or real-money features.

## What it adds

- An upgrade button in the player inventory and a dedicated upgrade screen.
- Inventory selection, a searchable reward catalog, and stack-sized rewards.
- A spinning wheel with a triangular pointer, sound, and clear win/loss results.
- Compact item cards with the mod name, damage/armour stats, and final value.
- Recipe-based prices for ordinary equipment and processed goods. Useful crops, leather, ink and food have their own prices instead of being treated as decoration.
- Additional valuation for supported powerful equipment, ability profiles and charged energy equipment. Worn items can lose up to 99% of their stake value.
- An optional hard mode that lowers the stake value of simple building resources.
- Server-side validation of inventory, quantities, progression and payouts.

## Installation

Choose the file for **exactly your Minecraft version**. Install Fabric Loader, Fabric API and this mod on both the client and server. Single-player works through the integrated server.

| Minecraft | Java | Fabric Loader | Fabric API |
|---|---|---|---|
| 1.20.1 | 17+ | 0.17.2+ | 0.92.12+1.20.1 or compatible newer build |
| 1.21.1 | 21+ | 0.17.2+ | 0.116.17+1.21.1 or compatible newer build |

Open your inventory and press the upgrade-arrow button. Choose your stake, switch to the reward catalog, select the reward and count, then review the displayed chance before spinning.

Version **0.6.5** localizes the interface, tooltips, results and server messages in **22 locales**, following Minecraft’s selected language with English fallback. Item names use translations from Minecraft and installed mods. Technical audit traces retain their original text. Please report translation and compatibility issues through GitHub.

## Modpacks and balance

The economy reads supported recipes, item attributes, tags and acquisition information from installed mods. BetterX and TechReborn have integration checks on Fabric 1.20.1. This does not mean every mod or complete modpack is supported: custom machines, unusual item data and hidden abilities may require a datapack profile. Full Better MC BMC2 has not been verified as a whole.

With standard vanilla recipes, a wooden sword costs 2.5 E, leather 48 E, paper 8 E and a book 72 E. Modpack recipes can change the results. Administrators can use `/upgrade audit` for a price report and `/upgrade hard true` to enable the extra restriction on simple resource stakes.

A separate historical Forge 1.20.1 build exists. It has an older feature set and its original license; these Fabric downloads do not run on Forge.

## License and credits

© 2026 Magersers. **All Rights Reserved — Magersers Proprietary License**, starting with 0.6.4. Gameplay and private use are allowed; reuploads and distributing modified versions require written permission. Modpack manifests may reference official downloads. Earlier license grants and third-party rights are unaffected. The full LICENSE is included in each JAR and the publication package.

Development and page text used substantial AI assistance. The cover/icon is AI-generated promotional artwork, not a screenshot; the gallery contains captures of the mod’s rendered interface with demonstration items. The mod does not call an AI service during gameplay.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
