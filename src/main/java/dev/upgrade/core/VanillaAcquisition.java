package dev.upgrade.core;

import java.util.*;

/** Proven vanilla world conversions which are absent from the crafting recipe manager. */
public final class VanillaAcquisition {
    private VanillaAcquisition() {}
    private static final List<String> COLOURS=List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray",
            "light_gray","cyan","purple","blue","brown","green","red","black");
    private static final Set<String> NETHER=Set.of("minecraft:story/enter_the_nether");

    public static List<CostEngine.Route> routes(Set<String> registered,Map<String,CostEngine.Value> seeds,Map<String,Double> floors) {
        List<CostEngine.Route> routes=new ArrayList<>();
        for (String colour:COLOURS) add(routes,registered,colour+"_concrete",1,colour+"_concrete_powder",1,0,Set.of());
        // Wax prevents weathering: waxed variants are priced by their real waxing recipes.
        for (String shape:List.of("copper","cut_copper","cut_copper_stairs","cut_copper_slab","chiseled_copper","copper_grate",
                "copper_bulb","copper_door","copper_trapdoor","lightning_rod","copper_bars","copper_chain","copper_lantern",
                "copper_chest","copper_golem_statue")) {
            String previous=shape.equals("copper")?"copper_block":shape;
            for (String stage:List.of("exposed_","weathered_","oxidized_")) {
                String output=stage+shape;
                add(routes,registered,output,1,previous,1,0,Set.of()); previous=output;
            }
        }
        add(routes,registered,"carved_pumpkin",1,"pumpkin",1,0,Set.of());
        // The minimum natural drop is three; stake accounting below uses the nine-drop Fortune maximum.
        add(routes,registered,"melon_slice",3,"melon",1,0,Set.of());
        add(routes,registered,"pale_moss_block",1,"moss_block",1,0,Set.of());
        add(routes,registered,"pale_hanging_moss",1,"moss_carpet",1,0,Set.of());
        for (String nylium:List.of("crimson_nylium","warped_nylium")) {
            if (registered.contains("minecraft:"+nylium) && registered.containsAll(Set.of("minecraft:netherrack","minecraft:bone_meal")))
                routes.add(new CostEngine.Route("upgrade:world/"+nylium,"minecraft:"+nylium,1,
                        List.of(input("netherrack",1),input("bone_meal",1)),0,.9,NETHER));
        }
        // Explicit per-colour acquisition profiles take precedence over these farm equivalents.
        for (String egg:List.of("blue_egg","brown_egg")) if (!seeds.containsKey("minecraft:"+egg))
            add(routes,registered,egg,1,"egg",1,0,Set.of());
        double bucketFloor=floors.getOrDefault("minecraft:bucket",360d);
        for (var entry:Map.of("water_bucket",0d,"milk_bucket",24d,"lava_bucket",40d,"powder_snow_bucket",8d,
                "tadpole_bucket",24d,"axolotl_bucket",90d).entrySet()) {
            double premium=Math.max(0,floors.getOrDefault("minecraft:"+entry.getKey(),bucketFloor+entry.getValue())-bucketFloor);
            add(routes,registered,entry.getKey(),1,"bucket",1,premium,Set.of());
        }
        for (String fish:List.of("cod","salmon","pufferfish","tropical_fish")) {
            if (registered.containsAll(Set.of("minecraft:"+fish,"minecraft:"+fish+"_bucket","minecraft:bucket")))
                routes.add(new CostEngine.Route("upgrade:world/"+fish+"_bucket","minecraft:"+fish+"_bucket",1,
                        List.of(input("bucket",1),input(fish,1)),0,.9,Set.of()));
        }
        return List.copyOf(routes);
    }

    /** Maximum real drops prevent a favourable roll from minting value via world conversions. */
    public static Map<String,Double> stakeLimits(Map<String,CostEngine.Value> values,Map<String,Double> existing) {
        Map<String,Double> result=new TreeMap<>(existing);
        if (values.keySet().containsAll(Set.of("minecraft:pumpkin","minecraft:pumpkin_seeds","minecraft:carved_pumpkin"))) {
            double pumpkin=Math.min(values.get("minecraft:pumpkin").cost(),existing.getOrDefault("minecraft:pumpkin",Double.POSITIVE_INFINITY));
            double seed=Math.min(values.get("minecraft:pumpkin_seeds").cost(),existing.getOrDefault("minecraft:pumpkin_seeds",Double.POSITIVE_INFINITY));
            // Carving leaves the block AND drops four seeds; account for both outputs.
            result.merge("minecraft:carved_pumpkin",Math.max(1e-6,pumpkin-4*seed),Math::min);
        }
        return Map.copyOf(result);
    }
    public static List<CostEngine.Route> stakeRoutes(List<CostEngine.Route> routes) {
        List<CostEngine.Route> result=new ArrayList<>(routes);
        if (routes.stream().anyMatch(r->r.id().equals("upgrade:world/melon_slice")))
            result.add(new CostEngine.Route("upgrade:world/melon_maximum_drop","minecraft:melon_slice",9,
                    List.of(input("melon",1)),0,.9,Set.of()));
        return List.copyOf(result);
    }
    private static CostEngine.Input input(String id,double count) { return new CostEngine.Input(List.of("minecraft:"+id),count); }
    private static void add(List<CostEngine.Route> into,Set<String> registry,String output,double count,String source,double needed,double premium,Set<String> gates) {
        if (registry.contains("minecraft:"+source)&&registry.contains("minecraft:"+output))
            into.add(new CostEngine.Route("upgrade:world/"+output,"minecraft:"+output,count,List.of(input(source,needed)),premium,.9,gates));
    }
}
