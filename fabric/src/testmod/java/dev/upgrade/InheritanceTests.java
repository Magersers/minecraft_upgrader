package dev.upgrade;

import dev.upgrade.compat.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.*;
import java.util.*;

final class InheritanceTests {
    static void run(TestContext ctx,net.minecraft.server.MinecraftServer server) throws Exception {
        ctx.assertTrue(NaturalInheritance.encounterEffort(200,10,20,8)>NaturalInheritance.encounterEffort(20,0,3,8)*5,"Boss attributes must substantially increase drop effort");
        var any=Platform.recipes(server).stream().map(Platform.RecipeRef::recipe).filter(r->r instanceof net.minecraft.world.item.crafting.CraftingRecipe).findFirst().orElseThrow();
        var route=RecipeInheritance.read(server,new Platform.RecipeRef(Ids.of("upgrade_test:simple"),any),new ItemStack(Items.TORCH,4),Set.of());
        ctx.assertTrue(route!=null && route.count()==4 && route.inputs().size()==2 && route.overhead()==1,"Generic item-only schema preserves inputs, batch and duration");
        var hidden=RecipeInheritance.read(server,new Platform.RecipeRef(Ids.of("upgrade_test:hidden_fluid"),any),new ItemStack(Items.TORCH,4),Set.of());
        ctx.assertTrue(hidden==null,"Unknown fluid requirement must not be silently omitted");
        boolean rejected=false;
        try { RecipeInheritance.read(server,new Platform.RecipeRef(Ids.of("upgrade_test:counted"),any),new ItemStack(Items.TORCH,4),Set.of()); }
        catch (IllegalArgumentException expected) { rejected=true; }
        ctx.assertTrue(rejected,"Unsupported ingredient counts must not become count one");
        var drops=EncounterProfiles.drops(server,Ids.of("upgrade_test:chests/weighted"));
        ctx.assertTrue(Math.abs(drops.getOrDefault("minecraft:diamond",0.0)-.1)<1e-8,"An unsupported enchanted entry must retain its weight without hiding the plain entry");
        ctx.assertTrue(!drops.containsKey("minecraft:enchanted_book"),"Do not price NBT-bearing rewards as blank items");
        if (FabricLoader.getInstance().isModLoaded("betterend")) {
            for (String id:List.of("betterend:thallasium_raw","betterend:thallasium_ingot","betterend:terminite_ingot","betterend:aeternium_ingot",
                    "betterend:aeternium_axe_head","betterend:enchanted_petal","betternether:cincinnasite","betternether:cincinnasite_ingot")) {
                var value=Economy.current.values().get(id);
                ctx.assertTrue(value!=null && value.cost()>0,"Complete natural -> processed -> crafted chain: "+id);
                ctx.assertTrue(value.gates().contains(id.startsWith("betterend:")?"minecraft:story/enter_the_end":"minecraft:story/enter_the_nether"),"Inherited dimension gate: "+id);
            }
            ctx.assertTrue(!Economy.current.explanations().get("betternether:cincinnasite_ingot").contains("Базовая цена группы"),"Crafting substitution tags must not assign iron price to cincinnasite");
            double iron=Economy.current.values().get("minecraft:iron_ingot").cost();
            double dust=Economy.current.values().get("betterend:ender_dust").cost();
            double terminite=Economy.current.values().get("betterend:terminite_ingot").cost();
            ctx.assertTrue(Math.abs(terminite-(iron+dust+2.25))<1e-6,"Terminite must charge both alloy ingredients and processing");
            long count=Economy.current.values().keySet().stream().filter(id->id.startsWith("betterend:")||id.startsWith("betternether:")).count();
            ctx.assertTrue(count>=1200,"BetterX coverage regression: "+count);
        }
    }
}
