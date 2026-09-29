package dev.upgrade;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;

/** Runs only in the isolated development test mod, never shipped in the release jar. */
@net.neoforged.fml.common.EventBusSubscriber(modid="upgrade")
public final class NeoForgeServerTests {
 @net.neoforged.bus.api.SubscribeEvent
 public static void run(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
  if (!Boolean.getBoolean("upgrade.serverTests")) return;
  var server=event.getServer(); server.execute(() -> {
   try {
    Economy.rebuild(server);
    EconomyAudit.write();
    var ctx=new TestContext(server.overworld());
    try (var reader=server.getResourceManager().getResourceOrThrow(Ids.of("upgrade:upgrade_values/baseline.json")).openAsReader()) {
     var baseline=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
     for (var source:baseline.getAsJsonArray("sources")) {
      var entry=source.getAsJsonObject();
      ctx.assertTrue(!entry.has("tag") || !entry.get("tag").getAsString().startsWith("forge:"),"Packaged baseline must use NeoForge common tags");
     }
    }
    price(ctx,"oak_planks",1); price(ctx,"stick",.5); price(ctx,"chest",8); 
    boolean compat=LoaderPlatform.isLoaded("tconstruct");
    if (!compat && !LoaderPlatform.isLoaded("bclib")) { price(ctx,"iron_block",1080);
    price(ctx,"diamond_pickaxe",6001); price(ctx,"cake",112); }
    ctx.assertTrue(Economy.current.values().size()>700,"Vanilla recipe coverage");
    ctx.assertTrue(!Economy.current.values().containsKey("minecraft:infested_cobblestone"),"Infested blocks must not inherit the cobblestone baseline");
    var tool=new ItemStack(Items.DIAMOND_PICKAXE);
    ctx.assertTrue(Economy.plain(tool),"Fresh tool should be valued"); tool.setDamageValue(1);
    ctx.assertTrue(!Economy.plain(tool),"Damaged tool should be excluded");
    tool.setDamageValue(0); TestPlatform.name(tool);
    ctx.assertTrue(!Economy.plain(tool),"Named items should be excluded");
    var lootMethod=dev.upgrade.compat.EncounterProfiles.class.getDeclaredMethod("loot",net.minecraft.server.MinecraftServer.class,net.minecraft.resources.ResourceLocation.class,java.util.Set.class);
    lootMethod.setAccessible(true);
    var drops=(java.util.Map<?,?>)lootMethod.invoke(null,server,Ids.of("minecraft:entities/skeleton"),new java.util.HashSet<>());
    ctx.assertTrue(Double.valueOf(1).equals(drops.get("minecraft:bone")) && Double.valueOf(1).equals(drops.get("minecraft:arrow")),"Read actual skeleton loot with Looting 0 in both formats");
    InheritanceTests.run(ctx,server);
    PerformanceTests.run(ctx,server);
    BalanceTests.run(ctx);
    NetworkGameTests.delayedAndExactlyOncePayout(ctx);
    NetworkGameTests.lossAndInterruptedPlayerRecovery(ctx);
    NetworkGameTests.inventorySlotAndStackReward(ctx);
    NetworkGameTests.priceUsesWholeRewardStack(ctx);
    NetworkGameTests.rejectForgedCountsAndChangedSlots(ctx);
    NetworkGameTests.wearAndHardMode(ctx);
    var player=TestPlatform.player(server.overworld());
    Platform.data(player).putString("saved-test","preserved");
    // NeoForge persists entity data in the full save/load lifecycle, outside
    // Player.addAdditionalSaveData (where Fabric's mixin used to run).
    var save=new CompoundTag(); player.saveWithoutId(save);
    var restored=TestPlatform.player(server.overworld()); restored.load(save);
    ctx.assertTrue(Platform.data(restored).getString("saved-test").equals("preserved"),"NeoForge must persist player data");
    var itemBuffer=TestPlatform.buffer(server.overworld());
    try {
     var stack=new ItemStack(Items.DIAMOND,8); TestPlatform.name(stack);
     Platform.writeItem(itemBuffer,stack); var decoded=Platform.readItem(itemBuffer);
     ctx.assertTrue(ItemStack.matches(stack,decoded),"Inventory codec must preserve count and custom data");
    } finally { itemBuffer.release(); }
    var packet=new Network.Spin(UUID.randomUUID(),"minecraft:diamond",3,12,8);
    var buf=new FriendlyByteBuf(Unpooled.buffer());
    try { Wire.encode(buf,packet); ctx.assertTrue(packet.equals(Wire.decode(buf,false)),"Spin codec roundtrip"); }
    finally { buf.release(); }
    buf=new FriendlyByteBuf(Unpooled.buffer());
    try {
     buf.writeVarInt(4); buf.writeVarInt(2);
     boolean rejected=false; try { Wire.decode(buf,false); } catch (IllegalArgumentException expected) { rejected=true; }
     ctx.assertTrue(rejected,"Reject clientbound payload before reading inventory");
    } finally { buf.release(); }
    if (compat) Class.forName("dev.upgrade.HephaestusTests").getMethod("tinkersMaterialsAndFluids",TestContext.class).invoke(null,ctx);
    Upgrade.LOGGER.info("NEOFORGE SERVER TESTS PASS: {} valued items",Economy.current.values().size());
    java.nio.file.Files.writeString(java.nio.file.Path.of("upgrade-tests-passed.txt"),"PASS\n");
    server.halt(false);
   } catch (Throwable failure) { Upgrade.LOGGER.error("NEOFORGE SERVER TESTS FAILED",failure); server.halt(false); }
  });
 }
 private static void price(TestContext ctx,String id,double expected) {
  var value=Economy.current.values().get("minecraft:"+id);
  ctx.assertTrue(value!=null && Math.abs(value.cost()-expected)<1e-7,"Price "+id+": "+value+", expected "+expected);
 }
}
