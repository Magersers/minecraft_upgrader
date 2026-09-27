package dev.upgrade.compat;

import net.minecraft.world.level.block.*;
import java.util.*;

/** Optional semantic interfaces used by BetterX; inherited interfaces cover whole block families. */
final class NaturalFamilies {
    private static final List<String> NAMES=List.of("Plant","PlantLike","Leaves","Sapling","SaplingLike","Seed","SeedLike",
            "WaterPlant","WaterPlantLike","WaterPlantSapling","WaterPlantSeed","Vine","ClimableVine","ShearablePlant",
            "Sand","Snow","Ice","Stone","Wood","Ore","Obsidian","Glass");
    private static final Map<Class<?>,String> TYPES=load();
    private static Map<Class<?>,String> load() {
        Map<Class<?>,String> types=new LinkedHashMap<>();
        for (String name:NAMES) try { types.put(Class.forName("org.betterx.bclib.behaviours.interfaces.Behaviour"+name),name); }
        catch (ClassNotFoundException ignored) {}
        return types;
    }
    static String family(Block block) {
        for (var e:TYPES.entrySet()) if (e.getKey().isInstance(block)) return e.getValue();
        if (block instanceof GrowingPlantBlock || block instanceof VineBlock || block instanceof CactusBlock) return "Plant";
        if (block.defaultBlockState().is(net.minecraft.tags.BlockTags.SAND)) return "Sand";
        return null;
    }
    static Map<String,Double> oreDrops(Block block) {
        for (Class<?> type=block.getClass();type!=null;type=type.getSuperclass()) if (type.getName().equals("org.betterx.bclib.blocks.BaseOreBlock")) {
            try {
                var itemField=type.getDeclaredField("dropItem"); var minField=type.getDeclaredField("minCount"); var maxField=type.getDeclaredField("maxCount");
                itemField.setAccessible(true); minField.setAccessible(true); maxField.setAccessible(true);
                var item=(net.minecraft.world.item.Item)((java.util.function.Supplier<?>)itemField.get(block)).get();
                int min=minField.getInt(block),max=maxField.getInt(block);
                if (min<0 || max<min || max==0) return null;
                return Map.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),(min+(double)max)/2);
            } catch (ReflectiveOperationException | RuntimeException ex) { return null; }
        }
        return null;
    }
    static double effort(String family) {
        return switch(family) { case "Glass"->64; case "Ore"->32; case "Obsidian"->24; case "Wood"->6; case "Stone","Ice"->4; default->3; };
    }
}
