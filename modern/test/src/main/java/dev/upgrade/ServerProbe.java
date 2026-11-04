package dev.upgrade;
import net.minecraft.server.MinecraftServer;
public final class ServerProbe {
 public static void run(MinecraftServer server) { server.execute(()->{
  try {
   Economy.rebuild(server);
   var ctx=new TestContext(server.overworld());
   ctx.assertTrue(Economy.current.values().size()>700,"Recipe coverage: "+Economy.current.values().size());
   price(ctx,"oak_planks",1); price(ctx,"stick",.5); price(ctx,"chest",8); price(ctx,"iron_block",1080);
   int eligible=0;
   for (var registered:net.minecraft.core.registries.BuiltInRegistries.ITEM) {
    var stack=registered.getDefaultInstance(); String id=Economy.id(stack);
    if (Economy.plain(stack) && !Economy.current.denied().contains(id)) {
     eligible++;
     ctx.assertTrue(Economy.current.values().containsKey(id),"Registry coverage: "+id);
    }
   }
   var coveragePlayer=TestPlatform.player(server.overworld());
   for (var registered:java.util.List.of(net.minecraft.world.item.Items.BUCKET,net.minecraft.world.item.Items.WATER_BUCKET,net.minecraft.world.item.Items.MILK_BUCKET,net.minecraft.world.item.Items.LAVA_BUCKET)) {
    var stack=registered.getDefaultInstance();
    ctx.assertTrue(Economy.target(coveragePlayer,stack)!=null,"Pristine container is available: "+Economy.id(stack));
    ctx.assertTrue(Economy.current.values().get(Economy.id(stack)).cost()>=Economy.current.values().get("minecraft:bucket").cost(),"No value minting by emptying a bucket");
   }
   ctx.assertTrue(Economy.target(coveragePlayer,net.minecraft.world.item.Items.COMMAND_BLOCK_MINECART.getDefaultInstance())==null,"Operator items stay excluded");
   var prices=Economy.current.values();
   price(ctx,"blue_egg",prices.get("minecraft:egg").cost());
   price(ctx,"brown_egg",prices.get("minecraft:egg").cost());
   double cakeNet=3*(prices.get("minecraft:milk_bucket").cost()-prices.get("minecraft:bucket").cost())+2*prices.get("minecraft:sugar").cost()+prices.get("minecraft:egg").cost()+3*prices.get("minecraft:wheat").cost();
   ctx.assertTrue(Economy.usable(coveragePlayer,net.minecraft.world.item.Items.CAKE.getDefaultInstance()).cost()<=cakeNet+1e-7,"Cake stake subtracts returned buckets");
   ctx.assertTrue(prices.get("minecraft:cake").cost()<=cakeNet+1e-7,"Cake reward subtracts returned buckets with vanilla egg alternatives");
        for (String colour:java.util.List.of("white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black")) {
            double concrete=prices.get("minecraft:"+colour+"_concrete").cost(),powder=prices.get("minecraft:"+colour+"_concrete_powder").cost();
            ctx.assertTrue(Math.abs(concrete-powder)<1e-7,"Water hardening preserves concrete acquisition value: "+colour);
        }
        ctx.assertTrue(Math.abs(prices.get("minecraft:oxidized_copper").cost()-prices.get("minecraft:copper_block").cost())<1e-7,"Copper weathering preserves its material tier");
        ctx.assertTrue(Math.abs(prices.get("minecraft:carved_pumpkin").cost()-prices.get("minecraft:pumpkin").cost())<1e-7,"Pumpkin carving does not create an expensive fallback");
        ctx.assertTrue(PerformancePricing.stakeLimits.get("minecraft:carved_pumpkin")+4*PerformancePricing.stakeLimits.get("minecraft:pumpkin_seeds")<=PerformancePricing.stakeLimits.get("minecraft:pumpkin")+1e-7,"Carving accounts for the pumpkin block and four dropped seeds");
        ctx.assertTrue(prices.get("minecraft:warped_nylium").gates().contains("minecraft:story/enter_the_nether"),"World conversion retains Nether progression");
        ctx.assertTrue(prices.get("minecraft:melon_slice").cost()<=prices.get("minecraft:melon").cost()/3+1e-7,"Minimum melon drops price edible slices");
        ctx.assertTrue(9*PerformancePricing.stakeLimits.get("minecraft:melon_slice")<=PerformancePricing.stakeLimits.get("minecraft:melon")+1e-7,"Fortune melon drops cannot mint stake value");
        ctx.assertTrue(prices.get("minecraft:powder_snow_bucket").cost()<=prices.get("minecraft:bucket").cost()+8+1e-7,"Powder snow bucket uses configured collection premium");
   ctx.assertTrue(prices.get("minecraft:pale_moss_block").cost()<=prices.get("minecraft:moss_block").cost()+1e-7,"Pale moss uses the renewable moss tier");
   EconomyAudit.write();
   Upgrade.LOGGER.info("CATALOG COVERAGE PASS: {} eligible registry items, {} fallback estimates",eligible,CatalogPricing.estimated.size());
   NetworkGameTests.delayedAndExactlyOncePayout(ctx);
   NetworkGameTests.lossAndInterruptedPlayerRecovery(ctx);
   NetworkGameTests.inventorySlotAndStackReward(ctx);
   NetworkGameTests.priceUsesWholeRewardStack(ctx);
   NetworkGameTests.rejectForgedCountsAndChangedSlots(ctx);
   NetworkGameTests.wearAndHardMode(ctx);
   var player=TestPlatform.player(server.overworld());
   Platform.data(player).putString("test-persist","saved");
   var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,server.registryAccess());
   player.saveWithoutId(output);
   var restored=TestPlatform.player(server.overworld());
   restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,server.registryAccess(),output.buildResult()));
   ctx.assertTrue(Platform.data(restored).getString("test-persist").orElse("").equals("saved"),"Player data survives save and load");
   var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,8);
   item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Saved prize"));
   ctx.assertTrue(net.minecraft.world.item.ItemStack.matches(item,Platform.loadItem(player,Platform.saveItem(player,item))),"Prize codec preserves count and components");
   java.nio.file.Files.writeString(java.nio.file.Path.of("upgrade-tests-passed.txt"),"PASS 26.3 server upgrade flow; "+Economy.current.values().size()+" priced items\n");
   Upgrade.LOGGER.info("UPGRADE 26.3 SERVER TESTS PASS");
  } catch(Throwable e) { Upgrade.LOGGER.error("UPGRADE 26.3 SERVER TESTS FAILED",e); }
  finally { if(!Boolean.getBoolean("upgrade.keepServer")) server.halt(false); }
 }); }
 private static void price(TestContext ctx,String id,double expected) { var v=Economy.current.values().get("minecraft:"+id);ctx.assertTrue(v!=null&&Math.abs(v.cost()-expected)<1e-7,"Price "+id+": "+v+", expected "+expected); }
}
