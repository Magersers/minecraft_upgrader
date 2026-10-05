package dev.upgrade;
import net.minecraft.server.MinecraftServer;
public final class ServerProbe {
 public static void run(MinecraftServer server) { server.execute(()->{
  try {
   Economy.rebuild(server);
   var ctx=new TestContext(server.overworld());
   ctx.assertTrue(Economy.current.values().size()>700,"Recipe coverage: "+Economy.current.values().size());
   price(ctx,"oak_planks",1); price(ctx,"stick",.5); price(ctx,"chest",8); price(ctx,"iron_block",1080);
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
