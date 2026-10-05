# Modded Item Upgrader 0.8.0 — Minecraft 26.3

Separate Java 25 ports for Fabric, NeoForge and Forge. Install exactly one loader-specific JAR on both client and server. These files do not support earlier Minecraft versions.

| Loader | Tested version | Additional requirement |
| --- | --- | --- |
| Fabric | Loader 0.19.5 | Fabric API 0.161.0+26.3 |
| NeoForge | 26.3.0.48-beta | None; this NeoForge release is beta |
| Forge | 26.3-66.0.9 | None |

The port adapts rendering, SDL input, networking, registry recipes, item components, item capabilities and player save data to 26.3. It retains the rotating wheel, delayed server-authoritative payout, inventory/reward quantities, configurable economy, hard mode and 22 languages. Shared conventional item tags use the current `c:` namespace.

## Build

Set JAVA_HOME to JDK 25. From this directory:

```powershell
.\gradlew.bat -p fabric clean build
.\gradlew.bat -p neoforge clean build
.\gradlew.bat -p forge clean build
```

JARs are written to each loader's `build/libs`. Never distribute a build compiled with `-PdevTests`: it includes developer-only fixtures.

## Validation

`fabric build` runs the existing economy assertion suite and localization consistency check. The common test sources also exercise actual dedicated-server recipe pricing, delayed/exactly-once payout, invalid requests, stack quantities, hard mode, damaged items and save/load persistence.

```powershell
.\gradlew.bat -p fabric runServer -PdevTests
```

Use the corresponding loader directory for other loaders. Before a server launch, accept Minecraft's EULA and configure the development server. NeoForge's development asset tool additionally requires a discoverable JDK 21.

For connected UI tests, start the local server with `-PdevTests -PkeepServer`, then run the client with `-PdevTests -Pe2e`. Defaults: Fabric 127.0.0.1:25633, NeoForge :25634, Forge :25635. Configure server.properties accordingly, using loopback binding and a disposable test world. Developer fixtures replace the test player's inventory with 32 oak logs.

The client clicks the inventory button, chooses eight planks and performs three real network upgrades, verifying wheel advancement, completion timing and actual inventory changes. Screenshots and pass reports are saved under `run/client`. Without `-Pe2e`, the client smoke test checks rendering in English and Russian. Gradle rejects test runs without a fresh success marker.

The automated scenarios cover vanilla items and the shared economy engine; they do not establish compatibility with every third-party modpack.
