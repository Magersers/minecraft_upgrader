# Modded Item Upgrader 0.9.1 for Minecraft 1.18.2

Install `upgrade-forge-1.18.2-0.9.1.jar` on the client and server. Use Java 17 and Minecraft Forge 40.3.0 or later in the 40.x series.

Open the upgrader with the inventory arrow or `/upgrade` in Survival. Choose items to stake and a reward, then check the displayed probability before spinning. A stake worth 100 E has a 42.5% chance to win a reward worth 200 E.

- The catalog prices items from Minecraft 1.18.2, including amethyst clusters and buds, lush cave plants, deepslate building materials and the *otherside* music disc.
- Raw metal storage blocks account for nine units. Spyglasses, tinted glass and cut copper use the installed recipes and their output quantities.
- Ore and amethyst cluster rewards account for the maximum value of drops a player could stake after mining with Fortune III. The acquisition price still limits the value of a block offered as a stake.
- Budding amethyst, bundles and sculk sensors are excluded from the default 1.18.2 survival catalog.

The inventory wheel, reward multipliers, server-checked stakes, persistent payouts and all 22 interface languages are included. Administrators can inspect item prices with `/upgrade audit`.

On a stock Forge 1.18.2 server the catalog offers 1,003 vanilla items. Examples from the normal-mode audit:

| Item | Reward (E) | Stake (E) |
| --- | ---: | ---: |
| Spore blossom | 1 | 1 |
| Amethyst shard | 30 | 30 |
| Raw copper | 40 | 40 |
| Amethyst cluster | 480 | 120 |
| Deepslate copper ore | 800 | 40 |
| Music disc (*otherside*) | 4,128 | 4,128 |
