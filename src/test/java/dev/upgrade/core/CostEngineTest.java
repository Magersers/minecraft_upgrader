package dev.upgrade.core;

import java.util.*;

public class CostEngineTest {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value) throw new AssertionError("check "+checks); }
    private static void near(double a,double b) { check(Math.abs(a-b)<1e-8); }
    private static CostEngine.Value v(double n) { return new CostEngine.Value(n,.95,"test",Set.of()); }
    private static CostEngine.Route r(String out,double count,String in,double needed) {
        return new CostEngine.Route(in+"->"+out,out,count,List.of(new CostEngine.Input(List.of(in),needed)),0,.95,Set.of());
    }
    public static void main(String[] args) {
        var packing=List.of(r("block",1,"ingot",9),r("ingot",9,"block",1));
        var result=CostEngine.solve(Map.of("ingot",v(10)),packing,64);
        near(result.values().get("block").cost(),90); near(result.values().get("ingot").cost(),10);
        check(CostEngine.solve(Map.of(),packing,64).values().isEmpty());
        catalogCoverage();
        containerAccounting();
        vanillaAcquisition();
        var exploit=CostEngine.solve(Map.of("ingot",v(10)),List.of(r("ingot",2,"ingot",1),r("sword",1,"ingot",2)),64);
        check(exploit.quarantined().containsAll(Set.of("ingot","sword"))); check(exploit.values().isEmpty());
        near(CostEngine.solve(Map.of("ingot",v(10),"block",v(45)),packing,64).values().get("ingot").cost(),5);
        var alt=new CostEngine.Route("tag","alloy",2,List.of(new CostEngine.Input(List.of("a","b"),3)),2,.9,Set.of("nether"));
        var a=CostEngine.solve(Map.of("a",v(10),"b",v(4)),List.of(alt),64).values().get("alloy");
        near(a.cost(),7); check(a.gates().contains("nether")); near(a.confidence(),.9);
        // Regression: a known alternative is sufficient; adding an unknown modded log cannot block chests.
        near(CostEngine.solve(Map.of("a",v(10)),List.of(alt),64).values().get("alloy").cost(),16);
        check(!CostEngine.solve(Map.of(),List.of(alt),64).values().containsKey("alloy"));
        var chain=new ArrayList<CostEngine.Route>();
        for(int i=1;i<=300;i++) chain.add(r("item"+i,1,"item"+(i-1),1));
        Collections.reverse(chain);
        var deep=CostEngine.solve(Map.of("item0",v(5)),chain,8);
        near(deep.values().get("item300").cost(),5); check(deep.quarantined().isEmpty());
        var crafting=List.of(r("plank",4,"log",1),r("stick",4,"plank",2),
                new CostEngine.Route("pickaxe","pickaxe",1,List.of(new CostEngine.Input(List.of("diamond"),3),new CostEngine.Input(List.of("stick"),2)),0,.9,Set.of()));
        var crafted=CostEngine.solve(Map.of("log",v(4),"diamond",v(120)),crafting,1);
        near(crafted.values().get("pickaxe").cost(),361);
        var explanation=crafted.breakdowns().get("pickaxe");
        near(explanation.parts().stream().mapToDouble(p -> p.count()*p.unitCost()).sum(),361);
        near(explanation.outputCount(),1); check(explanation.recipe().equals("pickaxe"));
        var equalPaths=List.of(new CostEngine.Route("a_gated","out",1,List.of(new CostEngine.Input(List.of("base"),1)),0,.9,Set.of("nether")),
                new CostEngine.Route("b_free","out",1,List.of(new CostEngine.Input(List.of("base"),1)),0,.9,Set.of()));
        check(CostEngine.solve(Map.of("base",v(5)),equalPaths,1).values().get("out").gates().isEmpty());
        // A cyclic component must not poison unrelated prices or impossible recipes.
        var isolated=new ArrayList<>(List.of(r("bad",2,"bad",1),r("safe",2,"wood",1)));
        isolated.add(new CostEngine.Route("impossible","safe",1,List.of(new CostEngine.Input(List.of("bad"),1),new CostEngine.Input(List.of("missing"),1)),0,.9,Set.of()));
        var isolation=CostEngine.solve(Map.of("bad",v(10),"wood",v(4)),isolated,8);
        near(isolation.values().get("safe").cost(),2); check(isolation.quarantined().contains("bad"));
        // Recipe ordering must not change final costs.
        Collections.reverse(chain); near(CostEngine.solve(Map.of("item0",v(5)),chain,8).values().get("item300").cost(),5);
        near(CostEngine.encounter(100,40,10,.1,100,200,10,.8,.25,2),450);
        near(CostEngine.DEFAULT_EFFICIENCY,.85); near(CostEngine.chance(100,200,.85),.425); near(CostEngine.chance(120,300,.85),.34);
        near(CostEngine.chance(1,1e12,.85),8.5e-13);
        for(int i=1;i<10000;i++) check(CostEngine.chance(i,i+100,.85)*(i+100)<=.85*i+1e-9);
        for(double n:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0}) {
            try { CostEngine.chance(n,300,.85); throw new AssertionError(); } catch(IllegalArgumentException expected) { checks++; }
        }
        check(SearchIndex.matches("алмаз меч","minecraft:diamond_sword","Алмазный меч"));
        check(SearchIndex.matches("БЕРЕЗОВЫЕ", "minecraft:birch_planks", "Берёзовые доски"));
        check(SearchIndex.matches("minecraft:diamond", "minecraft:diamond_sword", "Алмазный меч"));
        check(SearchIndex.matches("diamond_sword", "minecraft:diamond_sword", "Алмазный меч"));
        check(SearchIndex.matches("  IRON   INGOT  ", "minecraft:iron_ingot", "Железный слиток"));
        check(!SearchIndex.matches("золото", "minecraft:diamond_sword", "Алмазный меч"));
        check(SearchIndex.matches("", "minecraft:diamond", "Алмаз"));
        check(!RollTiming.canSettle(79,80,true)); check(RollTiming.canSettle(80,80,true));
        check(!RollTiming.canSettle(80,80,false)); check(!RollTiming.canSettle(279,80,false));
        check(RollTiming.canSettle(280,80,false));
        near(RollTiming.progress(-1),0); near(RollTiming.progress(4000),1); near(RollTiming.turns(0,.4),0); near(RollTiming.turns(1,.4),5.4);
        for(int i=0;i<100;i++) check(RollTiming.turns((i+1)/100.0,.42)>=RollTiming.turns(i/100.0,.42));
        System.out.println("PASS: "+checks+" assertions (economy, Unicode search, payout timing)");
    }
    private static void vanillaAcquisition() {
        Map<String,CostEngine.Value> seeds=new TreeMap<>();
        seeds.put("minecraft:white_concrete_powder",v(.8)); seeds.put("minecraft:copper_block",v(360));
        seeds.put("minecraft:melon",v(36)); seeds.put("minecraft:pumpkin",v(24)); seeds.put("minecraft:moss_block",v(1));
        seeds.put("minecraft:pumpkin_seeds",v(2));
        seeds.put("minecraft:bucket",v(360)); seeds.put("minecraft:netherrack",v(1)); seeds.put("minecraft:bone_meal",v(2));
        seeds.put("minecraft:egg",new CostEngine.Value(2,.95,"configured farm",Set.of("test:farm")));
        Set<String> registry=new HashSet<>(seeds.keySet());
        for (String id:List.of("white_concrete","exposed_copper","weathered_copper","oxidized_copper","waxed_exposed_copper",
                "carved_pumpkin","melon_slice","pale_moss_block","crimson_nylium","warped_nylium","blue_egg","brown_egg",
                "powder_snow_bucket","milk_bucket")) registry.add("minecraft:"+id);
        registry.add("mod:white_concrete");
        var routes=new ArrayList<>(VanillaAcquisition.routes(registry,seeds,Map.of("minecraft:bucket",360d,"minecraft:powder_snow_bucket",368d)));
        routes.add(r("minecraft:melon",1,"minecraft:melon_slice",9));
        var prices=CostEngine.solve(seeds,routes,16,Map.of("minecraft:egg",12d),Map.of());
        near(prices.values().get("minecraft:white_concrete").cost(),.8);
        near(prices.values().get("minecraft:oxidized_copper").cost(),360);
        near(prices.values().get("minecraft:carved_pumpkin").cost(),24);
        near(prices.values().get("minecraft:melon_slice").cost(),12);
        near(prices.values().get("minecraft:melon").cost(),36);
        near(prices.values().get("minecraft:pale_moss_block").cost(),1);
        near(prices.values().get("minecraft:powder_snow_bucket").cost(),368);
        near(prices.values().get("minecraft:milk_bucket").cost(),384);
        near(prices.values().get("minecraft:blue_egg").cost(),12);
        check(prices.values().get("minecraft:brown_egg").gates().equals(Set.of("test:farm")));
        check(prices.values().get("minecraft:warped_nylium").gates().contains("minecraft:story/enter_the_nether"));
        check(!prices.values().containsKey("mod:white_concrete")&&!prices.values().containsKey("minecraft:waxed_exposed_copper"));
        check(prices.quarantined().isEmpty());
        var stake=ContainerAccounting.limit(prices.values(),VanillaAcquisition.stakeLimits(prices.values(),Map.of()),VanillaAcquisition.stakeRoutes(routes),Map.of());
        near(stake.get("minecraft:melon_slice"),36d/9);
        check(9*stake.get("minecraft:melon_slice")<=stake.get("minecraft:melon")+1e-8);
        near(stake.get("minecraft:carved_pumpkin"),16);
        check(stake.get("minecraft:carved_pumpkin")+4*stake.get("minecraft:pumpkin_seeds")<=stake.get("minecraft:pumpkin")+1e-8);
        var capped=CostEngine.solve(seeds,routes,16,Map.of(),Map.of("minecraft:egg",1d));
        near(capped.values().get("minecraft:blue_egg").cost(),1);
        // Explicit coloured-egg profiles are not replaced by the automatic family route.
        seeds.put("minecraft:blue_egg",v(20));
        var explicit=CostEngine.solve(seeds,VanillaAcquisition.routes(registry,seeds,Map.of()),16);
        near(explicit.values().get("minecraft:blue_egg").cost(),20);
    }
    private static void containerAccounting() {
        var values=Map.of("bucket",v(360),"milk",v(384),"sugar",v(8),"egg",v(12),"wheat",v(8),"cake",v(1204),"double_cake",v(2408));
        var cake=new CostEngine.Route("cake","cake",1,List.of(new CostEngine.Input(List.of("milk"),3),new CostEngine.Input(List.of("sugar"),2),
                new CostEngine.Input(List.of("egg"),1),new CostEngine.Input(List.of("wheat"),3)),0,.9,Set.of());
        var returned=Map.of("milk",new ContainerAccounting.Remainder("bucket",1));
        var result=ContainerAccounting.limit(values,Map.of(),List.of(cake,r("double_cake",1,"cake",2)),returned);
        near(result.get("cake"),124); near(result.get("double_cake"),248); near(result.get("bucket"),360);
        // Reusing the three buckets repeatedly only credits actual consumed ingredients.
        near(10*result.get("cake"),10*(3*(384-360)+2*8+12+3*8));
        var catalyst=new CostEngine.Route("catalyst","cake",1,List.of(new CostEngine.Input(List.of("bucket"),1)),0,.9,Set.of());
        var reusable=ContainerAccounting.limit(values,Map.of(),List.of(catalyst),Map.of("bucket",new ContainerAccounting.Remainder("bucket",1)));
        check(reusable.get("cake")<=1e-6);
    }
    private static void catalogCoverage() {
        var calibrated=CostEngine.solve(Map.of("iron",new CostEngine.Value(100,.95,"known",Set.of("nether"))),List.of(),1);
        var machine=new CostEngine.Route("machine_recipe","machine",1,
                List.of(new CostEngine.Input(List.of("iron"),2),new CostEngine.Input(List.of("unknown"),1)),0,.9,Set.of());
        var recipes=List.of(machine,r("iron",10,"unknown",1));
        var complete=CatalogCoverage.complete(calibrated,Map.of("unknown",v(1000),"machine",v(5000)),
                recipes,Map.of("iron",100d),Map.of(),Map.of());
        near(complete.prices().values().get("iron").cost(),100); // Guessed conversion cannot cheapen a calibrated reward.
        near(complete.prices().values().get("machine").cost(),1200); // Exact recipe precedes a generic machine guess.
        check(complete.prices().values().get("machine").gates().contains("nether"));
        near(complete.stakeLimits().get("iron"),.1); // But crafting cannot turn a cheap unknown into inflated stake value.
        check(complete.stakeLimits().get("machine")<=1 && complete.estimated().equals(Set.of("unknown","machine")));
        var reversed=new ArrayList<>(recipes); Collections.reverse(reversed);
        var repeat=CatalogCoverage.complete(calibrated,Map.of("machine",v(5000),"unknown",v(1000)),reversed,Map.of("iron",100d),Map.of(),Map.of());
        check(repeat.prices().values().equals(complete.prices().values())); check(repeat.stakeLimits().equals(complete.stakeLimits()));
        var bad=CatalogCoverage.complete(calibrated,Map.of("bad",v(1000)),List.of(r("bad",2,"bad",1)),Map.of(),Map.of(),Map.of());
        check(!bad.prices().values().containsKey("bad") && bad.prices().quarantined().contains("bad"));
        var alreadyQuarantined=new CostEngine.Result(Map.of(),Set.of("bad"),1,Map.of());
        check(CatalogCoverage.complete(alreadyQuarantined,Map.of("bad",v(1000)),List.of(),Map.of(),Map.of(),Map.of()).prices().values().isEmpty());
        var cyclic=new CostEngine.Route("requires_stage","a",1,List.of(new CostEngine.Input(List.of("b"),1)),0,.9,Set.of("end"));
        var cycle=CatalogCoverage.complete(calibrated,Map.of("a",v(10),"b",v(10)),List.of(cyclic,r("b",1,"a",1)),Map.of(),Map.of(),Map.of());
        check(cycle.prices().values().get("a").gates().contains("end"));
        check(cycle.prices().values().get("b").gates().contains("end"));
        var bounded=CatalogCoverage.complete(calibrated,Map.of("flower",v(1000),"rare",v(1000)),List.of(),Map.of(),Map.of("rare",2000d),Map.of("flower",1d));
        near(bounded.prices().values().get("flower").cost(),1); near(bounded.prices().values().get("rare").cost(),2000);
    }
}
