package dev.upgrade.core;

import java.util.*;

/** Completes missing registry prices without replacing calibrated reward prices. */
public final class CatalogCoverage {
    private CatalogCoverage() {}
    public record Completion(CostEngine.Result prices, Map<String,Double> stakeLimits, Set<String> estimated) {}

    public static Completion complete(CostEngine.Result calibrated, Map<String,CostEngine.Value> estimates,
                                      List<CostEngine.Route> routes, Map<String,Double> existingLimits,
                                      Map<String,Double> floors, Map<String,Double> caps) {
        Map<String,CostEngine.Value> missing=new TreeMap<>(estimates);
        calibrated.values().keySet().forEach(missing::remove);
        calibrated.quarantined().forEach(missing::remove);
        if (missing.isEmpty()) return new Completion(calibrated,Map.copyOf(existingLimits),Set.of());

        // Calibrated outputs are immutable. In particular, a guessed material must not
        // make a previously expensive reward cheap through a mod's conversion recipe.
        var eligible=routes.stream().filter(r->missing.containsKey(r.output()))
                .filter(r->r.inputs().stream().noneMatch(i->i.alternatives().stream().anyMatch(calibrated.quarantined()::contains))).toList();
        Set<String> outputs=new HashSet<>(); eligible.forEach(r->outputs.add(r.output()));
        Map<String,CostEngine.Value> seeds=new TreeMap<>(calibrated.values());
        missing.forEach((id,value)-> { if (!outputs.contains(id)) seeds.put(id,value); });
        var first=CostEngine.solve(seeds,eligible,128,floors,caps);
        // Copy/packing cycles without a known base and special recipes still need an
        // estimate. Preserve every known ingredient gate, including transitive gates.
        Map<String,Set<String>> gates=new HashMap<>();
        calibrated.values().forEach((id,v)->gates.put(id,new TreeSet<>(v.gates())));
        missing.forEach((id,v)->gates.put(id,new TreeSet<>(v.gates())));
        boolean changed;
        do {
            changed=false;
            for (var r:eligible) {
                Set<String> g=gates.get(r.output());
                changed|=g.addAll(r.gates());
                for (var input:r.inputs()) for (String id:input.alternatives())
                    changed|=g.addAll(gates.getOrDefault(id,Set.of()));
            }
        } while(changed);
        Map<String,CostEngine.Value> finalSeeds=new TreeMap<>(seeds);
        missing.forEach((id,v)-> {
            if (!first.values().containsKey(id) && !first.quarantined().contains(id))
                finalSeeds.put(id,new CostEngine.Value(v.cost(),v.confidence(),v.source(),gates.get(id)));
        });
        var filled=CostEngine.solve(finalSeeds,eligible,128,floors,caps);
        Map<String,CostEngine.Value> values=new TreeMap<>(calibrated.values());
        Map<String,CostEngine.Breakdown> breakdowns=new HashMap<>(calibrated.breakdowns());
        Set<String> estimated=new TreeSet<>();
        filled.values().forEach((id,v)-> {
            if (missing.containsKey(id)) { values.put(id,v); estimated.add(id); }
        });
        filled.breakdowns().forEach((id,v)-> { if (estimated.contains(id)) breakdowns.put(id,v); });

        // Unknown acquisition is never a licence to mint stake value. A conservative
        // one-E source cap also flows through crafting into otherwise known items.
        Map<String,CostEngine.Value> budgetSeeds=new TreeMap<>();
        values.forEach((id,v)->budgetSeeds.put(id,new CostEngine.Value(
                Math.min(v.cost(),estimated.contains(id)?1d:existingLimits.getOrDefault(id,v.cost())),
                v.confidence(),v.source(),v.gates())));
        var budget=CostEngine.solve(budgetSeeds,routes,128);
        Map<String,Double> limits=new TreeMap<>(existingLimits);
        values.forEach((id,v)-> {
            var available=budget.values().get(id);
            if (available!=null) limits.merge(id,Math.min(v.cost(),available.cost()),Math::min);
            else if (budget.quarantined().contains(id)) limits.merge(id,.01,Math::min);
        });
        Set<String> quarantined=new TreeSet<>(calibrated.quarantined());
        quarantined.addAll(first.quarantined()); quarantined.addAll(filled.quarantined());
        return new Completion(new CostEngine.Result(Map.copyOf(values),Set.copyOf(quarantined),
                calibrated.passes()+filled.passes(),Map.copyOf(breakdowns)),Map.copyOf(limits),Set.copyOf(estimated));
    }
}
