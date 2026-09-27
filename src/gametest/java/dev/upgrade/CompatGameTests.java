package dev.upgrade;

import dev.upgrade.compat.TinkersCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(Upgrade.ID)
@PrefixGameTestTemplate(false)
public final class CompatGameTests {
    @GameTest(template="empty", timeoutTicks=400)
    public static void tinkersMaterialsAndFluids(GameTestHelper helper) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("tconstruct")) { helper.succeed(); return; }
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
    @GameTest(template="empty", timeoutTicks=400)
    public static void hydraDifficultyAndLootYield(GameTestHelper helper) {
        if (!ModList.get().isLoaded("twilightforest")) { helper.succeed(); return; }
        var values=Economy.current.values();
        for (String id:new String[]{"fiery_blood","hydra_chop","hydra_trophy","fiery_ingot","fiery_sword","naga_scale"})
            helper.assertTrue(values.containsKey("twilightforest:"+id),"Missing boss loot/recipe price: "+id);
        double blood=values.get("twilightforest:fiery_blood").cost(),chop=values.get("twilightforest:hydra_chop").cost(),trophy=values.get("twilightforest:hydra_trophy").cost();
        helper.assertTrue(Math.abs(blood*8.5-chop*20)<1e-6,"Actual Hydra loot means: 8.5 blood and 20 chops");
        helper.assertTrue(Math.abs(blood*8.5-trophy)<1e-6,"Trophy costs one successful encounter");
        helper.assertTrue(values.get("twilightforest:fiery_ingot").cost()>=blood,"Fiery equipment must include boss effort");
        helper.assertTrue(values.get("twilightforest:fiery_blood").source().contains("HP 360"),"Use live Hydra health");
        Upgrade.LOGGER.info("HYDRA PRICE blood={} chop={} trophy={}",blood,chop,trophy);
        for (String item:new String[]{"alpha_yeti_fur","fiery_tears","carminite","meef_stroganoff","twilight_scepter","triple_bow"})
            helper.assertTrue(values.containsKey("twilightforest:"+item),"Missing additional boss price "+item);
        double scepter=values.get("twilightforest:twilight_scepter").cost(),lich=values.get("twilightforest:lich_trophy").cost();
        helper.assertTrue(Math.abs(scepter-lich*4)<1e-6,"Lich has four equally weighted scepter rewards");
        helper.succeed();
    }
    private static ItemStack stack(String id) { return new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id))); }
}
