package dev.upgrade;

import java.nio.file.Path;
import java.util.Optional;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

public final class LoaderPlatform {
    private LoaderPlatform() {}
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    public static Optional<String> modName(String id) {
        return ModList.get().getModContainerById(id).map(mod -> mod.getModInfo().getDisplayName());
    }
    public static boolean isLoaded(String id) { return ModList.get().isLoaded(id); }
}
