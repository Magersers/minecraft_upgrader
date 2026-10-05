package dev.upgrade;

import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

/** Small loader boundary shared by the Fabric and NeoForge gameplay sources. */
public final class LoaderPlatform {
    private LoaderPlatform() {}
    public static Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    public static Optional<String> modName(String id) {
        return FabricLoader.getInstance().getModContainer(id).map(mod -> mod.getMetadata().getName());
    }
    public static boolean isLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
}
