package dev.upgrade;

import dev.upgrade.core.CostEngine;
import net.minecraft.world.item.*;
import java.util.*;

final class PerformanceTests {
    static void run(TestContext ctx,net.minecraft.server.MinecraftServer server) throws Exception {
        var base=new CostEngine.Value(200,.85,"Independent boot stats",Set.of("test:gate"));
        var steel=new CostEngine.Input(List.of("test:steel"),4);
        var crystal=new CostEngine.Input(List.of("test:crystal"),1);
        var pure=new CostEngine.Route("test:pure","test:boots",1,List.of(steel),0,.9,Set.of());
        var mixed=new CostEngine.Route("test:mixed","test:boots",1,List.of(steel,crystal),0,.9,Set.of());
        var inferred=PerformanceMath.reverse(Map.of("test:boots",base),Map.of(),List.of(pure),Set.of());
        ctx.assertTrue(inferred.get("test:steel").cost()==50,"Four ingots inherit 50 E each from 200 E boots");
        inferred=PerformanceMath.reverse(Map.of("test:boots",base),Map.of(),List.of(mixed),Set.of());
        ctx.assertTrue(inferred.get("test:steel").cost()==25 && inferred.get("test:crystal").cost()==100,"Scarce ingredient receives a larger per-unit share");
        ctx.assertTrue(inferred.get("test:crystal").gates().contains("test:gate"),"Reverse prices retain progression");
        inferred=PerformanceMath.reverse(Map.of("test:boots",base),Map.of("test:steel",new CostEngine.Value(30,.9,"Known",Set.of())),List.of(mixed),Set.of());
        ctx.assertTrue(!inferred.containsKey("test:steel") && inferred.get("test:crystal").cost()==80,"Subtract known costs before estimating unknown materials");
        var ambiguous=new CostEngine.Route("test:tag","test:boots",1,List.of(new CostEngine.Input(List.of("a","b"),4)),0,.9,Set.of());
        ctx.assertTrue(PerformanceMath.reverse(Map.of("test:boots",base),Map.of(),List.of(ambiguous),Set.of()).isEmpty(),"An ingredient tag cannot identify one material price");
        var cycle=new CostEngine.Route("test:cycle","test:steel",1,List.of(new CostEngine.Input(List.of("test:boots"),1)),0,.9,Set.of());
        ctx.assertTrue(PerformanceMath.reverse(Map.of("test:boots",base),Map.of(),List.of(pure,cycle),Set.of()).size()==1,"No feedback through reverse recipe cycles");
        double weapon=PerformanceMath.weapon(7,1.6,1561,0,240.5);
        ctx.assertTrue(PerformanceMath.weapon(14,1.6,1561,0,240.5)>=weapon*8-1e-7,"High damage increases utility substantially");
        ctx.assertTrue(PerformanceMath.weapon(7,1.6,3122,0,240.5)>weapon,"Higher durability increases value");
        ctx.assertTrue(PerformanceMath.armor(6,4,429,3,429,480)>480,"Protection and toughness increase armour value");
        var sword=new ItemStack(Items.DIAMOND_SWORD); var netherite=new ItemStack(Items.NETHERITE_SWORD);
        ctx.assertTrue(PerformancePricing.utility(netherite,Set.of()).cost()>PerformancePricing.utility(sword,Set.of()).cost(),"Read actual registered weapon stats");
        ctx.assertTrue(Math.abs(PerformancePricing.utility(sword,Set.of()).cost()-240.5*BalancePolicy.equipmentScale())<.01,"Diamond sword reference");
        var profiles=PricingPolicy.profiles;
        try {
            var altered=new HashMap<>(profiles);
            altered.put("minecraft:diamond_sword",new PricingPolicy.Profile(List.of(new PricingPolicy.Ability("poison",1,false)),Set.of()));
            PricingPolicy.profiles=altered;
            ctx.assertTrue(PerformancePricing.utility(sword,Set.of()).cost()>weapon*BalancePolicy.equipmentScale()*5,"Poison profile substantially raises weapon value");
        } finally { PricingPolicy.profiles=profiles; }
        var player=TestPlatform.player(server.overworld());
        var original=Economy.current;
        var unlocked=new HashMap<String,CostEngine.Value>(); original.values().forEach((id,v)->unlocked.put(id,new CostEngine.Value(v.cost(),v.confidence(),v.source(),Set.of())));
        Economy.current=new Economy.Snapshot(original.revision(),unlocked,original.denied(),original.explanations());
        boolean hard=PricingPolicy.hard;
        try {
            PricingPolicy.setHard(true); PricingPolicy.hard=false; PricingPolicy.load(server);
            ctx.assertTrue(PricingPolicy.hard,"Hard mode survives loading saved server config");
        } finally { PricingPolicy.setHard(hard); }
        try {
            PricingPolicy.hard=false;
            // Energy pricing is checked with progression unlocked, like the wear checks below.
            if (LoaderPlatform.isLoaded("techreborn")) energy(ctx,server);
            double pristine=Economy.usable(player,sword).cost();
            sword.setDamageValue(sword.getMaxDamage()/2);
            ctx.assertTrue(Math.abs(Economy.usable(player,sword).cost()/pristine-PerformanceMath.wear(sword.getDamageValue(),sword.getMaxDamage()))<1e-9,"Use actual inventory wear");
            sword.setDamageValue(sword.getMaxDamage()-1);
            ctx.assertTrue(Math.abs(Economy.usable(player,sword).cost()/pristine-.01)<1e-9,"Near-broken equipment loses 99 percent");
            TestPlatform.name(sword); ctx.assertTrue(Economy.usable(player,sword)==null,"Damage normalization must not erase custom item data");
            var log=new ItemStack(Items.OAK_LOG); double target=Economy.target(player,log).cost();
            PricingPolicy.hard=true;
            ctx.assertTrue(Economy.usable(player,log).cost()<=1 && Economy.target(player,log).cost()==target,"Hard mode lowers stakes but not reward prices");
            ctx.assertTrue(Economy.target(player,new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)).cost()>=14000,"Template includes seven-diamond marginal copy cost");
        } finally { Economy.current=original; PricingPolicy.hard=hard; }
    }
    private static void energy(TestContext ctx,net.minecraft.server.MinecraftServer server) throws Exception {
        Class<?> api=Class.forName("team.reborn.energy.api.base.SimpleEnergyItem");
        for (String id:List.of("techreborn:nano_chestplate","techreborn:quantum_chestplate")) {
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Ids.of(id));
            ctx.assertTrue(item!=Items.AIR && dev.upgrade.compat.EnergyCompat.read(item.getDefaultInstance())!=null,"Real supported energy armour: "+id);
            var empty=item.getDefaultInstance();
            var full=empty.copy(); long capacity=dev.upgrade.compat.EnergyCompat.read(full).capacity();
            api.getMethod("setStoredEnergyUnchecked",ItemStack.class,long.class).invoke(null,full,capacity);
            var half=empty.copy(); api.getMethod("setStoredEnergyUnchecked",ItemStack.class,long.class).invoke(null,half,capacity/2);
            var last=empty.copy(); api.getMethod("setStoredEnergyUnchecked",ItemStack.class,long.class).invoke(null,last,1L);
            var a=dev.upgrade.compat.EnergyCompat.read(full);
            ctx.assertTrue(a!=null && a.capacity()==capacity && a.fraction()==1,"Read actual Energy API capacity and charge");
            var low=PerformancePricing.utility(empty,Set.of()); var middle=PerformancePricing.utility(half,Set.of()); var high=PerformancePricing.utility(full,Set.of());
            var minimal=PerformancePricing.utility(last,Set.of());
            ctx.assertTrue(minimal.cost()>=low.cost() && minimal.cost()<middle.cost(),"One unit of charge cannot lower value or count as a full battery");
            ctx.assertTrue(high.cost()>middle.cost() && middle.cost()>low.cost(),"Value actual charge, not merely presence of energy");
            ctx.assertTrue(dev.upgrade.compat.EnergyCompat.read(full).amount()==capacity,"Pricing must not consume energy");
            ctx.assertTrue(Economy.plain(Platform.pricingCopy(full,PerformancePricing.allowedKeys(full))),"Known energy metadata may be priced safely");
            var quote=Economy.usable(TestPlatform.player(server.overworld()),full);
            var emptyQuote=Economy.usable(TestPlatform.player(server.overworld()),empty);
            ctx.assertTrue(quote!=null && emptyQuote!=null && quote.cost()>emptyQuote.cost(),"Real charge increases the acquisition-backed inventory stake");
            if (id.contains("quantum")) ctx.assertTrue(high.cost()>12000,"Quantum flight and shield carry a substantial premium");
            Upgrade.LOGGER.info("Energy valuation {}: empty={}, half={}, full={}",id,low.cost(),middle.cost(),high.cost());
        }
        Upgrade.LOGGER.info("TECHREBORN ENERGY TESTS PASS");
    }
}
