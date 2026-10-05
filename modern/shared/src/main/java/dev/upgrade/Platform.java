package dev.upgrade;

import dev.upgrade.core.CostEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import java.util.List;

public final class Platform {
    public static double foodValue(ItemStack stack) {
        try {
        var food=stack.get(net.minecraft.core.component.DataComponents.FOOD);
        return food==null?0:Math.max(6,food.nutrition()*2+food.saturation()*2);
        } catch (RuntimeException ignored) { return 0; }
    }
    public static boolean glass(net.minecraft.world.level.block.Block block) { return block instanceof net.minecraft.world.level.block.TransparentBlock; }
    public static net.minecraft.world.item.crafting.Ingredient ingredient(com.google.gson.JsonElement json) { return net.minecraft.world.item.crafting.Ingredient.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow(); }
    public record RecipeRef(Identifier id,Recipe<?> recipe) {}
    public static List<RecipeRef> recipes(MinecraftServer server) { return server.reloadableRegistries().lookup().lookupOrThrow(net.minecraft.core.registries.Registries.RECIPE).listElements().map(r -> new RecipeRef(r.key().identifier(),r.value())).toList(); }
    public static CompoundTag data(Player player) { return ((PersistentPlayer)player).upgrade$data(); }
    public static CompoundTag tag(ItemStack stack) { var data=stack.get(DataComponents.CUSTOM_DATA); return data==null?new CompoundTag():data.copyTag(); }
    public static boolean hasTag(ItemStack stack) { return !stack.getComponentsPatch().isEmpty(); }
    public static boolean ordinaryData(ItemStack stack) {
        return stack.getComponentsPatch().forget(t -> t==DataComponents.DAMAGE && stack.isDamageableItem() && stack.has(DataComponents.DAMAGE) && stack.getDamageValue()==0).isEmpty();
    }
    public static java.util.List<net.minecraft.world.item.crafting.Ingredient> recipeIngredients(Recipe<?> recipe) {
        var placement=recipe.placementInfo();
        return placement.slotsToIngredientIndex().intStream().filter(i->i>=0).mapToObj(i->placement.ingredients().get(i)).toList();
    }
    public static ItemStack recipeOutput(MinecraftServer server,Recipe<?> recipe) {
        var context=net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(server.overworld());
        var outputs=recipe.display().stream().flatMap(d->d.result().resolveForStacks(context).stream()).toList();
        if(outputs.isEmpty()) return ItemStack.EMPTY;
        var first=outputs.getFirst();
        return outputs.stream().allMatch(v->ItemStack.isSameItemSameComponents(v,first)&&v.getCount()==first.getCount())?first:ItemStack.EMPTY;
    }
    public static ItemStack pricingCopy(ItemStack stack,java.util.Set<String> allowed) {
        var copy=stack.copy();
        if (copy.isDamageableItem()) copy.setDamageValue(0);
        var tag=tag(copy); allowed.forEach(tag::remove);
        if (tag.isEmpty()) copy.remove(DataComponents.CUSTOM_DATA);
        else copy.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        return copy;
    }
    public static java.util.Map<String,Double> attributes(ItemStack stack,net.minecraft.world.entity.EquipmentSlot slot) {
        var accum=new java.util.HashMap<String,double[]>();
        stack.forEachModifier(slot,(holder,modifier)->PerformancePricing.attribute(accum,
                net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(holder.value()).getPath(),holder.value().getDefaultValue(),
                modifier.amount(),modifier.operation().ordinal()));
        return PerformancePricing.attributes(accum);
    }
    public static boolean sameItemData(ItemStack a,ItemStack b) { return ItemStack.isSameItemSameComponents(a,b); }
    public static boolean hasStorage(ItemStack stack) {
        var context=ContainerItemContext.withConstant(stack);
        return FluidStorage.ITEM.find(stack,context)!=null;
    }
    public static void writeItem(FriendlyByteBuf b,ItemStack stack) { ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf)b,stack); }
    public static ItemStack readItem(FriendlyByteBuf b) { return ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf)b); }
    public static CompoundTag saveItem(ServerPlayer player,ItemStack stack) { return (CompoundTag)ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),stack).getOrThrow(); }
    public static ItemStack loadItem(ServerPlayer player,CompoundTag tag) { return ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),tag).result().orElse(ItemStack.EMPTY); }
    public static com.google.gson.JsonObject lootJson(MinecraftServer server,Identifier id) {
        var table=server.reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,id));
        return net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.encodeStart(
                server.reloadableRegistries().lookup().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),table).getOrThrow().getAsJsonObject();
    }
    public static java.util.Collection<Identifier> lootKeys(MinecraftServer server) {
        return server.reloadableRegistries().lookup().lookupOrThrow(net.minecraft.core.registries.Registries.LOOT_TABLE).listElementIds().map(k->k.identifier()).toList();
    }
    public static net.minecraft.world.level.storage.loot.LootTable blockLoot(MinecraftServer server,net.minecraft.world.level.block.Block block) {
        return block.getLootTable().map(server.reloadableRegistries()::getLootTable).orElse(net.minecraft.world.level.storage.loot.LootTable.EMPTY);
    }
    public static Identifier mobLootId(net.minecraft.world.entity.Mob mob) { return mob.getLootTable().map(k->k.identifier()).orElse(Identifier.withDefaultNamespace("empty")); }
    public static String recipeDirectory() { return "recipe/"; }
    public static String lootDirectory() { return "loot_table/"; }
    public static boolean unlocked(ServerPlayer player,CostEngine.Value value) {
        for (String gate:value.gates()) {
            Identifier key=Identifier.tryParse(gate); if (key==null) return false;
            var advancement=player.level().getServer().getAdvancements().get(key);
            if (advancement==null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) return false;
        }
        return true;
    }
}
