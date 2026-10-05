package dev.upgrade;
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="upgrade")
public class ForgeProbe {
 @net.minecraftforge.eventbus.api.listener.SubscribeEvent
 public static void started(net.minecraftforge.event.server.ServerStartedEvent e) { ServerProbe.run(e.getServer()); }
 @net.minecraftforge.eventbus.api.listener.SubscribeEvent
 public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) { if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) Fixture.login(p); }
}
