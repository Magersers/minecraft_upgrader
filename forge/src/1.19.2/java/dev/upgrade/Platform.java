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
import java.util.List;

public final class Platform {
    public static void success(net.minecraft.commands.CommandSourceStack source,java.util.function.Supplier<net.minecraft.network.chat.Component> message,boolean broadcast) { source.sendSuccess(message.get(),broadcast); }
    public static double foodValue(ItemStack stack) {
        try {
        var food=stack.getItem().getFoodProperties();
        return food==null?0:Math.max(6,food.getNutrition()*2+food.getNutrition()*food.getSaturationModifier()*4);
        } catch (RuntimeException ignored) { return 0; }
    }
    public static boolean glass(net.minecraft.world.level.block.Block block) { return block instanceof net.minecraft.world.level.block.AbstractGlassBlock; }
    public static net.minecraft.world.item.crafting.Ingredient ingredient(com.google.gson.JsonElement json) { return net.minecraft.world.item.crafting.Ingredient.fromJson(json); }
    public record RecipeRef(ResourceLocation id,Recipe<?> recipe) {}
    public static List<RecipeRef> recipes(MinecraftServer server) { return server.getRecipeManager().getRecipes().stream().map(r -> new RecipeRef(r.getId(),r)).toList(); }
    public static CompoundTag data(Player player) { return player.getPersistentData(); }
    public static CompoundTag tag(ItemStack stack) { return stack.hasTag()?stack.getTag():new CompoundTag(); }
    public static boolean hasTag(ItemStack stack) { return stack.hasTag(); }
    public static boolean ordinaryData(ItemStack stack) {
        if (!stack.hasTag()) return true;
        var tag=stack.getTag();
        return tag.size()==1 && tag.contains("Damage",Tag.TAG_INT) && tag.getInt("Damage")==0 && stack.isDamageableItem();
    }
    public static ItemStack pricingCopy(ItemStack stack,java.util.Set<String> allowed) {
        var copy=stack.copy();
        if (copy.isDamageableItem()) copy.setDamageValue(0);
        if (copy.hasTag()) { allowed.forEach(copy.getTag()::remove); if (copy.getTag().isEmpty()) copy.setTag(null); }
        return copy;
    }
    public static java.util.Map<String,Double> attributes(ItemStack stack,net.minecraft.world.entity.EquipmentSlot slot) {
        var accum=new java.util.HashMap<String,double[]>();
        stack.getAttributeModifiers(slot).forEach((attribute,modifier)->PerformancePricing.attribute(accum,
                net.minecraft.core.Registry.ATTRIBUTE.getKey(attribute).getPath(),attribute.getDefaultValue(),
                modifier.getAmount(),modifier.getOperation().ordinal()));
        return PerformancePricing.attributes(accum);
    }
    public static boolean sameItemData(ItemStack a,ItemStack b) { return ItemStack.isSameItemSameTags(a,b); }
    public static boolean hasStorage(ItemStack stack) {
        return stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
    }
    public static void writeItem(FriendlyByteBuf b,ItemStack stack) { b.writeItem(stack); }
    public static ItemStack readItem(FriendlyByteBuf b) { return b.readItem(); }
    public static CompoundTag saveItem(ServerPlayer player,ItemStack stack) { return stack.save(new CompoundTag()); }
    public static ItemStack loadItem(ServerPlayer player,CompoundTag tag) { return ItemStack.of(tag); }
    public static com.google.gson.JsonObject lootJson(MinecraftServer server,ResourceLocation id) {
        return net.minecraft.world.level.storage.loot.Deserializers.createLootTableSerializer().create()
                .toJsonTree(server.getLootTables().get(id)).getAsJsonObject();
    }
    public static java.util.Collection<ResourceLocation> lootKeys(MinecraftServer server) {
        return server.getLootTables().getIds();
    }
    public static net.minecraft.world.level.storage.loot.LootTable blockLoot(MinecraftServer server,net.minecraft.world.level.block.Block block) {
        return server.getLootTables().get(block.getLootTable());
    }
    public static ResourceLocation mobLootId(net.minecraft.world.entity.Mob mob) { return mob.getLootTable(); }
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
