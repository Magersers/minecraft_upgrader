package dev.upgrade;

import com.google.gson.*;
import dev.upgrade.core.CostEngine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
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
        Map<String, CostEngine.Value> seeds = new TreeMap<>();
        List<CostEngine.Route> routes = new ArrayList<>();
        Map<String, String> unsupported = new HashMap<>();
        Set<String> denied = new HashSet<>(Set.of("minecraft:air", "minecraft:barrier", "minecraft:command_block",
                "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:structure_block",
                "minecraft:structure_void", "minecraft:jigsaw", "minecraft:debug_stick", "minecraft:light",
                "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:spawner", "minecraft:dragon_egg"));
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
                    if (s.has("item")) localSeeds.put(s.get("item").getAsString(), value);
                    else if (s.has("tag")) {
                        var tag = ForgeRegistries.ITEMS.tags().getTag(TagKey.create(Registries.ITEM, new ResourceLocation(s.get("tag").getAsString())));
                        for (Item item : tag) localSeeds.put(ForgeRegistries.ITEMS.getKey(item).toString(), value);
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
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            try {
                ItemStack output = recipe.getResultItem(server.registryAccess());
                if (output.isEmpty()) continue;
                // Read the contract (recipe type / ingredients), not an exact Java class.
                boolean supported = recipe instanceof CraftingRecipe || recipe instanceof AbstractCookingRecipe
                        || recipe instanceof StonecutterRecipe || recipe instanceof SmithingTransformRecipe;
                if (!supported || recipe.isSpecial() || !plain(output)) {
                    unsupported.putIfAbsent(id(output), "Особый рецепт: " + recipe.getId() + ". Нужен профиль routes.");
                    continue;
                }
                List<Ingredient> ingredients = new ArrayList<>(recipe.getIngredients());
                if (recipe instanceof SmithingTransformRecipe && ingredients.isEmpty()) {
                    // Vanilla smithing does not expose ingredients through Recipe#getIngredients.
                    ResourceLocation file = new ResourceLocation(recipe.getId().getNamespace(), "recipes/" + recipe.getId().getPath() + ".json");
                    var resource = server.getResourceManager().getResource(file);
                    if (resource.isEmpty()) continue;
                    try (Reader reader = resource.get().openAsReader()) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        for (String key : List.of("template", "base", "addition")) ingredients.add(Ingredient.fromJson(json.get(key)));
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
                    unsupported.putIfAbsent(id(output), "В рецепте " + recipe.getId() + " пустой тег или особый ингредиент.");
                    continue;
                }
                double overhead = recipe instanceof AbstractCookingRecipe cooking ? cooking.getCookingTime() / 200.0 : 0;
                routes.add(new CostEngine.Route(recipe.getId().toString(), id(output), output.getCount(), inputs, overhead, .9, Set.of()));
            } catch (Exception ex) { Upgrade.LOGGER.warn("Cannot import recipe {}", recipe.getId(), ex); }
        }
        denied.forEach(seeds::remove);
        List<CostEngine.Route> filtered = new ArrayList<>();
        for (CostEngine.Route route : routes) {
            if (denied.contains(route.output())) continue;
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
        var solved = CostEngine.solve(seeds, filtered, 128);
        Map<String, List<CostEngine.Route>> byOutput = new HashMap<>();
        filtered.forEach(r -> byOutput.computeIfAbsent(r.output(), k -> new ArrayList<>()).add(r));
        Map<String, String> explanations = new HashMap<>();
        for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
            String id = key.toString();
            String explanation;
            if (solved.quarantined().contains(id)) explanation = "Обнаружен цикл, бесконечно удешевляющий предмет.";
            else if (solved.values().containsKey(id)) {
                StringBuilder text = new StringBuilder();
                describe(id, solved, text, new HashSet<>(), 0);
                explanation = text.toString().strip();
            } else if (byOutput.containsKey(id)) {
                var missing = byOutput.get(id).stream().min(Comparator.comparingLong(r -> r.inputs().stream()
                        .filter(i -> CostEngine.cheapest(i, solved.values()) == null).count())).orElseThrow();
                List<String> names = missing.inputs().stream().filter(i -> CostEngine.cheapest(i, solved.values()) == null)
                        .map(i -> String.join(" / ", i.alternatives().stream().limit(3).toList())).distinct().limit(4).toList();
                explanation = "Рецепт: " + missing.id() + "\nНе оценены ингредиенты:\n" + String.join("\n", names);
            } else explanation = unsupported.getOrDefault(id, "Нет рецепта или базовой цены добычи. Добавьте источник в upgrade_values.");
            explanations.put(id, explanation.substring(0, Math.min(1800, explanation.length())));
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
        out.append("\n").append("  ".repeat(depth)).append("Крафт: ");
        var grouped = new LinkedHashMap<String, Double>();
        recipe.parts().forEach(p -> grouped.merge(p.item(), p.count(), Double::sum));
        grouped.forEach((item, count) -> out.append(String.format(Locale.ROOT,"%s × %.0f; ", item, count)));
        out.append(String.format(Locale.ROOT, "+ %.2f E / %.0f шт.\n", recipe.overhead(), recipe.outputCount()));
        grouped.keySet().forEach(item -> describe(item, solved, out, visited, depth + 1));
    }
    public static String reason(ServerPlayer player, ItemStack stack) {
        String id = id(stack);
        if (current.denied().contains(id)) return "Исключён профилем";
        if (!plain(stack)) return "Особые данные, износ или содержимое: такой предмет нельзя ставить.";
        var value = current.values().get(id);
        if (value != null && !unlocked(player, value)) return "Нужен этап: " + String.join(", ", value.gates());
        if (value != null && value.confidence() < MIN_CONFIDENCE) return "Недостаточное доверие к цене: " + Math.round(value.confidence() * 100) + "%";
        return current.explanations().getOrDefault(id, "Выберите предмет в основной руке");
    }
    public static String id(ItemStack stack) { return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(); }
    // Vanilla writes Damage:0 even on freshly crafted tools and armour. This is
    // normal item data, not a custom NBT payload, and must not exclude all equipment.
    private static boolean ordinaryData(ItemStack stack) {
        if (!stack.hasTag()) return true;
        var tag = stack.getTag();
        return tag.size() == 1 && tag.contains("Damage", net.minecraft.nbt.Tag.TAG_INT)
                && tag.getInt("Damage") == 0 && stack.getItem().isDamageable(stack);
    }
    public static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && ordinaryData(stack) && !stack.isDamaged() && !(stack.getItem() instanceof SpawnEggItem)
                && !stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                && !stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()
                && !stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
    }
    public static boolean unlocked(ServerPlayer player, CostEngine.Value value) {
        for (String gate : value.gates()) {
            ResourceLocation key = ResourceLocation.tryParse(gate);
            if (key == null) return false;
            var advancement = player.server.getAdvancements().getAdvancement(key);
            if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) return false;
        }
        return true;
    }
    public static CostEngine.Value usable(ServerPlayer player, ItemStack stack) {
        if (!plain(stack) || current.denied().contains(id(stack))) return null;
        var value = current.values().get(id(stack));
        return value != null && value.confidence() >= MIN_CONFIDENCE && unlocked(player, value) ? value : null;
    }
}
