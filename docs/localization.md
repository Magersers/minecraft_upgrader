# Localization

Fabric 0.6.5 uses Minecraft's **Options → Language** setting for the upgrade screen,
inventory button, item facts, results, server rejections and command feedback.
Each player sees their own language, even on a shared server. Other languages fall
back to English. Install 0.6.5 on both client and server.

## Supported locales

| Language | Minecraft locale |
|---|---|
| English (US) | `en_us` |
| Russian | `ru_ru` |
| Ukrainian | `uk_ua` |
| German | `de_de` |
| French | `fr_fr` |
| Spanish (Spain / Mexico) | `es_es`, `es_mx` |
| Portuguese (Brazil / Portugal) | `pt_br`, `pt_pt` |
| Italian | `it_it` |
| Polish | `pl_pl` |
| Dutch | `nl_nl` |
| Turkish | `tr_tr` |
| Chinese (Simplified / Traditional) | `zh_cn`, `zh_tw` |
| Japanese | `ja_jp` |
| Korean | `ko_kr` |
| Indonesian | `id_id` |
| Vietnamese | `vi_vn` |
| Arabic | `ar_sa` |
| Hindi | `hi_in` |
| Thai | `th_th` |

This covers 22 locales, not every language Minecraft supports. Item names come
from Minecraft or the owning mod. Technical `/upgrade audit` calculation traces,
datapack-authored reasons, identifiers, logs and legacy Forge releases are outside
the player-interface translation catalog. Existing diagnostic texts are preserved.
Translations were AI-assisted; native-speaker corrections are welcome.

## Contributing a translation

Edit UTF-8 JSON files in `fabric/src/main/resources/assets/upgrade/lang/`.
Use `en_us.json` as the complete key list. Preserve `%s`, `%1$s`, `%2$s` arguments
and `\n` line breaks; indexed arguments may be reordered to suit the language.
Keep button labels short. Minecraft handles glyph selection and right-to-left text.
Number formatting in item tooltips uses the selected locale.

When adding a locale, update `LocalizationTest.LOCALES`, publishing metadata and
the client CI language list. Run `./gradlew -p fabric build` for resource/key checks.
The client smoke test reloads actual Minecraft resources, verifies every translation,
formats result arguments, tests rejection messages and renders normal and compact
screens for all 22 locales on both supported game versions.

```sh
./gradlew -p fabric -Pminecraft_version=1.20.1 build runTestServer
xvfb-run -a ./gradlew -p fabric -Pminecraft_version=1.20.1 \
  -Dupgrade.testLanguages=en_us,de_de,zh_cn,ar_sa runTestClient
```

Repeat with `-Pminecraft_version=1.21.1`. On a desktop with a display, omit
`xvfb-run -a`. Screenshots are stored in the test client's `screenshots/` directory.
The release workflow verifies both runtime JARs contain exactly the committed
translation resources before publishing them with SHA-256 checksums.
