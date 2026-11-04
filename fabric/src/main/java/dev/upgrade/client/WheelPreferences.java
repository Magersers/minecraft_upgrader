package dev.upgrade.client;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Small client-only preference file; gameplay and server state are never saved here. */
final class WheelPreferences {
    private WheelPreferences() {}
    private static Path file(Path gameDirectory) { return gameDirectory.resolve("config").resolve("upgrade-client.properties"); }
    static double load(Path gameDirectory) {
        try (var input=Files.newInputStream(file(gameDirectory))) {
            Properties settings=new Properties(); settings.load(input);
            return UpgradeUiMath.normalize(Double.parseDouble(settings.getProperty("wheelAngle","0")));
        } catch (IOException|IllegalArgumentException ignored) { return 0; }
    }
    static void save(Path gameDirectory,double angle) {
        Path destination=file(gameDirectory),temporary=destination.resolveSibling("upgrade-client.properties.tmp");
        Properties settings=new Properties();
        try {
            Files.createDirectories(destination.getParent());
            if (Files.isRegularFile(destination)) try (var input=Files.newInputStream(destination)) { settings.load(input); }
            settings.setProperty("wheelAngle",Double.toString(UpgradeUiMath.normalize(angle)));
            try (var output=Files.newOutputStream(temporary)) { settings.store(output,"Upgrader client preferences"); }
            try { Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException|IllegalArgumentException ignored) { /* Keep playing when the configuration is read-only. */ }
    }
}
