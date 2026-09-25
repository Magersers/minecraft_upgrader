package dev.upgrade.compat;

import com.google.gson.*;
import dev.upgrade.Upgrade;
import dev.upgrade.core.CostEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.Reader;
import java.util.*;

/** Configurable effort model using live mob attributes and the active datapack's baseline (Looting 0) loot. */
public final class EncounterProfiles {
    private EncounterProfiles() {}
    private static double n(JsonObject o,String key,double fallback) { return o.has(key)?o.get(key).getAsDouble():fallback; }
    public static void load(MinecraftServer server,Map<String,CostEngine.Value> seeds) {
        var files=server.getResourceManager().listResources("upgrade_encounters",p -> p.getPath().endsWith(".json"));
        for (var file:new TreeMap<>(files).entrySet()) {
            try (Reader reader=file.getValue().openAsReader()) {
                JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
                Map<String,CostEngine.Value> local=new HashMap<>();
                for (JsonElement element:root.getAsJsonArray("encounters")) {
                    JsonObject profile=element.getAsJsonObject(); ResourceLocation entityId=new ResourceLocation(profile.get("entity").getAsString());
                    if (!ForgeRegistries.ENTITY_TYPES.containsKey(entityId)) continue;
                    var entity=ForgeRegistries.ENTITY_TYPES.getValue(entityId).create(server.overworld());
                    if (!(entity instanceof LivingEntity mob)) continue;
                    double hp=mob.getMaxHealth(),armor=mob.getArmorValue();
                    double attack=mob.getAttribute(Attributes.ATTACK_DAMAGE)==null?2:mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    double dps=n(profile,"player_dps",8),mechanics=n(profile,"mechanics",1);
                    if (!Double.isFinite(dps)||dps<=0||!Double.isFinite(mechanics)||mechanics<1) throw new IllegalArgumentException("Invalid combat model");
                    double effortSeconds=n(profile,"seconds_per_effort",10);
                    if (!Double.isFinite(effortSeconds)||effortSeconds<=0) throw new IllegalArgumentException("Invalid effort conversion");
                    double combat=hp/dps/effortSeconds*(1+armor/20)*mechanics*(1+Math.max(0,attack-2)/20);
                    String table=profile.has("loot_table")?profile.get("loot_table").getAsString():entityId.getNamespace()+":entities/"+entityId.getPath();
                    Map<String,Double> drops=loot(server,new ResourceLocation(table),new HashSet<>());
                    Set<String> gates=new HashSet<>(); if (profile.has("gates")) for (JsonElement gate:profile.getAsJsonArray("gates")) gates.add(gate.getAsString());
                    for (var drop:drops.entrySet()) {
                        double cost=CostEngine.encounter(n(profile,"search",0),combat,n(profile,"consumables",0),n(profile,"death_chance",0),
                                n(profile,"death_loss",0),n(profile,"setup",0),n(profile,"horizon",1),n(profile,"success",1),1,drop.getValue());
                        String reason=String.format(Locale.ROOT,"Добыча %s: HP %.0f, броня %.0f, атака %.1f; DPS игрока %.1f; сложность ×%.2f.\nПоиск %.1f + бой %.1f + расходники %.1f + риск %.2f × %.1f + подготовка %.1f / %.0f.\nУспех %.0f%%; средний дроп %.2f (без Добычи). Параметры: upgrade_encounters.",entityId,hp,armor,attack,dps,mechanics,n(profile,"search",0),combat,n(profile,"consumables",0),n(profile,"death_chance",0),n(profile,"death_loss",0),n(profile,"setup",0),n(profile,"horizon",1),n(profile,"success",1)*100,drop.getValue());
                        CostEngine.Value value=new CostEngine.Value(cost,n(profile,"confidence",.9),reason,gates);
                        local.merge(drop.getKey(),value,(a,b) -> a.cost()<=b.cost()?a:b);
                    }
                }
                local.forEach((id,value) -> seeds.merge(id,value,(a,b) -> a.cost()<=b.cost()?a:b));
            } catch (Exception e) { Upgrade.LOGGER.warn("Skipping invalid encounter profile {}: {}",file.getKey(),e.toString()); }
        }
    }
    private static double mean(JsonElement element) {
        if (element.isJsonPrimitive()) return finite(element.getAsDouble());
        JsonObject value=element.getAsJsonObject(); String type=value.get("type").getAsString();
        return switch (type) {
            case "minecraft:uniform" -> (mean(value.get("min"))+mean(value.get("max")))/2;
            case "minecraft:constant" -> mean(value.get("value"));
            case "minecraft:binomial" -> mean(value.get("n"))*probability(value.get("p").getAsDouble());
            default -> throw new IllegalArgumentException("Unsupported loot number "+type);
        };
    }
    private static double finite(double n) { if (!Double.isFinite(n)||n<0) throw new IllegalArgumentException("Invalid loot amount"); return n; }
    private static double probability(double n) { if (finite(n)>1) throw new IllegalArgumentException("Invalid probability"); return n; }
    private static double conditions(JsonObject o) {
        double chance=1;
        if (o.has("conditions")) for (JsonElement element:o.getAsJsonArray("conditions")) {
            JsonObject condition=element.getAsJsonObject();
            chance*=switch (condition.get("condition").getAsString()) {
                case "minecraft:killed_by_player" -> 1;
                case "minecraft:random_chance", "minecraft:random_chance_with_looting" -> probability(condition.get("chance").getAsDouble());
                default -> throw new IllegalArgumentException("Context-dependent loot condition");
            };
        }
        return chance;
    }
    private static double count(JsonObject o,double initial) {
        double count=initial;
        if (o.has("functions")) for (JsonElement element:o.getAsJsonArray("functions")) {
            JsonObject f=element.getAsJsonObject(); String type=f.get("function").getAsString();
            if (type.equals("minecraft:looting_enchant")) continue; // Explicit Looting 0 baseline.
            if (!type.equals("minecraft:set_count")) throw new IllegalArgumentException("Context-dependent loot function "+type);
            double chance=conditions(f),value=mean(f.get("count"));
            count=f.has("add")&&f.get("add").getAsBoolean()?count+chance*value:count*(1-chance)+chance*value;
        }
        return count;
    }
    private static Map<String,Double> loot(MinecraftServer server,ResourceLocation id,Set<ResourceLocation> visiting) throws Exception {
        if (!visiting.add(id)) throw new IllegalArgumentException("Recursive loot table");
        ResourceLocation file=new ResourceLocation(id.getNamespace(),"loot_tables/"+id.getPath()+".json");
        var resource=server.getResourceManager().getResource(file);
        if (resource.isEmpty()) throw new IllegalArgumentException("Missing loot table "+id);
        Map<String,Double> result=new HashMap<>();
        try (Reader reader=resource.get().openAsReader()) {
            JsonObject table=JsonParser.parseReader(reader).getAsJsonObject();
            if (table.has("functions")) throw new IllegalArgumentException("Table-wide loot functions require a dedicated profile");
            if (table.has("pools")) for (JsonElement p:table.getAsJsonArray("pools")) {
                JsonObject pool=p.getAsJsonObject();
                try {
                    var entries=pool.getAsJsonArray("entries");
                    double totalWeight=0;
                    for (JsonElement value:entries) {
                        JsonObject entry=value.getAsJsonObject();
                        if (entries.size()>1&&entry.has("conditions")) throw new IllegalArgumentException("Conditional weighted pool");
                        totalWeight+=finite(n(entry,"weight",1));
                    }
                    if (totalWeight<=0) continue;
                    double rolls=mean(pool.get("rolls"))*conditions(pool);
                    Map<String,Double> poolDrops=new HashMap<>();
                    for (JsonElement value:entries) {
                        JsonObject entry=value.getAsJsonObject();
                        double chance=conditions(entry)*n(entry,"weight",1)/totalWeight;
                        String type=entry.get("type").getAsString();
                        if (type.equals("minecraft:item")) {
                            double amount=count(pool,count(entry,1))*rolls*chance;
                            if (amount>0) poolDrops.merge(entry.get("name").getAsString(),amount,Double::sum);
                        } else if (type.equals("minecraft:loot_table")) {
                            if (pool.has("functions")||entry.has("functions")) throw new IllegalArgumentException("Nested loot functions");
                            loot(server,new ResourceLocation(entry.get("name").getAsString()),visiting).forEach((key,amount) -> poolDrops.merge(key,amount*rolls*chance,Double::sum));
                        } else if (!type.equals("minecraft:empty")) throw new IllegalArgumentException("Unsupported loot entry "+type);
                    }
                    poolDrops.forEach((key,amount) -> result.merge(key,amount,Double::sum));
                } catch (IllegalArgumentException e) { Upgrade.LOGGER.debug("Loot pool {} skipped: {}",id,e.getMessage()); }
            }
        } finally { visiting.remove(id); }
        return result;
    }
}
