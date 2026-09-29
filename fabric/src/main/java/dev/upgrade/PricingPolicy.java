package dev.upgrade;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.util.*;

public final class PricingPolicy {
    private PricingPolicy() {}
    public record Ability(String type,double strength,boolean charged) {}
    public record Profile(List<Ability> abilities,Set<String> dataKeys) {}
    public static boolean hard=false;
    public static double simpleCap=.5;
    public static Map<String,Profile> profiles=Map.of();
    public static final Map<String,Double> WEIGHTS=Map.ofEntries(Map.entry("flight",12000d),Map.entry("gliding",6000d),
            Map.entry("double_jump",1500d),Map.entry("water_breathing",600d),Map.entry("night_vision",250d),
            Map.entry("poison",1200d),Map.entry("wither",2400d),Map.entry("regeneration",3000d),
            Map.entry("fire_immunity",1800d),Map.entry("fire_extinguish",600d),Map.entry("shield",120d),Map.entry("speed",800d));
    private static Path path() { return LoaderPlatform.configDir().resolve("upgrade-economy.json"); }
    private static JsonObject config=new JsonObject();
    public static void load(MinecraftServer server) {
        try {
            if (!Files.exists(path())) { config=new JsonObject(); config.addProperty("hard_mode",false); config.addProperty("simple_stake_cap",.5); save(); }
            var next=JsonParser.parseString(Files.readString(path())).getAsJsonObject();
            double cap=next.has("simple_stake_cap")?next.get("simple_stake_cap").getAsDouble():.5;
            if (!Double.isFinite(cap)||cap<=0||cap>1) throw new IllegalArgumentException("simple_stake_cap must be in (0,1]");
            hard=next.has("hard_mode")&&next.get("hard_mode").getAsBoolean(); simpleCap=cap; config=next;
        } catch (Exception e) { Upgrade.LOGGER.error("Invalid upgrade-economy.json; retaining previous mode",e); }
        Map<String,Profile> loaded=new TreeMap<>();
        for (var entry:new TreeMap<>(server.getResourceManager().listResources("upgrade_abilities",id->id.getPath().endsWith(".json"))).entrySet()) {
            try (var reader=entry.getValue().openAsReader()) {
                Map<String,Profile> batch=new TreeMap<>();
                for (var el:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("items")) {
                    var row=el.getAsJsonObject(); String id=row.get("item").getAsString(); Ids.of(id);
                    List<Ability> abilities=new ArrayList<>(); Set<String> keys=new TreeSet<>();
                    for (var a:row.getAsJsonArray("abilities")) {
                        var o=a.getAsJsonObject(); String type=o.get("type").getAsString();
                        double strength=o.has("strength")?o.get("strength").getAsDouble():1;
                        if (!WEIGHTS.containsKey(type)||!Double.isFinite(strength)||strength<0) throw new IllegalArgumentException("Invalid ability: "+type);
                        abilities.add(new Ability(type,strength,o.has("charged")&&o.get("charged").getAsBoolean()));
                    }
                    if (row.has("allowed_data_keys")) for (var key:row.getAsJsonArray("allowed_data_keys")) {
                        String name=key.getAsString();
                        if (Set.of("display","Enchantments","AttributeModifiers","BlockEntityTag","Items","Unbreakable").contains(name)) throw new IllegalArgumentException("Unsafe data key");
                        keys.add(name);
                    }
                    batch.put(id,new Profile(List.copyOf(abilities),Set.copyOf(keys)));
                }
                loaded.putAll(batch);
            } catch (Exception ex) { Upgrade.LOGGER.warn("Skipping invalid ability profile {}",entry.getKey(),ex); }
        }
        profiles=Map.copyOf(loaded);
    }
    public static void setHard(boolean enabled) throws java.io.IOException {
        var next=config.deepCopy(); next.addProperty("hard_mode",enabled);
        Files.createDirectories(path().getParent()); Files.writeString(path(),new GsonBuilder().setPrettyPrinting().create().toJson(next));
        config=next; hard=enabled;
    }
    private static void save() throws java.io.IOException { Files.createDirectories(path().getParent()); Files.writeString(path(),new GsonBuilder().setPrettyPrinting().create().toJson(config)); }
}
