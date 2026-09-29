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
            if (price!=null && BalancePolicy.ordinaryPlant(item.getDefaultInstance()) && !BalancePolicy.usefulPlant(id))
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
        coherentRecipes(ctx);
        double baseWeapon=PerformanceMath.weapon(7,1.6,1561,0,1);
        ctx.assertTrue(Math.abs(PerformanceMath.weapon(14,1.6,1561,0,1)/baseWeapon-8)<1e-8,"Double damage costs eight times as much with other stats equal");
        double baseArmor=PerformanceMath.armor(3,2,429,3,429,1);
        ctx.assertTrue(Math.abs(PerformanceMath.armor(6,2,429,3,429,1)/baseArmor-8)<1e-8,"Double armour costs eight times as much with other stats equal");
        if (LoaderPlatform.isLoaded("betterend")) {
            var raw=prices.get("betterend:hydralux_petal");var enchanted=prices.get("betterend:enchanted_petal");
            ctx.assertTrue(raw!=null && raw.cost()<=1,"A harvested BetterEnd petal is a cheap plant product");
            ctx.assertTrue(enchanted!=null && enchanted.cost()>raw.cost(),"Processed enchanted petals retain their recipe cost");
        }
        Upgrade.LOGGER.info("BALANCE TESTS PASS: 14 petals -> 5 diamonds = {} percent",chance*100);
    }
    private static void coherentRecipes(TestContext ctx) {
        var player=TestPlatform.player(ctx.getLevel());
        boolean hard=PricingPolicy.hard;
        try {
            PricingPolicy.hard=false;
            // Test actual server quotes in both directions, not just an isolated utility formula.
            var examples=Map.ofEntries(
                    Map.entry("wooden_sword",2.5),Map.entry("wooden_pickaxe",4d),
                    Map.entry("stone_sword",2.5),Map.entry("iron_sword",240.5),
                    Map.entry("golden_sword",1000.5),Map.entry("golden_pickaxe",1501d),
                    Map.entry("leather",48d),Map.entry("leather_boots",192d),Map.entry("leather_chestplate",384d),
                    Map.entry("iron_boots",480d),Map.entry("iron_chestplate",960d),
                    Map.entry("golden_boots",2000d),Map.entry("golden_chestplate",4000d),
                    Map.entry("sugar_cane",8d),Map.entry("paper",8d),Map.entry("book",72d),Map.entry("ink_sac",32d),
                    Map.entry("bread",24d),Map.entry("beef",24d),Map.entry("cooked_beef",24.5),Map.entry("baked_potato",12.5));
            // Compatibility packs may intentionally add cheaper recipes; exact vanilla prices are tested without them.
            boolean vanilla=!LoaderPlatform.isLoaded("bclib")
                    && !LoaderPlatform.isLoaded("tconstruct")
                    && !LoaderPlatform.isLoaded("techreborn");
            for (var entry:examples.entrySet()) {
                var stack=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Ids.of("minecraft:"+entry.getKey())));
                var target=Economy.target(player,stack); var stake=Economy.usable(player,stack);
                ctx.assertTrue(target!=null&&stake!=null,"Basic goods available in both directions: "+entry.getKey());
                if (vanilla) {
                    ctx.assertTrue(Math.abs(target.cost()-entry.getValue())<1e-7,"Recipe-consistent target: "+entry.getKey()+" = "+target.cost());
                    ctx.assertTrue(Math.abs(stake.cost()-target.cost())<1e-7,"Same base valuation for ordinary goods: "+entry.getKey());
                }
            }
            double stake=Economy.usable(player,new ItemStack(Items.WOODEN_SWORD)).cost();
            double target=Economy.target(player,new ItemStack(Items.SUGAR_CANE)).cost()*64;
            ctx.assertTrue(CostEngine.chance(stake,target,Economy.EFFICIENCY)<.005,"A wooden sword to a stack of cane must be below half a percent");
            double book=Economy.current.values().get("minecraft:book").cost();
            ctx.assertTrue(book>=3*Economy.current.values().get("minecraft:paper").cost()+Economy.current.values().get("minecraft:leather").cost()-1e-7,"Books reflect paper and leather");
            PricingPolicy.hard=true;
            for (Item item:List.of(Items.SUGAR_CANE,Items.POTATO,Items.BREAD,Items.LEATHER)) {
                var stack=new ItemStack(item);
                ctx.assertTrue(Economy.usable(player,stack).cost()>1,"Hard mode does not mistake useful farming goods for decoration: "+Economy.id(stack));
            }
            Upgrade.LOGGER.info("COHERENT ECONOMY PASS: wood sword {} E -> 64 cane {} E, chance {} percent",stake,target,100*CostEngine.chance(stake,target,Economy.EFFICIENCY));
        } finally { PricingPolicy.hard=hard; }
    }
}
