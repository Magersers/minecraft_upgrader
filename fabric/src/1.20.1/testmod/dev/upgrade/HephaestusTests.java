package dev.upgrade;

import dev.upgrade.compat.TinkersCompat;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class HephaestusTests {
    public static void tinkersMaterialsAndFluids(TestContext helper) throws ReflectiveOperationException {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tconstruct")) { helper.succeed(); return; }
        Economy.rebuild(helper.getLevel().getServer());
        for (String id:new String[]{"tconstruct:cobalt_ingot","tconstruct:manyullyn_ingot","tconstruct:rose_gold_ingot","tconstruct:seared_brick","tconstruct:pick_head#tconstruct:iron","tconstruct:pick_head#tconstruct:wood"}) {
            var price=Economy.current.values().get(id);
            helper.assertTrue(price!=null,"Tinkers missing "+id+"; "+Economy.current.explanations().get(id));
            Upgrade.LOGGER.info("COMPAT PRICE {} = {}",id,price.cost());
        }
        var values=Economy.current.values();
        double manyullyn=values.get("tconstruct:manyullyn_ingot").cost();
        helper.assertTrue(manyullyn>values.get("tconstruct:cobalt_ingot").cost(),"Manyullyn must charge cobalt AND debris");
        helper.assertTrue(values.get("tconstruct:pick_head#tconstruct:iron").cost()>values.get("tconstruct:pick_head#tconstruct:wood").cost(),"Part prices must vary with material");
        ItemStack part=stack("tconstruct:pick_head"); part.getOrCreateTag().putString("Material","tconstruct:iron");
        helper.assertTrue(!Economy.plain(part)&&TinkersCompat.quote(part,values)!=null,"Valid material part needs its own NBT quote");
        part.setHoverName(net.minecraft.network.chat.Component.literal("Special"));
        helper.assertTrue(TinkersCompat.quote(part,values)==null,"Do not strip arbitrary part NBT");
        ItemStack pick=stack("tconstruct:pickaxe");
        helper.assertTrue(!Economy.plain(pick),"Blank tool without materials must not get an ordinary item price");
        Class<?> nbtType=Class.forName("slimeknights.tconstruct.library.tools.nbt.MaterialNBT");
        ListTag list=new ListTag(); for (String material:new String[]{"iron","wood","wood"}) list.add(StringTag.valueOf("tconstruct:"+material));
        Object materials=nbtType.getMethod("readFromNBT",Tag.class).invoke(null,list);
        Class<?> toolType=Class.forName("slimeknights.tconstruct.library.tools.item.IModifiable");
        pick=(ItemStack)Class.forName("slimeknights.tconstruct.library.tools.helper.ToolBuildHandler").getMethod("buildItemFromMaterials",toolType,nbtType).invoke(null,pick.getItem(),materials);
        helper.assertTrue(TinkersCompat.quote(pick,values)!=null,"Fresh material tool must have composition price");
        pick.getOrCreateTag().putBoolean("tic_broken",true);
        helper.assertTrue(TinkersCompat.quote(pick,values)==null,"Broken tool cannot be sold as fresh");
        helper.succeed();
    }
    private static ItemStack stack(String id) { return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id))); }
}
