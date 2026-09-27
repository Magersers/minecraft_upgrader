package dev.upgrade;
import net.minecraft.resources.ResourceLocation;
public final class Ids {
    private Ids() {}
    public static ResourceLocation of(String value) { return java.util.Objects.requireNonNull(ResourceLocation.tryParse(value),value); }
    public static ResourceLocation of(String namespace,String path) { return of(namespace+":"+path); }
}
