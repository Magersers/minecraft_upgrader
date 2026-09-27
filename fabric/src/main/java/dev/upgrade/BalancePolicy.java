package dev.upgrade;

import com.google.gson.*;
import dev.upgrade.core.CostEngine;
import dev.upgrade.compat.NaturalInheritance;
import net.minecraft.core.registries.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Bounds are applied inside the recipe graph, not as a final cosmetic multiplier. */
public final class BalancePolicy {
    private BalancePolicy() {}
    public static Map<String,Double> floors=Map.of("minecraft:diamond",2000d);
    private static final Map<String,Double> caps=new TreeMap<>();
    public static double diamond() { return floors.getOrDefault("minecraft:diamond",2000d); }
    public static double equipmentScale() { return diamond()/120; }
    public static void load(MinecraftServer server) {
        Map<String,Double> minimum=new TreeMap<>(),maximum=new TreeMap<>();
        try (var reader=server.getResourceManager().getResourceOrThrow(Ids.of("upgrade:upgrade_balance/policy.json")).openAsReader()) {
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            read(json.getAsJsonObject("floors"),minimum); read(json.getAsJsonObject("caps"),maximum);
            JsonObject tags=json.getAsJsonObject("tag_floors");
            for (var entry:tags.entrySet()) {
                double cost=entry.getValue().getAsDouble(); validate(cost);
                for (var holder:BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM,Ids.of(entry.getKey()))))
                    minimum.merge(BuiltInRegistries.ITEM.getKey(holder.value()).toString(),cost,Math::max);
            }
            for (var item:BuiltInRegistries.ITEM) {
                var stack=item.getDefaultInstance(); String id=Economy.id(stack);
                if (ordinaryPlant(stack)||item instanceof DyeItem) maximum.merge(id,1d,Math::min);
            }
            // Valuable tagged materials always retain their floor, even if a mod grows them on plants.
            minimum.keySet().forEach(maximum::remove);
            floors=Map.copyOf(minimum); caps.clear(); caps.putAll(maximum);
        } catch (Exception ex) { Upgrade.LOGGER.error("Invalid upgrade_balance/policy.json; retaining previous valid price bounds",ex); }
    }
    private static void read(JsonObject json,Map<String,Double> into) {
        for (var entry:json.entrySet()) {
            double cost=entry.getValue().getAsDouble(); validate(cost);
            if (BuiltInRegistries.ITEM.containsKey(Ids.of(entry.getKey()))) into.put(entry.getKey(),cost);
        }
    }
    private static void validate(double cost) { if (!Double.isFinite(cost)||cost<=0) throw new IllegalArgumentException("Invalid balance price"); }
    public static boolean ordinaryPlant(ItemStack stack) {
        if (stack.is(Items.WITHER_ROSE)) return false;
        return stack.is(ItemTags.FLOWERS) || stack.getItem() instanceof BlockItem item && ordinaryPlant(item.getBlock());
    }
    public static boolean ordinaryPlant(Block block) {
        if (block instanceof WitherRoseBlock) return false;
        return block instanceof BushBlock || block instanceof PinkPetalsBlock || block instanceof GrowingPlantBlock
                || block instanceof VineBlock || block instanceof LeavesBlock || NaturalInheritance.plantBlock(block);
    }
    public static void gatheredPlantDrop(String id) { if (!floors.containsKey(id)) caps.merge(id,1d,Math::min); }
    public static CostEngine.Result solve(Map<String,CostEngine.Value> seeds,List<CostEngine.Route> routes,int passes) {
        return solve(seeds,routes,passes,Map.of());
    }
    public static CostEngine.Result solve(Map<String,CostEngine.Value> seeds,List<CostEngine.Route> routes,int passes,Map<String,Double> extraFloors) {
        Map<String,Double> minimum=new TreeMap<>(floors),maximum=new TreeMap<>(caps);
        extraFloors.forEach((id,cost)->minimum.merge(id,cost,Math::max));
        minimum.keySet().forEach(maximum::remove);
        return CostEngine.solve(seeds,routes,passes,minimum,maximum);
    }
}
