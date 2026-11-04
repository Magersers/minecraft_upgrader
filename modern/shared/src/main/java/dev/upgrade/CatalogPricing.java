package dev.upgrade;

import dev.upgrade.compat.NaturalInheritance;
import dev.upgrade.core.CatalogCoverage;
import dev.upgrade.core.ContainerAccounting;
import dev.upgrade.core.CostEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import java.util.*;

/** Last-resort registry coverage; exact recipes, loot and attributes always run first. */
public final class CatalogPricing {
    private CatalogPricing() {}
    public static Set<String> estimated=Set.of();
    public static CostEngine.Result complete(CostEngine.Result calibrated,List<CostEngine.Route> routes,Set<String> denied) {
        calibrated=vanillaCake(calibrated,routes);
        Map<String,CostEngine.Value> estimates=new TreeMap<>();
        for (var item:BuiltInRegistries.ITEM) {
            ItemStack stack=item.getDefaultInstance(); String id=Economy.id(stack);
            if (denied.contains(id)||!Economy.plain(stack)||calibrated.values().containsKey(id)) continue;
            double rarity=switch(stack.getRarity()) { case UNCOMMON->2; case RARE->4; case EPIC->8; default->1; };
            // Unknown code-only mod items can hide powerful abilities. Keep their reward
            // price conservative; datapack profiles replace this estimate automatically.
            double cost=BalancePolicy.diamond()*(id.startsWith("minecraft:")?.5:8)*rarity;
            if (BalancePolicy.ordinaryPlant(stack)&&!BalancePolicy.usefulPlant(id) || item instanceof DyeItem) cost=1;
            cost=Math.max(cost,BalancePolicy.floors.getOrDefault(id,0d));
            estimates.put(id,new CostEngine.Value(cost,.85,
                    "Резервная оценка каталога; характеристики/добыча не определены. Ставка не выше 1 E. Точная настройка: upgrade_values.",
                    NaturalInheritance.namespaceGates(id)));
        }
        var completed=CatalogCoverage.complete(calibrated,estimates,routes,PerformancePricing.stakeLimits,
                BalancePolicy.floors,BalancePolicy.catalogCaps());
        Map<String,ContainerAccounting.Remainder> remainders=new TreeMap<>();
        for (var item:BuiltInRegistries.ITEM) {
            var returned=item.getCraftingRemainder();
            if (returned!=null) remainders.put(BuiltInRegistries.ITEM.getKey(item).toString(),
                    new ContainerAccounting.Remainder(BuiltInRegistries.ITEM.getKey(returned.create().getItem()).toString(),returned.count()));
        }
        PerformancePricing.stakeLimits=ContainerAccounting.limit(completed.prices().values(),
                dev.upgrade.core.VanillaAcquisition.stakeLimits(completed.prices().values(),completed.stakeLimits()),
                dev.upgrade.core.VanillaAcquisition.stakeRoutes(routes),remainders);
        estimated=completed.estimated();
        Upgrade.LOGGER.info("Registry coverage: {} additional estimated items; {} total values",estimated.size(),completed.prices().values().size());
        return completed.prices();
    }
    // Vanilla cake returns all three buckets. Correct this proven recipe explicitly;
    // unusual mod byproducts keep conservative reward pricing and capped stakes.
    private static CostEngine.Result vanillaCake(CostEngine.Result result,List<CostEngine.Route> routes) {
        var expected=Map.of("minecraft:milk_bucket",3d,"minecraft:sugar",2d,"minecraft:egg",1d,"minecraft:wheat",3d);
        var vanillaEggs=Set.of("minecraft:egg","minecraft:blue_egg","minecraft:brown_egg");
        var old=result.values().get("minecraft:cake"); var bucket=result.values().get("minecraft:bucket");
        if (old==null||bucket==null||!result.values().keySet().containsAll(expected.keySet())) return result;
        for (var route:routes) {
            if (!route.id().equals("minecraft:cake")||!route.output().equals("minecraft:cake")||route.count()!=1) continue;
            Map<String,Double> actual=new TreeMap<>();
            for (var input:route.inputs()) {
                if (input.alternatives().isEmpty()) { actual.clear(); break; }
                boolean eggs=vanillaEggs.containsAll(input.alternatives());
                if (!eggs && input.alternatives().size()!=1) { actual.clear(); break; }
                actual.merge(eggs?"minecraft:egg":input.alternatives().get(0),input.count(),Double::sum);
            }
            if (!actual.equals(expected)) continue;
            double total=route.overhead()-3*bucket.cost(),confidence=old.confidence();
            Set<String> gates=new TreeSet<>(old.gates()); gates.addAll(route.gates());
            for (var ingredient:route.inputs()) {
                var value=ingredient.alternatives().stream().map(result.values()::get).filter(Objects::nonNull)
                        .min(Comparator.comparingDouble(CostEngine.Value::cost)).orElse(null);
                if (value==null) return result;
                total+=ingredient.count()*value.cost();
                confidence=Math.min(confidence,value.confidence()); gates.addAll(value.gates());
            }
            total=Math.max(total,BalancePolicy.floors.getOrDefault("minecraft:cake",0d));
            if (!Double.isFinite(total)||total<=0||total>=old.cost()) return result;
            Map<String,CostEngine.Value> values=new TreeMap<>(result.values());
            values.put("minecraft:cake",new CostEngine.Value(total,confidence,
                    "Крафт торта: молоко, сахар, яйцо и пшеница; стоимость трёх возвращаемых вёдер вычтена.",gates));
            Map<String,CostEngine.Breakdown> breakdowns=new HashMap<>(result.breakdowns()); breakdowns.remove("minecraft:cake");
            return new CostEngine.Result(Map.copyOf(values),result.quarantined(),result.passes(),Map.copyOf(breakdowns));
        }
        return result;
    }

}
