package dev.upgrade;
import net.minecraft.resources.Identifier;
public final class Ids {
    private Ids() {}
    public static Identifier of(String value) { return java.util.Objects.requireNonNull(Identifier.tryParse(value),value); }
    public static Identifier of(String namespace,String path) { return of(namespace+":"+path); }
}
