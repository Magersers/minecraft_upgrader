package dev.upgrade;

import dev.upgrade.core.CostEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
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
    public static net.minecraft.world.item.crafting.Ingredient ingredient(com.google.gson.JsonElement json) { return net.minecraft.world.item.crafting.Ingredient.fromJson(json); }
    public record RecipeRef(ResourceLocation id,Recipe<?> recipe) {}
    public static List<RecipeRef> recipes(MinecraftServer server) { return server.getRecipeManager().getRecipes().stream().map(r -> new RecipeRef(r.getId(),r)).toList(); }
    public static CompoundTag data(Player player) { return ((PersistentPlayer)player).upgrade$data(); }
    public static CompoundTag tag(ItemStack stack) { return stack.hasTag()?stack.getTag():new CompoundTag(); }
    public static boolean hasTag(ItemStack stack) { return stack.hasTag(); }
    public static boolean ordinaryData(ItemStack stack) {
        if (!stack.hasTag()) return true;
        var tag=stack.getTag();
        return tag.size()==1 && tag.contains("Damage",Tag.TAG_INT) && tag.getInt("Damage")==0 && stack.isDamageableItem();
    }
    public static boolean sameItemData(ItemStack a,ItemStack b) { return ItemStack.isSameItemSameTags(a,b); }
    public static boolean hasStorage(ItemStack stack) {
        var context=ContainerItemContext.withConstant(stack);
        return FluidStorage.ITEM.find(stack,context)!=null;
    }
    public static void writeItem(FriendlyByteBuf b,ItemStack stack) { b.writeItem(stack); }
    public static ItemStack readItem(FriendlyByteBuf b) { return b.readItem(); }
    public static CompoundTag saveItem(ServerPlayer player,ItemStack stack) { return stack.save(new CompoundTag()); }
    public static ItemStack loadItem(ServerPlayer player,CompoundTag tag) { return ItemStack.of(tag); }
    public static String recipeDirectory() { return "recipes/"; }
    public static String lootDirectory() { return "loot_tables/"; }
    public static boolean unlocked(ServerPlayer player,CostEngine.Value value) {
        for (String gate:value.gates()) {
            ResourceLocation key=ResourceLocation.tryParse(gate); if (key==null) return false;
            var advancement=player.server.getAdvancements().getAdvancement(key);
            if (advancement==null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) return false;
        }
        return true;
    }
}
