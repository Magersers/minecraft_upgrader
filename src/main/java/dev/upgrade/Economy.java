package dev.upgrade;
import com.google.gson.*;
import dev.upgrade.core.CostEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.Reader;
import java.util.*;
public final class Economy {
    public static final double EFFICIENCY=CostEngine.DEFAULT_EFFICIENCY;
    public static final double MIN_CONFIDENCE=.85;
    public record Snapshot(UUID revision,Map<String,CostEngine.Value> values,Set<String> denied) {}
    public static Snapshot current=new Snapshot(UUID.randomUUID(),Map.of(),Set.of());
    private static Set<String> strings(JsonObject o,String key) {Set<String> out=new TreeSet<>();if(o.has(key))for(JsonElement e:o.getAsJsonArray(key))out.add(e.getAsString());return out;}
    private static double number(JsonObject o,String key,double fallback) {return o.has(key)?o.get(key).getAsDouble():fallback;}
    public static void rebuild(MinecraftServer server) {
        Map<String,CostEngine.Value> seeds=new TreeMap<>();List<CostEngine.Route> routes=new ArrayList<>();
        Set<String> denied=new HashSet<>(Set.of("minecraft:air","minecraft:barrier","minecraft:command_block","minecraft:chain_command_block","minecraft:repeating_command_block","minecraft:structure_block","minecraft:structure_void","minecraft:jigsaw","minecraft:debug_stick","minecraft:light","minecraft:bedrock","minecraft:end_portal_frame","minecraft:spawner","minecraft:dragon_egg"));
        var profiles=server.getResourceManager().listResources("upgrade_values",p->p.getPath().endsWith(".json"));
        try {
            for(var entry:new TreeMap<>(profiles).entrySet())try(Reader reader=entry.getValue().openAsReader()) {
                JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();denied.addAll(strings(root,"deny"));
                if(root.has("sources"))for(JsonElement element:root.getAsJsonArray("sources")) {
                    JsonObject s=element.getAsJsonObject();String id=s.get("item").getAsString();double cost;
                    if(s.has("encounter")) {JsonObject e=s.getAsJsonObject("encounter");cost=CostEngine.encounter(number(e,"search",0),number(e,"combat",0),number(e,"consumables",0),number(e,"death_chance",0),number(e,"death_loss",0),number(e,"setup",0),number(e,"horizon",1),number(e,"success",1),number(e,"drop_chance",1),number(e,"mean_count",1));}else cost=s.get("cost").getAsDouble();
                    var value=new CostEngine.Value(cost,number(s,"confidence",.5),s.get("reason").getAsString(),strings(s,"gates"));seeds.merge(id,value,(a,b)->a.cost()<=b.cost()?a:b);
                }
                if(root.has("routes"))for(JsonElement element:root.getAsJsonArray("routes")) {
                    JsonObject r=element.getAsJsonObject();List<CostEngine.Input> inputs=new ArrayList<>();
                    for(JsonElement el:r.getAsJsonArray("inputs")){JsonObject i=el.getAsJsonObject();inputs.add(new CostEngine.Input(new ArrayList<>(strings(i,"alternatives")),number(i,"count",1)));}
                    routes.add(new CostEngine.Route(r.get("id").getAsString(),r.get("output").getAsString(),number(r,"count",1),inputs,number(r,"overhead",0),number(r,"confidence",.5),strings(r,"gates")));
                }
            }
        }catch(Exception invalidProfile){current=new Snapshot(UUID.randomUUID(),Map.of(),Set.copyOf(denied));Upgrade.LOGGER.error("Economy disabled: invalid profile",invalidProfile);return;}
        for(Recipe<?> recipe:server.getRecipeManager().getRecipes()) {
            boolean simple=recipe.getClass()==ShapedRecipe.class||recipe.getClass()==ShapelessRecipe.class||recipe.getClass()==StonecutterRecipe.class||recipe.getClass()==SmeltingRecipe.class||recipe.getClass()==BlastingRecipe.class||recipe.getClass()==SmokingRecipe.class||recipe.getClass()==CampfireCookingRecipe.class;
            if(!simple||recipe.isSpecial())continue;ItemStack output=recipe.getResultItem(server.registryAccess());if(!plain(output)||recipe.getIngredients().isEmpty())continue;
            List<CostEngine.Input> inputs=new ArrayList<>();boolean safe=true;
            for(Ingredient ingredient:recipe.getIngredients()) {
                if(ingredient.isEmpty())continue;List<String> choices=new ArrayList<>();
                for(ItemStack stack:ingredient.getItems()){if(!plain(stack)||stack.hasCraftingRemainingItem()){safe=false;break;}choices.add(id(stack));}
                if(!safe||choices.isEmpty()){safe=false;break;}inputs.add(new CostEngine.Input(choices,1));
            }
            if(!safe||inputs.isEmpty())continue;double overhead=recipe instanceof AbstractCookingRecipe cooking?cooking.getCookingTime()/200.0:0;
            routes.add(new CostEngine.Route(recipe.getId().toString(),id(output),output.getCount(),inputs,overhead,.9,Set.of()));
        }
        denied.forEach(seeds::remove);routes.removeIf(r->denied.contains(r.output())||r.inputs().stream().anyMatch(i->i.alternatives().stream().anyMatch(denied::contains)));
        var solved=CostEngine.solve(seeds,routes,128);current=new Snapshot(UUID.randomUUID(),solved.values(),Set.copyOf(denied));
        Upgrade.LOGGER.info("Economy: {} valued, {} quarantined, {} passes",solved.values().size(),solved.quarantined().size(),solved.passes());
    }
    public static String id(ItemStack stack){return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();}
    public static boolean plain(ItemStack stack){return !stack.isEmpty()&&!stack.hasTag()&&!stack.isDamaged()&&!(stack.getItem() instanceof SpawnEggItem)&&!stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()&&!stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()&&!stack.getCapability(ForgeCapabilities.ENERGY).isPresent();}
    public static boolean unlocked(ServerPlayer player,CostEngine.Value value){for(String gate:value.gates()){ResourceLocation key=ResourceLocation.tryParse(gate);if(key==null)return false;var advancement=player.server.getAdvancements().getAdvancement(key);if(advancement==null||!player.getAdvancements().getOrStartProgress(advancement).isDone())return false;}return true;}
    public static CostEngine.Value usable(ServerPlayer player,ItemStack stack){if(!plain(stack)||current.denied.contains(id(stack)))return null;var value=current.values.get(id(stack));return value!=null&&value.confidence()>=MIN_CONFIDENCE&&unlocked(player,value)?value:null;}
}
