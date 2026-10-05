package dev.upgrade.compat;

import com.google.gson.*;
import dev.upgrade.*;
import dev.upgrade.core.CostEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.io.Reader;
import java.util.*;

/** Import item-only recipe contracts. Unknown fields fail closed: fluids, tools and secondary outputs need adapters. */
public final class RecipeInheritance {
    private static final Set<String> SIMPLE_FIELDS = Set.of("type", "group", "category", "ingredient", "ingredients",
            "input", "result", "output", "time", "cookingtime", "experience", "show_notification", "fabric:load_conditions", "conditions");
    private RecipeInheritance() {}
    public static CostEngine.Route read(MinecraftServer server, Platform.RecipeRef ref, ItemStack output, Set<String> denied) throws Exception {
        Object recipe=ref.recipe(); String cls=recipe.getClass().getName();
        List<CostEngine.Input> inputs=new ArrayList<>(); double overhead=0;
        // These contracts expose consumed inputs; getIngredients alone also includes the reusable hammer on anvils.
        if (cls.equals("org.betterx.bclib.recipes.AnvilRecipe")) {
            Ingredient ingredient=(Ingredient)recipe.getClass().getMethod("getMainIngredient").invoke(recipe);
            double count=((Number)recipe.getClass().getMethod("getInputCount").invoke(recipe)).doubleValue();
            inputs.add(input(ingredient,count,denied));
            overhead=((Number)recipe.getClass().getMethod("getDamage").invoke(recipe)).doubleValue();
        } else if (cls.equals("org.betterx.bclib.recipes.AlloyingRecipe") || cls.equals("org.betterx.betterend.recipe.builders.InfusionRecipe")) {
            for (Ingredient ingredient:Platform.recipeIngredients(ref.recipe())) if (ingredient!=null) inputs.add(input(ingredient,1,denied));
            String time=cls.endsWith("AlloyingRecipe")?"getSmeltTime":"getInfusionTime";
            overhead=((Number)recipe.getClass().getMethod(time).invoke(recipe)).doubleValue()/200;
        } else {
            var file=Ids.of(ref.id().getNamespace(),Platform.recipeDirectory()+ref.id().getPath()+".json");
            var resource=server.getResourceManager().getResource(file); if (resource.isEmpty()) return null;
            try (Reader reader=resource.get().openAsReader()) {
                JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
                if (!SIMPLE_FIELDS.containsAll(json.keySet())) return null;
                int fields=0;
                for (String key:List.of("ingredient","ingredients","input")) if (json.has(key)) {
                    fields++;
                    if (key.equals("ingredients")) for (JsonElement e:json.getAsJsonArray(key)) inputs.add(input(simpleIngredient(e),1,denied));
                    else inputs.add(input(simpleIngredient(json.get(key)),1,denied));
                }
                if (fields!=1 || json.has("result")==json.has("output")) return null;
                JsonElement result=json.get(json.has("result")?"result":"output");
                String item; int count=1;
                if (result.isJsonPrimitive()) item=result.getAsString();
                else {
                    JsonObject r=result.getAsJsonObject();
                    if (!Set.of("item","id","count").containsAll(r.keySet())) return null;
                    item=r.get(r.has("item")?"item":"id").getAsString();
                    if (r.has("count")) count=r.get("count").getAsInt();
                }
                if (!item.equals(Economy.id(output)) || count!=output.getCount()) return null;
                if (json.has("time") && json.has("cookingtime")) return null;
                for (String key:List.of("time","cookingtime")) if (json.has(key)) overhead=json.get(key).getAsDouble()/200;
            }
        }
        if (inputs.isEmpty()) return null;
        return new CostEngine.Route("Оценка обработки: "+ref.id(),Economy.id(output),output.getCount(),inputs,overhead,.85,Set.of());
    }
    private static Ingredient simpleIngredient(JsonElement json) {
        if (json.isJsonArray()) { for (JsonElement e:json.getAsJsonArray()) simpleIngredient(e); }
        else {
            var object=json.getAsJsonObject();
            if (object.size()!=1 || !(object.has("item")||object.has("tag"))) throw new IllegalArgumentException("Ingredient needs a counted/custom adapter");
        }
        return Platform.ingredient(json);
    }
    private static CostEngine.Input input(Ingredient ingredient,double count,Set<String> denied) {
        List<String> ids=new ArrayList<>();
        for (ItemStack stack:ingredient.items().map(h->h.value().getDefaultInstance()).toList()) if (!stack.isEmpty() && Platform.ordinaryData(stack) && !stack.isDamaged()
                && ingredient.test(stack) && !denied.contains(Economy.id(stack))) ids.add(Economy.id(stack));
        return new CostEngine.Input(ids,count);
    }
}
