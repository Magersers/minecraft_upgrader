package dev.upgrade;

import dev.upgrade.core.CostEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import java.util.List;

public final class Platform {
    public static net.minecraft.world.item.crafting.Ingredient ingredient(com.google.gson.JsonElement json) { return net.minecraft.world.item.crafting.Ingredient.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow(); }
    public record RecipeRef(ResourceLocation id,Recipe<?> recipe) {}
    public static List<RecipeRef> recipes(MinecraftServer server) { return server.getRecipeManager().getRecipes().stream().map(r -> new RecipeRef(r.id(),r.value())).toList(); }
    public static CompoundTag data(Player player) { return ((PersistentPlayer)player).upgrade$data(); }
    public static CompoundTag tag(ItemStack stack) { var data=stack.get(DataComponents.CUSTOM_DATA); return data==null?new CompoundTag():data.copyTag(); }
    public static boolean hasTag(ItemStack stack) { return !stack.getComponentsPatch().isEmpty(); }
    public static boolean ordinaryData(ItemStack stack) {
        for (var entry:stack.getComponentsPatch().entrySet()) {
            if (entry.getKey()!=DataComponents.DAMAGE || entry.getValue().isEmpty() || !entry.getValue().get().equals(0) || !stack.isDamageableItem()) return false;
        }
        return true;
    }
    public static boolean sameItemData(ItemStack a,ItemStack b) { return ItemStack.isSameItemSameComponents(a,b); }
    public static boolean hasStorage(ItemStack stack) {
        var context=ContainerItemContext.withConstant(stack);
        return FluidStorage.ITEM.find(stack,context)!=null;
    }
    public static void writeItem(FriendlyByteBuf b,ItemStack stack) { ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf)b,stack); }
    public static ItemStack readItem(FriendlyByteBuf b) { return ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf)b); }
    public static CompoundTag saveItem(ServerPlayer player,ItemStack stack) { return (CompoundTag)stack.save(player.registryAccess()); }
    public static ItemStack loadItem(ServerPlayer player,CompoundTag tag) { return ItemStack.parseOptional(player.registryAccess(),tag); }
    public static com.google.gson.JsonObject lootJson(MinecraftServer server,ResourceLocation id) {
        var table=server.reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,id));
        return net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.encodeStart(
                server.reloadableRegistries().get().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),table).getOrThrow().getAsJsonObject();
    }
    public static java.util.Collection<ResourceLocation> lootKeys(MinecraftServer server) {
        return server.reloadableRegistries().getKeys(net.minecraft.core.registries.Registries.LOOT_TABLE);
    }
    public static net.minecraft.world.level.storage.loot.LootTable blockLoot(MinecraftServer server,net.minecraft.world.level.block.Block block) {
        return server.reloadableRegistries().getLootTable(block.getLootTable());
    }
    public static ResourceLocation mobLootId(net.minecraft.world.entity.Mob mob) { return mob.getLootTable().location(); }
    public static String recipeDirectory() { return "recipe/"; }
    public static String lootDirectory() { return "loot_table/"; }
    public static boolean unlocked(ServerPlayer player,CostEngine.Value value) {
        for (String gate:value.gates()) {
            ResourceLocation key=ResourceLocation.tryParse(gate); if (key==null) return false;
            var advancement=player.server.getAdvancements().get(key);
            if (advancement==null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) return false;
        }
        return true;
    }
}
