package dev.upgrade.compat;

import dev.upgrade.Economy;
import dev.upgrade.Upgrade;
import dev.upgrade.core.CostEngine;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.upgrade.Platform;
import java.lang.reflect.Method;
import java.util.*;

/** Optional adapter. Only invokes Tinkers' own public, non-Minecraft method names (safe after reobfuscation). */
public final class TinkersCompat {
    private static final String LIB="slimeknights.tconstruct.library.";
    private static final Map<String,Optional<Class<?>>> TYPES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final String MATERIAL="@material/", FLUID="@fluid/";
    private TinkersCompat() {}
    private record FluidValue(net.minecraft.world.level.material.Fluid getFluid,double getAmount,boolean hasTag) {
        static FluidValue of(Object value) throws ReflectiveOperationException {
            return new FluidValue((net.minecraft.world.level.material.Fluid)call(value,"getFluid"),number(value,"getAmount"),(boolean)call(value,"hasTag"));
        }
        boolean isEmpty() { return getFluid==net.minecraft.world.level.material.Fluids.EMPTY||getAmount<=0; }
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(LIB+name); }
    private static boolean instance(Object object,String name) {
        return TYPES.computeIfAbsent(name,key -> {
            try { return Optional.of(type(key)); } catch (ClassNotFoundException|LinkageError e) { return Optional.empty(); }
        }).map(c -> c.isInstance(object)).orElse(false);
    }
    private static Object call(Object object,String name,Object... args) throws ReflectiveOperationException {
        Class<?> cls=object instanceof Class<?> c?c:object.getClass();
        for (Method m:cls.getMethods()) {
            if (!m.getName().equals(name)||m.getParameterCount()!=args.length) continue;
            boolean matches=true;
            for (int i=0;i<args.length;i++) if (args[i]!=null&&!boxed(m.getParameterTypes()[i]).isInstance(args[i])) { matches=false; break; }
            if (matches) { m.setAccessible(true); return m.invoke(object instanceof Class<?>?null:object,args); }
        }
        throw new NoSuchMethodException(cls.getName()+"."+name);
    }
    private static Object field(Object object,String name) throws ReflectiveOperationException {
        for (Class<?> c=object.getClass();c!=null;c=c.getSuperclass()) {
            try { var f=c.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Class<?> boxed(Class<?> c) { return c==boolean.class?Boolean.class:c==int.class?Integer.class:c; }
    private static double number(Object object,String method) throws ReflectiveOperationException { return ((Number)call(object,method)).doubleValue(); }
    private static String fluid(FluidValue stack) { return FLUID+BuiltInRegistries.FLUID.getKey(stack.getFluid()); }
    private static String material(Object variant) throws ReflectiveOperationException { return call(variant,"getVariant").toString(); }
    public static boolean special(ItemStack stack) {
        return instance(stack.getItem(),"tools.part.IMaterialItem") || instance(stack.getItem(),"tools.item.IModifiable");
    }
    private static String partKey(ItemStack stack) {
        return Economy.id(stack)+"#"+Platform.tag(stack).getString("Material").orElse("");
    }
    public static String key(ItemStack stack) { return instance(stack.getItem(),"tools.part.IMaterialItem")?partKey(stack):Economy.id(stack); }
    private static CostEngine.Input ingredient(Ingredient ingredient,double count) {
        List<String> choices=new ArrayList<>();
        for (ItemStack stack:ingredient.items().map(h->h.value().getDefaultInstance()).toList()) {
            if (!ingredient.test(stack)) continue;
            if (Economy.plain(stack)) choices.add(Economy.id(stack));
            else if (instance(stack.getItem(),"tools.part.IMaterialItem")) {
                if (!Platform.hasTag(stack)) choices.add("@anypart/"+Economy.id(stack));
                else if (Platform.tag(stack).size()==1&&Platform.tag(stack).getString("Material").isPresent()) choices.add(partKey(stack));
            }
        }
        return new CostEngine.Input(choices,count);
    }
    private static void route(List<CostEngine.Route> routes,String id,String output,double count,List<CostEngine.Input> inputs,double overhead) {
        routes.add(new CostEngine.Route(id,output,count,inputs,overhead,.9,Set.of()));
    }
    /** Normalize each fluid alternative separately: tags may contain fluids requiring different mB amounts. */
    private static CostEngine.Input fluidInput(List<CostEngine.Route> routes,String recipe,Object fluidIngredient) throws ReflectiveOperationException {
        return fluidInput(routes,recipe,(List<?>)call(fluidIngredient,"getFluids"));
    }
    private static CostEngine.Input fluidInput(List<CostEngine.Route> routes,String recipe,List<?> fluids) throws ReflectiveOperationException {
        String unit="@input/"+recipe; int index=0;
        for (Object value:fluids) {
            FluidValue stack=FluidValue.of(value);
            if (stack.isEmpty()||stack.hasTag()) continue;
            route(routes,recipe+"/fluid/"+index++,unit,1,List.of(new CostEngine.Input(List.of(fluid(stack)),stack.getAmount())),0);
        }
        if (index==0) throw new IllegalArgumentException("No plain fluid alternatives");
        return new CostEngine.Input(List.of(unit),1);
    }
    public static void importRecipes(MinecraftServer server,List<CostEngine.Route> routes) {
        var materials=new HashMap<String,Object>();
        int before=routes.size();
        var recipes=Platform.recipes(server).stream().filter(ref -> {
            Recipe<?> r=ref.recipe();
            var id=BuiltInRegistries.RECIPE_SERIALIZER.getKey(r.getSerializer());
            return id!=null&&id.getNamespace().equals("tconstruct");
        }).toList();
        for (var ref:recipes) {
            Recipe<?> recipe=ref.recipe();
            String id=ref.id().toString(),kind=BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).getPath();
            try {
                switch (kind) {
                    case "melting", "damagable_melting" -> {
                        FluidValue output=FluidValue.of(call(recipe,"getOutput"));
                        if (!output.isEmpty()&&!output.hasTag()) route(routes,id,fluid(output),output.getAmount(),List.of(ingredient((Ingredient)call(recipe,"getInput"),1)),number(recipe,"getTime")/200);
                    }
                    case "ore_melting" -> {
                        // The registered base output is the melter yield. Smeltery/foundry boosts depend on config.
                        // Use the base route conservatively; do not invent a universal ore multiplier.
                        FluidValue output=FluidValue.of(call(recipe,"getOutput"));
                        if (!output.isEmpty()&&!output.hasTag()) route(routes,id+"/base_yield",fluid(output),output.getAmount(),List.of(ingredient((Ingredient)call(recipe,"getInput"),1)),number(recipe,"getTime")/200);
                    }
                    case "alloy" -> {
                        List<CostEngine.Input> inputs=new ArrayList<>(); int n=0;
                        for (Object input:(List<?>)call(recipe,"getDisplayInputs")) inputs.add(fluidInput(routes,id+"/"+n++,(List<?>)input));
                        FluidValue output=FluidValue.of(call(recipe,"getOutput"));
                        if (!output.isEmpty()&&!output.hasTag()) route(routes,id,fluid(output),output.getAmount(),inputs,1);
                    }
                    case "casting_table", "casting_basin" -> {
                        ItemStack output=Platform.recipeOutput(server,recipe);
                        if (!Economy.plain(output)) break;
                        List<CostEngine.Input> inputs=new ArrayList<>();
                        inputs.add(fluidInput(routes,id,(List<?>)call(recipe,"getFluids")));
                        Ingredient cast=(Ingredient)call(recipe,"getCast");
                        if (cast!=null) inputs.add(ingredient(cast,(boolean)call(recipe,"isConsumed")?1:1.0/64));
                        route(routes,id,Economy.id(output),output.getCount(),inputs,number(recipe,"getCoolingTime")/200);
                    }
                    case "molding_table", "molding_basin" -> {
                        ItemStack output=Platform.recipeOutput(server,recipe);
                        if (!Economy.plain(output)) break;
                        List<CostEngine.Input> inputs=new ArrayList<>(List.of(ingredient((Ingredient)call(recipe,"getMaterial"),1)));
                        Ingredient pattern=(Ingredient)call(recipe,"getPattern");
                        if (pattern!=null) inputs.add(ingredient(pattern,(boolean)call(recipe,"isPatternConsumed")?1:1.0/64));
                        route(routes,id,Economy.id(output),output.getCount(),inputs,0);
                    }
                    case "material" -> {
                        Object variant=call(recipe,"getMaterial"); String material=material(variant); materials.put(material,variant);
                        route(routes,id,MATERIAL+material,number(recipe,"getValue"),List.of(ingredient((Ingredient)call(recipe,"getIngredient"),number(recipe,"getNeeded"))),0);
                    }
                    default -> { /* Other serializers require their own contracts; never treat machine inputs as shapeless recipes. */ }
                }
            } catch (ReflectiveOperationException|RuntimeException ex) { Upgrade.LOGGER.debug("Tinkers recipe {} unavailable: {}",id,ex.toString()); }
        }
        for (var ref:recipes) {
            Recipe<?> recipe=ref.recipe();
            String id=ref.id().toString(),kind=BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).getPath();
            try {
                if (kind.equals("part_builder")) {
                    ItemStack output=Platform.recipeOutput(server,recipe);
                    double cost=number(recipe,"getCost");
                    // Consult the live material registry and the part's material predicate, including variants.
                    for (var entry:materials.entrySet()) {
                        Object variant=entry.getValue(),materialId=call(variant,"getVariant");
                        boolean craftable=(boolean)call(call(variant,"get"),"isCraftable");
                        if (!craftable || !(boolean)call(output.getItem(),"canUseMaterial",call(variant,"getId"))) continue;
                        ItemStack part=(ItemStack)call(recipe,"getRecipeOutput",materialId);
                        route(routes,id+"/"+entry.getKey(),partKey(part),part.getCount(),List.of(new CostEngine.Input(List.of(MATERIAL+entry.getKey()),cost),
                                new CostEngine.Input(List.of("tconstruct:pattern"),1.0/64)),0);
                    }
                } else if (kind.equals("table_casting_material")||kind.equals("basin_casting_material")) {
                    // Read live material-fluid recipes directly, avoiding JEI's client-oriented display caches.
                    ItemStack blank=Platform.recipeOutput(server,recipe);
                    int itemCost=(int)field(recipe,"itemCost");
                    Ingredient cast=(Ingredient)call(recipe,"getCast");
                    for (var fluidRef:recipes) {
                        Recipe<?> fluidRecipe=fluidRef.recipe();
                        if (!instance(fluidRecipe,"recipe.casting.material.MaterialFluidRecipe")||call(fluidRecipe,"getInput")!=null) continue;
                        Object variant=call(fluidRecipe,"getOutput"),variantId=call(variant,"getVariant");
                        if (!(boolean)call(blank.getItem(),"canUseMaterial",call(variant,"getId"))) continue;
                        ItemStack part=(ItemStack)call(blank.getItem(),"withMaterial",variantId);
                        int index=0;
                        for (Object value:(List<?>)call(fluidRecipe,"getFluids")) {
                            FluidValue f=FluidValue.of(value);
                            if (f.isEmpty()||f.hasTag()) continue;
                            List<CostEngine.Input> inputs=new ArrayList<>(List.of(new CostEngine.Input(List.of(fluid(f)),(double)f.getAmount()*itemCost)));
                            if (cast!=null) inputs.add(ingredient(cast,(boolean)call(recipe,"isConsumed")?1:1.0/64));
                            route(routes,id+"/"+material(variant)+"/"+index++,partKey(part),part.getCount(),inputs,1);
                        }
                    }
                }
            } catch (ReflectiveOperationException|RuntimeException ex) { Upgrade.LOGGER.debug("Tinkers part recipe {} unavailable: {}",id,ex.toString()); }
        }
        Upgrade.LOGGER.debug("Tinkers adapter: {} materials, {} routes",materials.size(),routes.size()-before);
        // Tag ingredients can accept any valid material part (e.g. the model used to make a gold cast).
        // Keep material-specific values; the alias is used only as an input alternative.
        List<String> parts=routes.stream().map(CostEngine.Route::output).filter(k -> k.contains("#")&&!k.startsWith("@")).distinct().toList();
        for (String part:parts) route(routes,"upgrade:anypart/"+part,"@anypart/"+part.substring(0,part.indexOf('#')),1,List.of(new CostEngine.Input(List.of(part),1)),0);
    }
    /** Quotes only valid, pristine material parts/tools. Modified tools require explicit modifier pricing. */
    public static CostEngine.Value quote(ItemStack stack,Map<String,CostEngine.Value> values) {
        if (stack.isEmpty()||stack.isDamaged()) return null;
        try {
            if (instance(stack.getItem(),"tools.part.IMaterialItem")) {
                if (!Platform.hasTag(stack)||Platform.tag(stack).size()!=1||!Platform.tag(stack).getString("Material").isPresent()) return null;
                Object material=call(stack.getItem(),"getMaterial",stack);
                ItemStack canonical=(ItemStack)call(stack.getItem(),"withMaterial",material);
                if (!Platform.sameItemData(stack,canonical)) return null;
                return values.get(partKey(stack));
            }
            if (!instance(stack.getItem(),"tools.item.IModifiable")||!Platform.hasTag(stack)) return null;
            Object nbt=call(type("tools.nbt.MaterialNBT"),"readFromNBT",Platform.tag(stack).get("tic_materials"));
            ItemStack canonical=(ItemStack)call(type("tools.helper.ToolBuildHandler"),"buildItemFromMaterials",stack.getItem(),nbt);
            if (!Platform.sameItemData(stack,canonical)) return null;
            Object definition=call(stack.getItem(),"getToolDefinition");
            List<?> parts=(List<?>)call(call(definition,"getData"),"getParts");
            List<?> materials=(List<?>)call(nbt,"getList");
            if (parts.isEmpty()||parts.size()!=materials.size()) return null;
            double total=0,confidence=.9; Set<String> gates=new HashSet<>(); StringBuilder detail=new StringBuilder("Состав инструмента: ");
            for (int i=0;i<parts.size();i++) {
                ItemStack part=(ItemStack)call(call(parts.get(i),"getPart"),"withMaterial",call(materials.get(i),"getVariant"));
                String key=partKey(part); CostEngine.Value value=values.get(key); if (value==null) return null;
                total+=value.cost(); confidence=Math.min(confidence,value.confidence()); gates.addAll(value.gates());
                detail.append(key).append(String.format(Locale.ROOT," = %.2f E; ",value.cost()));
            }
            return new CostEngine.Value(total,confidence,detail.toString(),gates);
        } catch (ReflectiveOperationException|RuntimeException ex) { return null; }
    }
    public static String reason(ItemStack stack,Map<String,CostEngine.Value> values,Map<String,String> explanations) {
        CostEngine.Value quote=quote(stack,values);
        if (quote!=null) return explanations.getOrDefault(key(stack),quote.source());
        if (instance(stack.getItem(),"tools.part.IMaterialItem")&&Platform.hasTag(stack)&&Platform.tag(stack).size()==1&&Platform.tag(stack).getString("Material").isPresent())
            return explanations.getOrDefault(key(stack),"Нет цепочки получения детали из материала "+Platform.tag(stack).getString("Material").orElse(""));
        return "Tinkers: нужен новый предмет с оценённым материалом. Износ, дополнительные модификаторы, содержимое и предмет без материала не поддерживаются.";
    }
}
