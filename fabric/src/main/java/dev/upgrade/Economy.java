package dev.upgrade;

import com.google.gson.*;
import dev.upgrade.core.CostEngine;
import dev.upgrade.compat.TinkersCompat;
import dev.upgrade.compat.EncounterProfiles;
import dev.upgrade.compat.RecipeInheritance;
import dev.upgrade.compat.NaturalInheritance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.upgrade.Platform;
import dev.upgrade.Ids;
import java.io.Reader;
import java.util.*;

public final class Economy {
    public static final double EFFICIENCY = CostEngine.DEFAULT_EFFICIENCY;
    public static final double MIN_CONFIDENCE = .85;
    public record Snapshot(UUID revision, Map<String, CostEngine.Value> values, Set<String> denied,
                           Map<String, String> explanations) {}
    public static Snapshot current = new Snapshot(UUID.randomUUID(), Map.of(), Set.of(), Map.of());

    private static Set<String> strings(JsonObject o, String key) {
        Set<String> out = new TreeSet<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }
    private static double number(JsonObject o, String key, double fallback) { return o.has(key) ? o.get(key).getAsDouble() : fallback; }

    public static void rebuild(MinecraftServer server) {
        PricingPolicy.load(server);
        BalancePolicy.load(server);
        Map<String, CostEngine.Value> seeds = new TreeMap<>();
        List<CostEngine.Route> routes = new ArrayList<>();
        Map<String, String> unsupported = new HashMap<>();
        Set<String> denied = new HashSet<>(Set.of("minecraft:air", "minecraft:barrier", "minecraft:command_block",
                "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:structure_block",
                "minecraft:structure_void", "minecraft:jigsaw", "minecraft:debug_stick", "minecraft:light",
                "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:spawner", "minecraft:dragon_egg"));
        BuiltInRegistries.ITEM.keySet().stream().filter(k->k.getPath().startsWith("debug/")).forEach(k->denied.add(k.toString()));
        var profiles = server.getResourceManager().listResources("upgrade_values", p -> p.getPath().endsWith(".json"));
        // Validate each complete profile before applying it. One broken optional pack must
        // not disable every item in the economy or leave a half-applied profile behind.
        for (var entry : new TreeMap<>(profiles).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, CostEngine.Value> localSeeds = new TreeMap<>();
                List<CostEngine.Route> localRoutes = new ArrayList<>();
                if (root.has("sources")) for (JsonElement element : root.getAsJsonArray("sources")) {
                    JsonObject s = element.getAsJsonObject();
                    double cost;
                    if (s.has("encounter")) {
                        JsonObject e = s.getAsJsonObject("encounter");
                        cost = CostEngine.encounter(number(e,"search",0),number(e,"combat",0),number(e,"consumables",0),
                                number(e,"death_chance",0),number(e,"death_loss",0),number(e,"setup",0),number(e,"horizon",1),
                                number(e,"success",1),number(e,"drop_chance",1),number(e,"mean_count",1));
                    } else cost = s.get("cost").getAsDouble();
                    var value = new CostEngine.Value(cost, number(s,"confidence",.9),
                            s.has("reason") ? s.get("reason").getAsString() : "Базовый ресурс", strings(s,"gates"));
                    if (s.has("item")) {
                        String itemId=s.get("item").getAsString();
                        if (BuiltInRegistries.ITEM.containsKey(Ids.of(itemId))) localSeeds.put(itemId,value);
                    }
                    else if (s.has("tag")) {
                        var tag = BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Ids.of(s.get("tag").getAsString())));
                        for (var holder : tag) {
                            var itemKey=BuiltInRegistries.ITEM.getKey(holder.value());
                            // Crafting substitutability is not equal acquisition cost (e.g. cincinnasite in c:iron_ingots).
                            // Pack-authored profiles may deliberately set a shared price; the shipped baseline must not.
                            if (entry.getKey().equals(Ids.of("upgrade:upgrade_values/baseline.json")) && !itemKey.getNamespace().equals("minecraft")) continue;
                            localSeeds.put(itemKey.toString(),value);
                        }
                    } else throw new IllegalArgumentException("Source requires item or tag");
                }
                if (root.has("routes")) for (JsonElement element : root.getAsJsonArray("routes")) {
                    JsonObject r = element.getAsJsonObject();
                    List<CostEngine.Input> inputs = new ArrayList<>();
                    for (JsonElement el : r.getAsJsonArray("inputs")) {
                        JsonObject i = el.getAsJsonObject();
                        inputs.add(new CostEngine.Input(new ArrayList<>(strings(i,"alternatives")), number(i,"count",1)));
                    }
                    localRoutes.add(new CostEngine.Route(r.get("id").getAsString(), r.get("output").getAsString(),
                            number(r,"count",1), inputs, number(r,"overhead",0), number(r,"confidence",.9), strings(r,"gates")));
                }
                Set<String> localDenied = strings(root,"deny");
                localSeeds.forEach((id, value) -> seeds.merge(id, value, (a,b) -> a.cost() <= b.cost() ? a : b));
                routes.addAll(localRoutes); denied.addAll(localDenied);
            } catch (Exception ex) { Upgrade.LOGGER.error("Skipping invalid value profile {}", entry.getKey(), ex); }
        }
        EncounterProfiles.load(server,seeds);
        TinkersCompat.importRecipes(server,routes);
        for (var recipeRef : Platform.recipes(server)) {
            Recipe<?> recipe=recipeRef.recipe();
            try {
                ItemStack output = recipe.getResultItem(server.registryAccess());
                if (output.isEmpty()) continue;
                // Read the contract (recipe type / ingredients), not an exact Java class.
                boolean supported = recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe
                        || recipe instanceof StonecutterRecipe || recipe instanceof SmithingTransformRecipe;
                if (!supported && plain(output)) {
                    var imported=RecipeInheritance.read(server,recipeRef,output,denied);
                    if (imported!=null) { routes.add(imported); continue; }
                }
                if (!supported || recipe.isSpecial() || !plain(output)) {
                    unsupported.putIfAbsent(id(output), "Особый рецепт: " + recipeRef.id() + ". Нужен профиль routes.");
                    continue;
                }
                List<Ingredient> ingredients = new ArrayList<>(recipe.getIngredients());
                if (recipe instanceof SmithingTransformRecipe && ingredients.isEmpty()) {
                    // Vanilla smithing does not expose ingredients through Recipe#getIngredients.
                    ResourceLocation file = Ids.of(recipeRef.id().getNamespace(), Platform.recipeDirectory() + recipeRef.id().getPath() + ".json");
                    var resource = server.getResourceManager().getResource(file);
                    if (resource.isEmpty()) continue;
                    try (Reader reader = resource.get().openAsReader()) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        for (String key : List.of("template", "base", "addition")) ingredients.add(Platform.ingredient(json.get(key)));
                    }
                }
                List<CostEngine.Input> inputs = new ArrayList<>();
                boolean safe = true;
                for (Ingredient ingredient : ingredients) {
                    // Ingredient.EMPTY is an empty crafting-grid slot; an empty tag is a missing input.
                    if (ingredient == Ingredient.EMPTY) continue;
                    List<String> choices = new ArrayList<>();
                    for (ItemStack stack : ingredient.getItems()) {
                        if (!stack.isEmpty() && ordinaryData(stack) && !stack.isDamaged() && ingredient.test(stack)
                                && !denied.contains(id(stack))) choices.add(id(stack));
                    }
                    if (choices.isEmpty()) { safe = false; break; }
                    // Charge the complete input. Container returns are deliberately not subtracted:
                    // this supports bucket/bottle recipes without creating negative-cost loops.
                    inputs.add(new CostEngine.Input(choices, 1));
                }
                if (!safe || inputs.isEmpty()) {
                    unsupported.putIfAbsent(id(output), "В рецепте " + recipeRef.id() + " пустой тег или особый ингредиент.");
                    continue;
                }
                double overhead = recipe instanceof AbstractCookingRecipe cooking ? cooking.getCookingTime() / 200.0 : 0;
                routes.add(new CostEngine.Route(recipeRef.id().toString(), id(output), output.getCount(), inputs, overhead, .9, Set.of()));
            } catch (Exception ex) { Upgrade.LOGGER.warn("Cannot import recipe {}", recipeRef.id(), ex); }
        }
        denied.forEach(seeds::remove);
        // Resolve exact routes first. Automatic acquisition fills gaps, preserving calibrated prices.
        var exact=BalancePolicy.solve(seeds,routes,128);
        Upgrade.LOGGER.info("Economy before automatic sources: {} valued",exact.values().size());
        NaturalInheritance.load(server,seeds,routes,exact.values(),denied);
        List<CostEngine.Route> filtered = new ArrayList<>();
        for (CostEngine.Route route : routes) {
            if (denied.contains(route.output().split("#",2)[0])) continue;
            List<CostEngine.Input> inputs = new ArrayList<>();
            boolean safe = true;
            for (CostEngine.Input input : route.inputs()) {
                List<String> choices = input.alternatives().stream().filter(id -> !denied.contains(id)).toList();
                if (choices.isEmpty()) { safe = false; break; }
                inputs.add(new CostEngine.Input(choices, input.count()));
            }
            if (safe) filtered.add(new CostEngine.Route(route.id(), route.output(), route.count(), inputs,
                    route.overhead(), route.confidence(), route.gates()));
        }
        var acquisition = BalancePolicy.solve(seeds, filtered, 128);
        var solved = PerformancePricing.apply(server,acquisition,seeds,filtered,denied);
        Map<String, List<CostEngine.Route>> byOutput = new HashMap<>();
        filtered.forEach(r -> byOutput.computeIfAbsent(r.output(), k -> new ArrayList<>()).add(r));
        Map<String, String> explanations = new HashMap<>();
        Set<String> explanationKeys=new HashSet<>(byOutput.keySet());
        BuiltInRegistries.ITEM.keySet().forEach(k -> explanationKeys.add(k.toString()));
        for (String id : explanationKeys) {
            String explanation;
            if (solved.quarantined().contains(id)) explanation = "Обнаружен цикл, бесконечно удешевляющий предмет.";
            else if (solved.values().containsKey(id)) {
                StringBuilder text = new StringBuilder();
                describe(id, solved, text, new HashSet<>(), 0);
                explanation = (solved.values().get(id).confidence()<=.85?"Приблизительная цена: ":"")+text.toString().strip();
            } else if (byOutput.containsKey(id)) {
                var missing = byOutput.get(id).stream().min(Comparator.comparingLong(r -> r.inputs().stream()
                        .filter(i -> CostEngine.cheapest(i, solved.values()) == null).count())).orElseThrow();
                List<String> names = missing.inputs().stream().filter(i -> CostEngine.cheapest(i, solved.values()) == null)
                        .map(i -> String.join(" / ", i.alternatives().stream().limit(3).toList())).distinct().limit(4).toList();
                explanation = "Рецепт: " + missing.id() + "\nНе оценены ингредиенты:\n" + String.join("\n", names);
            } else explanation = unsupported.getOrDefault(id, "Нет рецепта или базовой цены добычи. Добавьте источник в upgrade_values.");
            explanations.put(id, explanation.substring(0, Math.min(1800, explanation.length())));
        }
        for (String id : solved.values().keySet()) if (!explanations.containsKey(id)) {
            StringBuilder text=new StringBuilder(); describe(id,solved,text,new HashSet<>(),0);
            explanations.put(id,text.substring(0,Math.min(1800,text.length())));
        }
        current = new Snapshot(UUID.randomUUID(), solved.values(), Set.copyOf(denied), Map.copyOf(explanations));
        Upgrade.LOGGER.info("Economy: {} valued, {} recipes, {} quarantined, {} passes", solved.values().size(),
                filtered.size(), solved.quarantined().size(), solved.passes());
    }
    private static void describe(String id, CostEngine.Result solved, StringBuilder out, Set<String> visited, int depth) {
        if (depth > 3 || out.length() > 1300 || !visited.add(id)) return;
        var value = solved.values().get(id);
        if (value == null) return;
        out.append("  ".repeat(depth)).append(id).append(String.format(Locale.ROOT, " = %.2f E", value.cost()));
        var recipe = solved.breakdowns().get(id);
        if (recipe == null) { out.append(" · ").append(value.source()).append('\n'); return; }
        out.append("\n").append("  ".repeat(depth)).append(recipe.recipe().contains("дроп")?"Добыча: ":"Крафт: ");
        var grouped = new LinkedHashMap<String, Double>();
        recipe.parts().forEach(p -> grouped.merge(p.item(), p.count(), Double::sum));
        grouped.forEach((item, count) -> out.append(String.format(Locale.ROOT,"%s × %.3f; ", item, count)));
        out.append(String.format(Locale.ROOT, "+ %.2f E / %.3f ед.\n", recipe.overhead(), recipe.outputCount()));
        grouped.keySet().forEach(item -> describe(item, solved, out, visited, depth + 1));
    }
    public static String reason(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return "Пустой слот. Выберите предмет в инвентаре.";
        String id = id(stack);
        if (current.denied().contains(id)) return "Исключён профилем";
        if (TinkersCompat.special(stack)) {
            var quote=TinkersCompat.quote(Platform.pricingCopy(stack,Set.of()),current.values());
            if (quote!=null&&!unlocked(player,quote)) return "Нужен этап: "+String.join(", ",quote.gates());
            return TinkersCompat.reason(Platform.pricingCopy(stack,Set.of()),current.values(),current.explanations())+(stack.isDamaged()?String.format(Locale.ROOT," · Износ: ставка × %.3f",PerformanceMath.wear(stack.getDamageValue(),stack.getMaxDamage())):"");
        }
        if (!stakeData(stack)) return "Особые данные или содержимое: нужен профиль этого предмета.";
        var value = current.values().get(id);
        if (value != null && !unlocked(player, value)) return "Нужен этап: " + String.join(", ", value.gates());
        if (value != null && value.confidence() < MIN_CONFIDENCE) return "Недостаточное доверие к цене: " + Math.round(value.confidence() * 100) + "%";
        String detail=current.explanations().getOrDefault(id, "Нет оценки предмета");
        if (value!=null && PerformancePricing.stakeLimits.getOrDefault(id,value.cost())<value.cost())
            detail=String.format(Locale.ROOT,"Ставка ограничена доступной цепочкой добычи/крафта: %.2f E; цена получения %.2f E.\n",PerformancePricing.stakeLimits.get(id),value.cost())+detail;
        if (stack.isDamaged()) detail+=String.format(Locale.ROOT,"\nОстаток прочности %.1f%%; стоимость ставки × %.3f",100*PerformanceMath.wear(stack.getDamageValue(),stack.getMaxDamage()),PerformanceMath.wear(stack.getDamageValue(),stack.getMaxDamage()));
        if (dev.upgrade.compat.EnergyCompat.read(stack)!=null) { var quote=PerformancePricing.utility(stack,value==null?Set.of():value.gates()); if (quote!=null) detail=quote.source()+"\n"+detail; }
        if (PricingPolicy.hard && PerformancePricing.simple.contains(id)) detail+=String.format(Locale.ROOT,"\nХард-режим: ставка не дороже %.2f E/шт.; цена получения не снижена",PricingPolicy.simpleCap);
        return detail.substring(0,Math.min(1800,detail.length()));
    }
    public static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    // Vanilla writes Damage:0 even on freshly crafted tools and armour. This is
    // normal item data, not a custom NBT payload, and must not exclude all equipment.
    private static boolean ordinaryData(ItemStack stack) { return Platform.ordinaryData(stack); }
    public static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && !TinkersCompat.special(stack) && ordinaryData(stack) && !stack.isDamaged() && !(stack.getItem() instanceof SpawnEggItem) && !(stack.getItem() instanceof GameMasterBlockItem)
                && !Platform.hasStorage(stack);
    }
    public static boolean unlocked(ServerPlayer player, CostEngine.Value value) {
        return Platform.unlocked(player,value);
    }

    private static boolean stakeData(ItemStack stack) {
        return plain(Platform.pricingCopy(stack,PerformancePricing.allowedKeys(stack)));
    }
    public static CostEngine.Value target(ServerPlayer player,ItemStack stack) {
        if (current.denied().contains(id(stack)) || !plain(stack)) return null;
        var value=current.values().get(id(stack));
        return value!=null && value.confidence()>=MIN_CONFIDENCE && unlocked(player,value)?value:null;
    }
    public static CostEngine.Value usable(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()||current.denied().contains(id(stack))) return null;
        var clean=Platform.pricingCopy(stack,PerformancePricing.allowedKeys(stack));
        var value=TinkersCompat.special(stack)?TinkersCompat.quote(clean,current.values()):stakeData(stack)?current.values().get(id(stack)):null;
        if (value==null || value.confidence()<MIN_CONFIDENCE || !unlocked(player,value)) return null;
        double cost=TinkersCompat.special(stack)?value.cost():Math.min(value.cost(),PerformancePricing.stakeLimits.getOrDefault(id(stack),value.cost()));
        if (!TinkersCompat.special(stack) && dev.upgrade.compat.EnergyCompat.read(stack)!=null) {
            var powered=PerformancePricing.utility(stack,value.gates());
            var empty=PerformancePricing.utility(stack.getItem().getDefaultInstance(),value.gates());
            if (powered!=null && empty!=null) cost+=Math.max(0,powered.cost()-empty.cost());
        }
        cost*=PerformanceMath.wear(stack.getDamageValue(),stack.getMaxDamage());
        if (PricingPolicy.hard && PerformancePricing.simple.contains(id(stack))) cost=Math.min(cost,PricingPolicy.simpleCap);
        return new CostEngine.Value(cost,value.confidence(),value.source(),value.gates());
    }
}
