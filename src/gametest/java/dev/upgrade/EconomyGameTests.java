package dev.upgrade;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Upgrade.ID)
@PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    @GameTest(template="empty", timeoutTicks=200)
    public static void vanillaRecipeCoverage(GameTestHelper helper) {
        Economy.rebuild(helper.getLevel().getServer());
        var tool = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        helper.assertTrue(Economy.plain(tool), "Fresh Damage:0 tool must be allowed");
        tool.setDamageValue(1);
        helper.assertTrue(!Economy.plain(tool), "Damaged equipment must not be valued as new");
        tool.setDamageValue(0);
        tool.setHoverName(net.minecraft.network.chat.Component.literal("Custom name"));
        helper.assertTrue(!Economy.plain(tool), "Custom item data must stay excluded");
        price(helper,"oak_planks",1);
        price(helper,"stick",.5);
        price(helper,"chest",8);
        price(helper,"crafting_table",4);
        price(helper,"paper",1);
        if (!net.minecraftforge.fml.ModList.get().isLoaded("tconstruct")) {
        price(helper,"iron_sword",15.5);
        price(helper,"diamond_pickaxe",361);
        price(helper,"iron_block",67.5);
        price(helper,"iron_nugget",7.5/9);
        price(helper,"cake",67);
        price(helper,"netherite_chestplate",2118);
        }
        helper.assertTrue(Economy.current.values().size()>700,"Expected broad vanilla coverage, got "+Economy.current.values().size());
        helper.assertTrue(Economy.current.explanations().get("minecraft:diamond_pickaxe").contains("minecraft:stick"),"Missing recipe breakdown");
        helper.assertTrue(!Economy.current.values().containsKey("minecraft:command_block"),"Command block must stay excluded");
        Upgrade.LOGGER.info("GAME TEST COVERAGE: {} valued items",Economy.current.values().size());
        // Simulate a mod subclass and an ingredient with one unknown alternative.
        var manager = helper.getLevel().getServer().getRecipeManager();
        var originals = java.util.List.copyOf(manager.getRecipes());
        try {
            var modded = new net.minecraft.world.item.crafting.ShapelessRecipe(
                    net.minecraft.resources.ResourceLocation.tryParse("upgrade:test_mod_recipe"), "",
                    net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DRAGON_BREATH),
                    net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                            net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.DIAMOND,
                                    net.minecraft.world.item.Items.EXPERIENCE_BOTTLE))) {};
            var recipes = new java.util.ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(originals);
            recipes.add(modded); manager.replaceRecipes(recipes);
            Economy.rebuild(helper.getLevel().getServer());
            price(helper, "dragon_breath", Economy.current.values().get("minecraft:diamond").cost());
        } finally {
            manager.replaceRecipes(originals);
            Economy.rebuild(helper.getLevel().getServer());
        }
        helper.succeed();
    }
    private static void price(GameTestHelper helper,String name,double expected) {
        var value=Economy.current.values().get("minecraft:"+name);
        helper.assertTrue(value!=null,"Missing value: "+name+"; "+Economy.current.explanations().get("minecraft:"+name));
        helper.assertTrue(Math.abs(value.cost()-expected)<1e-7,name+": expected "+expected+", got "+value.cost());
    }
}
