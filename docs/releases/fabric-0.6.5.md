# Modded Item Upgrader 0.6.5 — Multilingual

The upgrade interface now follows the language selected in Minecraft.

- 22 complete locale catalogs: English, Russian, Ukrainian, German, French,
  Spanish (Spain and Mexico), Portuguese (Brazil and Portugal), Italian, Polish,
  Dutch, Turkish, Simplified and Traditional Chinese, Japanese, Korean, Indonesian,
  Vietnamese, Arabic, Hindi and Thai.
- Translated inventory controls, reward catalog, item tooltips, result messages,
  server validation errors and `/upgrade hard` / `/upgrade audit` feedback.
- English fallback for other languages; each multiplayer client uses its own language.
- Item tooltip numbers follow the selected locale. Long headings stay inside the panel.
- Server rejection messages remain visible until the player changes their selection.
- Automated translation completeness, argument, client rendering, economy and server tests.

## Downloads

- `upgrade-fabric-1.20.1-0.6.5.jar`: Minecraft 1.20.1, Java 17+,
  Fabric Loader 0.17.2+, Fabric API 0.92.12+1.20.1.
- `upgrade-fabric-1.21.1-0.6.5.jar`: Minecraft 1.21.1, Java 21+,
  Fabric Loader 0.17.2+, Fabric API 0.116.17+1.21.1.

Install the matching version on both client and server. Check file integrity with
`SHA256SUMS.txt`. The economy and payout rules are unchanged.

Item names use translations from Minecraft and the owning mods. Technical audit
traces and datapack-authored diagnostic reasons retain their original text.
Translations are AI-assisted and welcome native-speaker review. Older Forge
releases remain separate. See [the localization guide](https://github.com/Magersers/minecraft_upgrader/blob/main/docs/localization.md).
