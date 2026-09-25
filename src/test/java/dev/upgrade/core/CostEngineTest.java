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
}
