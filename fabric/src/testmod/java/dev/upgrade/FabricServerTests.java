package dev.upgrade;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;

/** Runs only in the isolated development test mod, never shipped in the release jar. */
public final class FabricServerTests implements ModInitializer {
 public void onInitialize() {
  if (!Boolean.getBoolean("upgrade.serverTests")) return;
  ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> {
   try {
    Economy.rebuild(server);
    var ctx=new TestContext(server.overworld());
    price(ctx,"oak_planks",1); price(ctx,"stick",.5); price(ctx,"chest",8); 
    boolean compat=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tconstruct");
    if (!compat) { price(ctx,"iron_block",67.5);
    price(ctx,"diamond_pickaxe",361); price(ctx,"cake",67); }
    ctx.assertTrue(Economy.current.values().size()>700,"Vanilla recipe coverage");
    var tool=new ItemStack(Items.DIAMOND_PICKAXE);
    ctx.assertTrue(Economy.plain(tool),"Fresh tool should be valued"); tool.setDamageValue(1);
    ctx.assertTrue(!Economy.plain(tool),"Damaged tool should be excluded");
    tool.setDamageValue(0); TestPlatform.name(tool);
    ctx.assertTrue(!Economy.plain(tool),"Named items should be excluded");
    NetworkGameTests.delayedAndExactlyOncePayout(ctx);
    NetworkGameTests.lossAndInterruptedPlayerRecovery(ctx);
    NetworkGameTests.inventorySlotAndStackReward(ctx);
    NetworkGameTests.priceUsesWholeRewardStack(ctx);
    NetworkGameTests.rejectForgedCountsAndChangedSlots(ctx);
    var player=TestPlatform.player(server.overworld());
    Platform.data(player).putString("saved-test","preserved");
    var save=new CompoundTag(); player.addAdditionalSaveData(save);
    var restored=TestPlatform.player(server.overworld()); restored.readAdditionalSaveData(save);
    ctx.assertTrue(Platform.data(restored).getString("saved-test").equals("preserved"),"Mixin must persist player data");
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
    Upgrade.LOGGER.info("FABRIC SERVER TESTS PASS: {} valued items",Economy.current.values().size());
    server.halt(false);
   } catch (Throwable failure) { Upgrade.LOGGER.error("FABRIC SERVER TESTS FAILED",failure); System.exit(1); }
  }));
 }
 private static void price(TestContext ctx,String id,double expected) {
  var value=Economy.current.values().get("minecraft:"+id);
  ctx.assertTrue(value!=null && Math.abs(value.cost()-expected)<1e-7,"Price "+id+": "+value+", expected "+expected);
 }
}
