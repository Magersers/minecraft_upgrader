package dev.upgrade.core;
import java.util.*;
public class CostEngineTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static void near(double a,double b){check(Math.abs(a-b)<1e-8);}
    private static CostEngine.Value v(double n){return new CostEngine.Value(n,.95,"test",Set.of());}
    private static CostEngine.Route r(String out,double count,String in,double needed){return new CostEngine.Route(in+"->"+out,out,count,List.of(new CostEngine.Input(List.of(in),needed)),0,.95,Set.of());}
    public static void main(String[] args){
        var packing=List.of(r("block",1,"ingot",9),r("ingot",9,"block",1));var result=CostEngine.solve(Map.of("ingot",v(10)),packing,64);
        near(result.values().get("block").cost(),90);near(result.values().get("ingot").cost(),10);check(CostEngine.solve(Map.of(),packing,64).values().isEmpty());
        var exploit=CostEngine.solve(Map.of("ingot",v(10)),List.of(r("ingot",2,"ingot",1),r("sword",1,"ingot",2)),64);check(exploit.quarantined().containsAll(Set.of("ingot","sword")));check(exploit.values().isEmpty());
        near(CostEngine.solve(Map.of("ingot",v(10),"block",v(45)),packing,64).values().get("ingot").cost(),5);
        var alt=new CostEngine.Route("tag","alloy",2,List.of(new CostEngine.Input(List.of("a","b"),3)),2,.9,Set.of("nether"));var a=CostEngine.solve(Map.of("a",v(10),"b",v(4)),List.of(alt),64).values().get("alloy");near(a.cost(),7);check(a.gates().contains("nether"));near(a.confidence(),.9);check(!CostEngine.solve(Map.of("a",v(10)),List.of(alt),64).values().containsKey("alloy"));
        near(CostEngine.encounter(100,40,10,.1,100,200,10,.8,.25,2),450);
        near(CostEngine.DEFAULT_EFFICIENCY,.85);near(CostEngine.chance(100,200,CostEngine.DEFAULT_EFFICIENCY),.425);near(CostEngine.chance(120,300,CostEngine.DEFAULT_EFFICIENCY),.34);near(CostEngine.chance(1,1e12,CostEngine.DEFAULT_EFFICIENCY),8.5e-13);
        for(int i=1;i<10000;i++)check(CostEngine.chance(i,i+100,CostEngine.DEFAULT_EFFICIENCY)*(i+100)<=.85*i+1e-9);
        for(double n:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0}){try{CostEngine.chance(n,300,CostEngine.DEFAULT_EFFICIENCY);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
        System.out.println("PASS: "+checks+" assertions");
    }
}
