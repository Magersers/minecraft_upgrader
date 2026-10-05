package dev.upgrade;

import dev.upgrade.compat.EnergyCompat;
import dev.upgrade.compat.EncounterProfiles;
import dev.upgrade.core.CostEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import java.util.*;

/** Utility prices are independent anchors, not additional cheap acquisition routes. */
public final class PerformancePricing {
    private PerformancePricing() {}
    public static Set<String> simple=Set.of();
    public static Map<String,Double> stakeLimits=Map.of();
    public static void attribute(Map<String,double[]> map,String name,double base,double amount,int operation) {
        if (!Double.isFinite(base)||!Double.isFinite(amount)) return;
        String key=name.replace("generic.","").replace("player.","");
        double playerBase=switch(key) { case "attack_damage"->1; case "attack_speed"->4; case "movement_speed"->.1; default->base; };
        var a=map.computeIfAbsent(key,k->new double[]{playerBase,0,0,1});
        if (operation==0) a[1]+=amount;
        else if (operation==1) a[2]+=amount;
        else if (operation==2) a[3]*=1+amount;
    }
    public static Map<String,Double> attributes(Map<String,double[]> accum) {
        Map<String,Double> values=new TreeMap<>();
        accum.forEach((key,a)-> { double n=(a[0]+a[1])*(1+a[2])*a[3]; if (Double.isFinite(n)) values.put(key,n); });
        return values;
    }
    public static boolean equipment(ItemStack stack) {
        return ModernItems.armor(stack) || stack.has(net.minecraft.core.component.DataComponents.TOOL)
                || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem
                || stack.has(net.minecraft.core.component.DataComponents.GLIDER) || stack.getItem() instanceof ShieldItem
                || Platform.attributes(stack,EquipmentSlot.MAINHAND).getOrDefault("attack_damage",1d)>1;
    }
    public static Set<String> allowedKeys(ItemStack stack) {
        var profile=PricingPolicy.profiles.get(Economy.id(stack));
        return profile==null?Set.of():profile.dataKeys();
    }
    public static CostEngine.Value utility(ItemStack stack,Set<String> gates) {
        Item item=stack.getItem(); var profile=PricingPolicy.profiles.get(Economy.id(stack));
        if (!equipment(stack) && profile==null) return null;
        EquipmentSlot slot=ModernItems.slot(stack);
        var stats=Platform.attributes(stack,slot);
        var charge=EnergyCompat.read(stack);
        // A last unit of energy must not receive the same valuation as a fully charged suit.
        if (charge!=null && charge.fraction()<1) {
            var empty=Platform.attributes(item.getDefaultInstance(),slot);
            Map<String,Double> blended=new HashMap<>(stats);
            stats.forEach((key,n)-> { double fallback=switch(key) {
                case "armor"->ModernItems.stat(item,"armor");
                case "armor_toughness"->ModernItems.stat(item,"armor_toughness");
                case "attack_damage"->1; case "attack_speed"->4; case "movement_speed"->.1; case "max_health"->20; default->0;
            };
            double base=empty.getOrDefault(key,fallback); blended.put(key,base+(n-base)*charge.fraction()); });
            stats=blended;
        }
        double durability=stack.getMaxDamage(),cost; String detail;
        if (ModernItems.armor(item.getDefaultInstance())) {
            Item reference=switch(slot) { case HEAD->Items.DIAMOND_HELMET; case LEGS->Items.DIAMOND_LEGGINGS; case FEET->Items.DIAMOND_BOOTS; default->Items.DIAMOND_CHESTPLATE; };
            
            double units=switch(slot) { case HEAD->5; case LEGS->7; case FEET->4; default->8; };
            double protection=stats.getOrDefault("armor",ModernItems.stat(item,"armor"));
            double toughness=stats.getOrDefault("armor_toughness",ModernItems.stat(item,"armor_toughness"));
            cost=PerformanceMath.armor(protection,toughness,durability>0?durability:reference.getDefaultInstance().getMaxDamage(),
                    ModernItems.stat(reference,"armor"),reference.getDefaultInstance().getMaxDamage(),units*120);
            detail=String.format(Locale.ROOT,"Броня %.2f; твёрдость %.2f; прочность %.0f",protection,toughness,durability);
        } else if (item.getDefaultInstance().has(net.minecraft.core.component.DataComponents.GLIDER)) { cost=1200; detail="Основа элитр"; }
        else if (item instanceof ShieldItem) { cost=180*Math.pow(Math.max(1,durability)/336,.35); detail="Щит и прочность "+durability; }
        else if (item instanceof BowItem || item instanceof CrossbowItem) {
            cost=(item instanceof CrossbowItem?360:240)*Math.pow(Math.max(1,durability)/(item instanceof CrossbowItem?465:384),.35);
            detail="Дальний бой: базовая модель типа оружия; нестандартные снаряды требуют профиля";
        } else {
            double damage=stats.getOrDefault("attack_damage",1d),speed=stats.getOrDefault("attack_speed",4d);
            double mining=ModernItems.mining(stack);
            cost=PerformanceMath.weapon(damage,speed,durability>0?durability:1561,mining,mining>0?361:240.5);
            detail=String.format(Locale.ROOT,"Урон %.2f; атак/с %.2f; прочность %.0f; добыча %.2f (алмаз: 7 / 1.6 / 1561 / 8)",damage,speed,durability,mining);
        }
        double extras=Math.max(0,stats.getOrDefault("max_health",20d)-20)*100
                +Math.max(0,stats.getOrDefault("knockback_resistance",0d))*800
                +Math.max(0,stats.getOrDefault("movement_speed",.1)-.1)*8000
                +Math.max(0,stats.getOrDefault("block_interaction_range",4.5)-4.5)*600
                +Math.max(0,stats.getOrDefault("entity_interaction_range",3d)-3)*800;
        cost+=extras;
        if (extras>0) detail+=String.format(Locale.ROOT,"; дополнительные атрибуты +%.2f E",extras*BalancePolicy.equipmentScale());
        if (profile!=null) for (var ability:profile.abilities()) {
            double factor=ability.charged()?(charge==null?0:charge.fraction()):1;
            if (!EnergyCompat.abilityEnabled(Economy.id(stack),ability.type())) factor=0;
            double bonus=PricingPolicy.WEIGHTS.get(ability.type())*ability.strength()*factor;
            cost+=bonus; detail+=String.format(Locale.ROOT,"; %s +%.2f E%s",ability.type(),bonus*BalancePolicy.equipmentScale(),ability.charged()?" (по заряду)":"");
        }
        if (charge!=null) {
            double battery=80*Math.log1p(charge.capacity()/10000d);
            cost+=battery*(.2+.8*charge.fraction());
            detail+=String.format(Locale.ROOT,"; заряд %d/%d",charge.amount(),charge.capacity());
        }
        if (!Double.isFinite(cost)||cost<=0) return null;
        if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft") && profile==null)
            detail+="; скрытые эффекты кода не оценены: нужен профиль upgrade_abilities";
        cost*=BalancePolicy.equipmentScale();
        if (!Double.isFinite(cost)) return null;
        return new CostEngine.Value(cost,.85,"Оценка по характеристикам (алмаз "+BalancePolicy.diamond()+" E): "+detail,gates);
    }
    private static boolean premiumEquipment(String id,CostEngine.Value stats) {
        Item item=BuiltInRegistries.ITEM.getValue(Ids.of(id));
        var profile=PricingPolicy.profiles.get(id);
        if (profile!=null&&!profile.abilities().isEmpty() || EnergyCompat.read(item.getDefaultInstance())!=null) return true;
        Item reference;
        if (ModernItems.armor(item.getDefaultInstance())) reference=switch(ModernItems.slot(item.getDefaultInstance())) {
            case HEAD->Items.DIAMOND_HELMET; case LEGS->Items.DIAMOND_LEGGINGS;
            case FEET->Items.DIAMOND_BOOTS; default->Items.DIAMOND_CHESTPLATE;
        };
        else if (item.getDefaultInstance().is(ItemTags.AXES)) reference=Items.DIAMOND_AXE;
        else if (item.getDefaultInstance().is(ItemTags.PICKAXES)) reference=Items.DIAMOND_PICKAXE;
        else if (item.getDefaultInstance().is(ItemTags.SHOVELS)) reference=Items.DIAMOND_SHOVEL;
        else if (item.getDefaultInstance().is(ItemTags.HOES)) reference=Items.DIAMOND_HOE;
        else if (item instanceof BowItem) reference=Items.BOW;
        else if (item instanceof CrossbowItem) reference=Items.CROSSBOW;
        else if (item instanceof ShieldItem) reference=Items.SHIELD;
        else if (item.getDefaultInstance().has(net.minecraft.core.component.DataComponents.GLIDER)) reference=Items.ELYTRA;
        else reference=Items.DIAMOND_SWORD;
        var baseline=utility(reference.getDefaultInstance(),Set.of());
        return baseline!=null && stats.cost()>baseline.cost()*1.001;
    }
    public static CostEngine.Result apply(MinecraftServer server,CostEngine.Result acquisition,Map<String,CostEngine.Value> baseSeeds,List<CostEngine.Route> routes,Set<String> denied) {
        Map<String,CostEngine.Value> anchors=new TreeMap<>(),equipmentAnchors=new TreeMap<>(); Set<String> inputs=new HashSet<>(),cheap=new HashSet<>();
        routes.forEach(r->r.inputs().forEach(i->inputs.addAll(i.alternatives())));
        for (var item:BuiltInRegistries.ITEM) {
            var stack=item.getDefaultInstance(); String id=Economy.id(stack);
            if (stack.isEmpty()||denied.contains(id)||!Economy.plain(stack)) continue;
            var old=acquisition.values().get(id); Set<String> gates=new TreeSet<>(dev.upgrade.compat.NaturalInheritance.namespaceGates(id)); if (old!=null) gates.addAll(old.gates());
            try {
                var utility=utility(stack,gates); if (utility!=null) equipmentAnchors.put(id,utility);
                if (simpleResource(stack) || decorative(stack,inputs)) cheap.add(id);
                if (decorative(stack,inputs)) {
                    var block=((BlockItem)item).getBlock(); double hardness=block.defaultBlockState().getDestroySpeed(server.overworld(),BlockPos.ZERO);
                    if (hardness>=0 && Double.isFinite(hardness)) anchors.put(id,new CostEngine.Value(1+Math.pow(hardness,1.5),.85,
                            "Приблизительная оценка строительного блока по твёрдости "+hardness+"; не является ингредиентом известных рецептов",gates));
                }
            } catch (RuntimeException ex) { Upgrade.LOGGER.debug("Cannot read item stats {}",id,ex); }
        }
        // Templates are reusable designs but consumed when smithing. Price at least the marginal copy cost.
        Map<String,Double> templateLoot=new HashMap<>();
        for (var table:Platform.lootKeys(server)) if (table.getPath().startsWith("chests/")) {
            try {
                for (var drop:EncounterProfiles.drops(server,table).entrySet())
                    if (BuiltInRegistries.ITEM.getValue(Ids.of(drop.getKey())) instanceof SmithingTemplateItem && drop.getValue()>0)
                        templateLoot.merge(drop.getKey(),128/drop.getValue(),Math::min);
            } catch (Exception ex) { Upgrade.LOGGER.debug("Cannot inspect template loot {}",table,ex); }
        }
        for (var item:BuiltInRegistries.ITEM) if (item instanceof SmithingTemplateItem) {
            String id=BuiltInRegistries.ITEM.getKey(item).toString(); if (denied.contains(id)) continue;
            var old=acquisition.values().get(id); double copy=0;
            for (var r:routes) if (r.output().equals(id)) {
                double self=0,other=r.overhead(); boolean valid=true;
                for (var in:r.inputs()) {
                    if (in.alternatives().size()==1 && in.alternatives().get(0).equals(id)) self+=in.count();
                    else { String chosen=CostEngine.cheapest(in,acquisition.values()); if (chosen==null) { valid=false; break; } other+=acquisition.values().get(chosen).cost()*in.count(); }
                }
                if (valid && self>0 && r.count()>self) { double candidate=other/(r.count()-self); copy=copy==0?candidate:Math.min(copy,candidate); }
            }
            double cost=Math.max(copy,templateLoot.getOrDefault(id,old==null?840:old.cost()));
            if (cost>0 && Double.isFinite(cost)) anchors.put(id,new CostEngine.Value(cost,.85,
                    String.format(Locale.ROOT,"Кузнечный шаблон: копирование %.2f E, поиск %.2f E; используется большая оценка",copy,templateLoot.getOrDefault(id,old==null?840:old.cost())),old==null?Set.of():old.gates()));
        }
        Map<String,CostEngine.Value> knownForReverse=new TreeMap<>(acquisition.values()); knownForReverse.putAll(anchors); knownForReverse.putAll(equipmentAnchors);
        var inferred=PerformanceMath.reverse(equipmentAnchors,knownForReverse,routes,denied);
        Map<String,CostEngine.Value> seeds=new TreeMap<>(baseSeeds); seeds.putAll(inferred); seeds.putAll(anchors);
        // The stake cannot mint utility value by crafting cheap ingredients into expensive equipment/decor.
        Map<String,CostEngine.Value> acquisitionSeeds=new TreeMap<>(baseSeeds); acquisitionSeeds.putAll(inferred);
        anchors.forEach((id,value)-> { if (BuiltInRegistries.ITEM.getValue(Ids.of(id)) instanceof SmithingTemplateItem) acquisitionSeeds.put(id,value); });
        var budget=BalancePolicy.solve(acquisitionSeeds,routes,128);
        Map<String,Double> limits=new TreeMap<>(); budget.values().forEach((id,value)->limits.put(id,value.cost()));
        stakeLimits=Map.copyOf(limits);
        var activeRoutes=routes.stream().filter(r->!anchors.containsKey(r.output())).toList();
        Map<String,Double> equipmentFloors=new TreeMap<>();
        equipmentAnchors.forEach((id,v)-> {
            // Known ordinary gear is worth its recipe, not a fraction of diamond utility.
            // Preserve independent anchors for unknown mod materials and genuinely stronger gear.
            boolean unknown=!budget.values().containsKey(id);
            if (unknown || premiumEquipment(id,v)) equipmentFloors.put(id,v.cost());
            if (unknown) seeds.put(id,v);
        });
        var result=BalancePolicy.solve(seeds,activeRoutes,128,equipmentFloors);
        Map<String,CostEngine.Value> values=new TreeMap<>(result.values());
        Map<String,CostEngine.Breakdown> breakdowns=new HashMap<>(result.breakdowns());
        equipmentAnchors.forEach((id,stats)-> {
            if (!equipmentFloors.containsKey(id)) return;
            var value=values.get(id); if (value==null) return;
            values.put(id,new CostEngine.Value(value.cost(),Math.min(value.confidence(),stats.confidence()),
                    stats.source()+String.format(Locale.ROOT,"; минимум по характеристикам %.2f E; итог также учитывает рецепт/добычу: %s",stats.cost(),value.source()),value.gates()));
            breakdowns.remove(id);
        });
        simple=Set.copyOf(cheap);
        return new CostEngine.Result(Map.copyOf(values),result.quarantined(),result.passes(),Map.copyOf(breakdowns));
    }
    private static boolean simpleResource(ItemStack stack) {
        if (BalancePolicy.usefulPlant(Economy.id(stack)) || Platform.foodValue(stack)>0) return false;
        return stack.is(ItemTags.LOGS)||stack.is(ItemTags.PLANKS)||stack.is(ItemTags.DIRT)
                ||stack.is(Items.STONE)||stack.is(Items.COBBLESTONE)||stack.is(Items.DEEPSLATE)||stack.is(Items.COBBLED_DEEPSLATE)
                ||stack.is(Items.PINK_PETALS)||stack.is(Items.SAND)||stack.is(Items.GRAVEL)
                ||stack.getItem() instanceof BlockItem b && (dev.upgrade.compat.NaturalInheritance.simpleBlock(b.getBlock()) || b.getBlock() instanceof FlowerBlock || b.getBlock() instanceof FlowerBedBlock);
    }
    private static boolean decorative(ItemStack stack,Set<String> inputs) {
        if (!(stack.getItem() instanceof BlockItem item)||inputs.contains(Economy.id(stack))) return false;
        var block=item.getBlock(); var state=block.defaultBlockState();
        if (state.hasBlockEntity() || state.hasAnalogOutputSignal() || block instanceof net.minecraft.world.MenuProvider) return false;
        return block.getClass()==Block.class || block instanceof StairBlock || block instanceof SlabBlock || block instanceof WallBlock
                ||block instanceof FenceBlock || block instanceof RotatedPillarBlock || Platform.glass(block) || block instanceof CarpetBlock;
    }
}
