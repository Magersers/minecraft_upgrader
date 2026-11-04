package dev.upgrade.core;

import java.util.*;

/** Conservative stake budgets for world drops and crafting that returns buckets, bottles or tools. */
public final class ContainerAccounting {
    private ContainerAccounting() {}
    public record Remainder(String item,int count) {
        public Remainder { if (count<1) throw new IllegalArgumentException("remainder count"); }
    }
    public static Map<String,Double> limit(Map<String,CostEngine.Value> rewards,Map<String,Double> limits,
                                           List<CostEngine.Route> routes,Map<String,Remainder> remainders) {
        Map<String,Double> values=new TreeMap<>();
        for (var entry:rewards.entrySet()) values.put(entry.getKey(),Math.min(entry.getValue().cost(),limits.getOrDefault(entry.getKey(),entry.getValue().cost())));
        Set<String> changing=new HashSet<>();
        int iterations=Math.max(128,values.size()+1);
        for (int pass=0;pass<iterations;pass++) {
            Map<String,Double> next=new TreeMap<>(values); changing.clear();
            for (var route:routes) {
                if (!values.containsKey(route.output())) continue;
                double total=route.overhead(); boolean known=true;
                for (var input:route.inputs()) {
                    double cheapest=Double.POSITIVE_INFINITY;
                    for (String id:input.alternatives()) {
                        Double cost=values.get(id); if (cost==null) continue;
                        var returned=remainders.get(id);
                        double refund=returned==null?0:values.getOrDefault(returned.item(),0d)*returned.count();
                        cheapest=Math.min(cheapest,Math.max(0,cost-refund));
                    }
                    if (!Double.isFinite(cheapest)) { known=false; break; }
                    total+=cheapest*input.count();
                }
                if (!known||!Double.isFinite(total)) continue;
                double cost=Math.max(1e-6,total/route.count()),old=next.get(route.output());
                if (cost<old*(1-1e-10)) { next.put(route.output(),cost); changing.add(route.output()); }
            }
            values=next;
            if (changing.isEmpty()) return Map.copyOf(values);
        }
        // A productive return/crafting loop cannot be sold back as a valuable stake.
        Set<String> cyclic=new HashSet<>(changing); boolean changed;
        do {
            changed=false;
            for (var route:routes) if (values.containsKey(route.output()) && route.inputs().stream()
                    .anyMatch(i->i.alternatives().stream().anyMatch(cyclic::contains))) changed|=cyclic.add(route.output());
        } while(changed);
        for (String id:cyclic) values.computeIfPresent(id,(key,value)->Math.min(value,1e-6));
        return Map.copyOf(values);
    }
}
