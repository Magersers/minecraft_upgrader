package dev.upgrade;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="upgrade",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ForgeClientProbe {
 @net.minecraftforge.eventbus.api.listener.SubscribeEvent
 public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent.Post e) { try{if(Boolean.getBoolean("upgrade.e2eClient")) dev.upgrade.client.LiveUpgradeTest.tick(); else dev.upgrade.client.ClientSmokeTest.tick();}catch(Exception ex){throw new RuntimeException(ex);} }
}
