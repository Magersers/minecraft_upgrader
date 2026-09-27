package dev.upgrade.compat;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.upgrade.*;
import dev.upgrade.core.CostEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Approximate acquisition sources from runtime registries, never a blanket price for every registered item. */
public final class NaturalInheritance {
    private NaturalInheritance() {}
    private static JsonObject latestPolicy=new JsonObject();
    public static Set<String> namespaceGates(String id) { return gates(latestPolicy,id); }
    public static boolean simpleBlock(Block block) { return Set.of("Stone","Wood","Sand","Snow").contains(java.util.Objects.toString(NaturalFamilies.family(block),"")); }
    public static boolean plantBlock(Block block) {
        String family=java.util.Objects.toString(NaturalFamilies.family(block),"");
        return family.contains("Plant")||family.contains("Vine")||family.contains("Sapling")||family.equals("Leaves");
    }
    private record Source(double cost,String reason) {}
    public static void load(MinecraftServer server,Map<String,CostEngine.Value> seeds,List<CostEngine.Route> routes,
                            Map<String,CostEngine.Value> known,Set<String> denied) {
        JsonObject policy;
        try (var reader=server.getResourceManager().getResourceOrThrow(Ids.of("upgrade:upgrade_inheritance/policy.json")).openAsReader()) {
            policy=JsonParser.parseReader(reader).getAsJsonObject();
            for (String key:List.of("enabled","natural_resources","block_drops","mob_drops","chest_loot"))
                if (policy.has(key) && (!policy.get(key).isJsonPrimitive() || !policy.getAsJsonPrimitive(key).isBoolean())) throw new IllegalArgumentException(key);
            if (policy.has("namespace_gates")) for (var e:policy.getAsJsonObject("namespace_gates").entrySet())
                for (var gate:e.getValue().getAsJsonArray()) if (net.minecraft.resources.ResourceLocation.tryParse(gate.getAsString())==null) throw new IllegalArgumentException("Invalid gate");
            for (String key:List.of("chest_effort","mob_search_effort")) {
                double n=policy.get(key).getAsDouble(); if (!Double.isFinite(n)||n<=0) throw new IllegalArgumentException(key);
            }
        } catch (Exception ex) { Upgrade.LOGGER.warn("Invalid inheritance policy; estimates disabled",ex); return; }
        latestPolicy=policy;
        if (!enabled(policy,"enabled")) return;
        Map<Block,Source> natural=new HashMap<>();
        Set<String> craftable=new HashSet<>(); routes.forEach(r->craftable.add(r.output()));
        // The loaded registry also contains features injected programmatically by mods/datapacks.
        var features=server.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        if (enabled(policy,"natural_resources")) for (var key:features.keySet()) {
            var feature=features.get(key);
            try {
                if (feature.config() instanceof OreConfiguration ore) {
                    double cost=Math.max(4,160.0/Math.max(1,ore.size));
                    for (var target:ore.targetStates) put(natural,target.state.getBlock(),cost,"Генерация руды "+key+", размер жилы "+ore.size);
                } else {
                    ConfiguredFeature.DIRECT_CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),feature).result()
                            .ifPresent(json->states(json,natural,key.toString()));
                }
            } catch (Exception ex) { Upgrade.LOGGER.debug("Cannot inspect feature {}: {}",key,ex.toString()); }
        }
        if (enabled(policy,"natural_resources")) for (Block block:BuiltInRegistries.BLOCK) {
            var state=block.defaultBlockState(); var stack=block.asItem().getDefaultInstance();
            if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft") && !generatedFlower(block,natural)) continue;
            String family=NaturalFamilies.family(block);
            if (family!=null && (!craftable.contains(Economy.id(stack)) || family.equals("Ore")))
                put(natural,block,NaturalFamilies.effort(family),"Природное семейство "+family);
            if ((state.is(BlockTags.LOGS) || stack.is(ItemTags.LOGS))) put(natural,block,6,"Семейство брёвен (тег logs)");
            else if (block instanceof LeavesBlock) put(natural,block,3,"Семейство листвы");
            else if (block instanceof SaplingBlock) put(natural,block,6,"Семейство саженцев");
            else if (block instanceof BushBlock) put(natural,block,4,"Семейство природных растений");
            else if (!natural.containsKey(block) && (block instanceof DropExperienceBlock || state.getTags().anyMatch(t->oreTag(t.location().getPath()))))
                put(natural,block,Math.max(16,16*Math.max(1,state.getDestroySpeed(server.overworld(),BlockPos.ZERO)/3)),"Семейство руд; оценка по твёрдости");
        }
        int sources=0,dropRoutes=0;
        for (var entry:natural.entrySet()) {
            Block block=entry.getKey(); ItemStack stack=block.asItem().getDefaultInstance(); String id=stack.isEmpty()?"upgrade:harvest/"+BuiltInRegistries.BLOCK.getKey(block).toString().replace(':','/'):Economy.id(stack);
            var state=block.defaultBlockState();
            for (var property:state.getProperties()) if (property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age && age.getName().equals("age"))
                state=state.setValue(age,Collections.max(age.getPossibleValues()));
            if ((BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft") && !generatedFlower(block,natural)) || denied.contains(id)
                    || block instanceof EntityBlock || block instanceof InfestedBlock || state.getDestroySpeed(server.overworld(),BlockPos.ZERO)<0 || (!stack.isEmpty() && !Economy.plain(stack))) continue;
            if (!seeds.containsKey(id)) {
                Source source=entry.getValue();
                seeds.putIfAbsent(id,new CostEngine.Value(source.cost(),.85,"Приблизительная добыча: "+source.reason()+". Редкость биома и доступ к измерению требуют калибровки сборки.",gates(policy,BuiltInRegistries.BLOCK.getKey(block).toString()))); sources++;
            }
        }
        int mobs=0;
        if (enabled(policy,"mob_drops")) for (var type:BuiltInRegistries.ENTITY_TYPE) {
            var key=BuiltInRegistries.ENTITY_TYPE.getKey(type); if (key.getNamespace().equals("minecraft")) continue;
            try {
                if (!(type.create(server.overworld()) instanceof Mob mob)) continue;
                double hp=mob.getMaxHealth(),armor=mob.getArmorValue();
                double attack=mob.getAttribute(Attributes.ATTACK_DAMAGE)==null?0:mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                double effort=encounterEffort(hp,armor,attack,policy.get("mob_search_effort").getAsDouble());
                var drops=EncounterProfiles.drops(server,Platform.mobLootId(mob));
                for (var drop:drops.entrySet()) if (!known.containsKey(drop.getKey()) && !denied.contains(drop.getKey()) && drop.getValue()>0) {
                    var value=new CostEngine.Value(effort/drop.getValue(),.85,String.format(Locale.ROOT,
                            "Приблизительная добыча %s: HP %.0f, броня %.0f, атака %.1f, средний дроп %.3f. Поиск и механики оценочные; точный профиль: upgrade_encounters.",key,hp,armor,attack,drop.getValue()),gates(policy,key.toString()));
                    seeds.merge(drop.getKey(),value,(a,b)->a.cost()<=b.cost()?a:b); mobs++;
                }
            } catch (Exception ex) { Upgrade.LOGGER.debug("Cannot inspect mob loot {}: {}",key,ex.toString()); }
        }
        int chests=0;
        if (enabled(policy,"chest_loot")) for (var table:Platform.lootKeys(server)) {
            if (!table.getPath().startsWith("chests/") || table.getNamespace().equals("minecraft")) continue;
            try {
                for (var drop:EncounterProfiles.drops(server,table).entrySet()) if (!known.containsKey(drop.getKey()) && !denied.contains(drop.getKey()) && drop.getValue()>0) {
                    var value=new CostEngine.Value(policy.get("chest_effort").getAsDouble()/drop.getValue(),.85,
                            "Приблизительная добыча из сундука "+table+", средний дроп "+drop.getValue()+". Бюджет поиска задаётся в upgrade_inheritance/policy.json.",gates(policy,table.toString()));
                    seeds.merge(drop.getKey(),value,(a,b)->a.cost()<=b.cost()?a:b); chests++;
                }
            } catch (Exception ex) { Upgrade.LOGGER.debug("Cannot inspect chest {}: {}",table,ex.toString()); }
        }
        // Import edges even before their blocks are priced. The solver can then follow arbitrary craft/drop chains.
        if (enabled(policy,"block_drops")) for (Block block:BuiltInRegistries.BLOCK) {
            var key=BuiltInRegistries.BLOCK.getKey(block); if (key.getNamespace().equals("minecraft")) continue;
            ItemStack stack=block.asItem().getDefaultInstance();
            String id=stack.isEmpty()?"upgrade:harvest/"+key.toString().replace(':','/'):Economy.id(stack);
            if (denied.contains(id) || block instanceof EntityBlock || block instanceof InfestedBlock) continue;
            if (!stack.isEmpty() && !Economy.plain(stack)) continue;
            dropRoutes+=blockDrops(server,block,id,known,denied,routes);
        }
        Upgrade.LOGGER.info("Automatic chest loot: {} sources",chests);
        Upgrade.LOGGER.info("Automatic inheritance: {} natural sources, {} block drop routes, {} mob drops",sources,dropRoutes,mobs);
    }
    private static int blockDrops(MinecraftServer server,Block block,String id,Map<String,CostEngine.Value> known,Set<String> denied,List<CostEngine.Route> routes) {
        int dropRoutes=0;
        var state=block.defaultBlockState();
        for (var property:state.getProperties()) if (property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age && age.getName().equals("age"))
            state=state.setValue(age,Collections.max(age.getPossibleValues()));
            // Real loaded loot tables include mod callbacks. Fixed seeds make reloads reproducible.
            // Only unknown products are imported, so sampling cannot undercut an existing calibrated price.
            try {
                var builder=new LootParams.Builder(server.overworld()).withParameter(LootContextParams.ORIGIN,Vec3.ZERO)
                        .withParameter(LootContextParams.BLOCK_STATE,state).withParameter(LootContextParams.TOOL,new ItemStack(Items.NETHERITE_PICKAXE));
                var params=builder.create(LootContextParamSets.BLOCK);
                Map<String,Double> drops=new TreeMap<>();
                var table=Platform.blockLoot(server,block);
                // BCLib and other mods override Block#getDrops and never register a loot table.
                // Use their runtime contract for that case; those samples can vary slightly between reloads.
                boolean custom=table==net.minecraft.world.level.storage.loot.LootTable.EMPTY;
                var exactDrops=NaturalFamilies.oreDrops(block);
                if (exactDrops!=null) drops.putAll(exactDrops);
                else for (int i=1;i<=128;i++) for (ItemStack drop:custom?state.getDrops(builder):table.getRandomItems(params,0x52FA1234L+i*104729L)) {
                    if (Economy.plain(drop)) drops.merge(Economy.id(drop),drop.getCount()/128.0,Double::sum);
                }
                for (var drop:drops.entrySet()) if (!drop.getKey().equals(id) && (!known.containsKey(drop.getKey()) || BalancePolicy.ordinaryPlant(block)) && !denied.contains(drop.getKey())) {
                    if (BalancePolicy.ordinaryPlant(block) && drop.getValue()>=.5) BalancePolicy.gatheredPlantDrop(drop.getKey());
                    routes.add(new CostEngine.Route((exactDrops==null?"Оценка дропа (128 проб): ":"Средний дроп руды: ")+id+" -> "+drop.getKey(),drop.getKey(),drop.getValue(),
                            List.of(new CostEngine.Input(List.of(id),1)),.1,.85,Set.of())); dropRoutes++;
                }
            } catch (Exception ex) { Upgrade.LOGGER.debug("Cannot inspect block loot {}: {}",id,ex.toString()); }
        return dropRoutes;
    }
    public static double encounterEffort(double hp,double armor,double attack,double search) {
        for (double n:new double[]{hp,armor,attack,search}) if (!Double.isFinite(n)||n<0) throw new IllegalArgumentException("Invalid mob attribute");
        return search+hp/80*(1+armor/20)*(1+attack/10)+Math.max(0,hp-20)/4+attack;
    }
    private static boolean enabled(JsonObject policy,String key) { return !policy.has(key)||policy.get(key).getAsBoolean(); }
    private static Set<String> gates(JsonObject policy,String id) {
        var map=policy.getAsJsonObject("namespace_gates"); String namespace=id.split(":",2)[0];
        Set<String> result=new TreeSet<>();
        if (map!=null && map.has(namespace)) for (JsonElement e:map.getAsJsonArray(namespace)) result.add(e.getAsString());
        return result;
    }
    // Only genuinely generated overworld flowers, not every BushBlock (boss drops and Nether plants differ).
    private static boolean generatedFlower(Block block,Map<Block,Source> natural) {
        return natural.containsKey(block) && !(block instanceof WitherRoseBlock)
                && (block instanceof FlowerBlock || block instanceof PinkPetalsBlock || block instanceof DoublePlantBlock);
    }
    private static boolean oreTag(String p) { return p.equals("ores")||p.startsWith("ores/")||p.endsWith("_ores"); }
    private static void put(Map<Block,Source> values,Block block,double cost,String reason) {
        values.merge(block,new Source(cost,reason),(a,b)->a.cost()<=b.cost()?a:b);
    }
    private static void states(JsonElement e,Map<Block,Source> natural,String feature) {
        if (e.isJsonArray()) { for (JsonElement child:e.getAsJsonArray()) states(child,natural,feature); }
        else if (e.isJsonObject()) {
            JsonObject o=e.getAsJsonObject();
            if (o.has("Name") && o.get("Name").isJsonPrimitive()) {
                var key=net.minecraft.resources.ResourceLocation.tryParse(o.get("Name").getAsString());
                if (key!=null && BuiltInRegistries.BLOCK.containsKey(key)) put(natural,BuiltInRegistries.BLOCK.get(key),4,"Природный блок из генерации "+feature);
            }
            for (var child:o.entrySet()) states(child.getValue(),natural,feature);
        }
    }
}
