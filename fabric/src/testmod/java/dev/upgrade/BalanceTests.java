package dev.upgrade;

import dev.upgrade.core.CostEngine;
import net.minecraft.world.item.*;
import java.util.*;

final class BalanceTests {
    static void run(TestContext ctx) {
        var prices=Economy.current.values();
        ctx.assertTrue(prices.get("minecraft:diamond").cost()>=2000,"Diamonds retain the new mineral floor");
        ctx.assertTrue(prices.get("minecraft:iron_ingot").cost()>=120 && prices.get("minecraft:gold_ingot").cost()>=500,"Metal tiers are consistently expensive");
        ctx.assertTrue(prices.get("minecraft:netherite_sword").cost()>=prices.get("minecraft:diamond_sword").cost()+prices.get("minecraft:netherite_ingot").cost()+prices.get("minecraft:netherite_upgrade_smithing_template").cost(),
                "High-tier gear cannot bypass its expensive crafting ingredients and template");
        double petals=prices.get("minecraft:pink_petals").cost(),diamond=prices.get("minecraft:diamond").cost();
        double chance=CostEngine.chance(14*petals,5*diamond,Economy.EFFICIENCY);
        ctx.assertTrue(chance<=.00119+1e-10,"Fourteen ordinary petals to five diamonds must be <= 0.119 percent, not 50 percent");
        for (var item:net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            String id=Economy.id(item.getDefaultInstance());var price=prices.get(id);
            if (price!=null && BalancePolicy.ordinaryPlant(item.getDefaultInstance()) && !BalancePolicy.floors.containsKey(id))
                ctx.assertTrue(price.cost()<=1,"Ordinary plant price cap: "+id+" = "+price.cost());
        }
        var gates=Set.of("test:rare_stage");
        var seeds=Map.of("diamond",new CostEngine.Value(120,.9,"Legacy cheap profile",gates),
                "petal",new CostEngine.Value(279,.9,"Rare chest",Set.of()));
        var pack=new CostEngine.Route("pack","block",1,List.of(new CostEngine.Input(List.of("diamond"),9)),0,.9,Set.of());
        var unpack=new CostEngine.Route("unpack","diamond",9,List.of(new CostEngine.Input(List.of("block"),1)),0,.9,Set.of());
        var cheap=new CostEngine.Route("cheap_mod_conversion","diamond",1,List.of(new CostEngine.Input(List.of("petal"),1)),0,.9,gates);
        var dye=new CostEngine.Route("dye","dye",2,List.of(new CostEngine.Input(List.of("petal"),1)),0,.9,Set.of());
        var result=CostEngine.solve(seeds,List.of(pack,unpack,cheap,dye),128,Map.of("diamond",2000d),Map.of("petal",1d));
        ctx.assertTrue(result.values().get("diamond").cost()==2000 && result.values().get("block").cost()==18000,"Recipe paths cannot bypass mineral floors, and blocks inherit them");
        ctx.assertTrue(result.values().get("dye").cost()==.5 && result.quarantined().isEmpty(),"Plant cap propagates to batch recipes without creating a false cycle");
        ctx.assertTrue(result.values().get("diamond").gates().containsAll(gates),"Bounds preserve progression gates");
        var unknown=CostEngine.solve(Map.of(),List.of(),128,Map.of("unknown",2000d),Map.of());
        ctx.assertTrue(unknown.values().isEmpty(),"A price floor does not invent acquisition for unknown mod items");
        double woodBudget=2*prices.get("minecraft:oak_planks").cost()+prices.get("minecraft:stick").cost();
        ctx.assertTrue(PerformancePricing.stakeLimits.get("minecraft:wooden_sword")<=woodBudget+1e-8,"Crafting a wooden sword must not mint hundreds of stake value");
        double baseWeapon=PerformanceMath.weapon(7,1.6,1561,0,1);
        ctx.assertTrue(Math.abs(PerformanceMath.weapon(14,1.6,1561,0,1)/baseWeapon-8)<1e-8,"Double damage costs eight times as much with other stats equal");
        double baseArmor=PerformanceMath.armor(3,2,429,3,429,1);
        ctx.assertTrue(Math.abs(PerformanceMath.armor(6,2,429,3,429,1)/baseArmor-8)<1e-8,"Double armour costs eight times as much with other stats equal");
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("betterend")) {
            var raw=prices.get("betterend:hydralux_petal");var enchanted=prices.get("betterend:enchanted_petal");
            ctx.assertTrue(raw!=null && raw.cost()<=1,"A harvested BetterEnd petal is a cheap plant product");
            ctx.assertTrue(enchanted!=null && enchanted.cost()>raw.cost(),"Processed enchanted petals retain their recipe cost");
        }
        Upgrade.LOGGER.info("BALANCE TESTS PASS: 14 petals -> 5 diamonds = {} percent",chance*100);
    }
}
