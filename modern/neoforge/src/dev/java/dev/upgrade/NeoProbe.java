package dev.upgrade;
@net.neoforged.fml.common.Mod("upgrade")
public class NeoProbe {
 public NeoProbe() { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)Fixture.login(p);}); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e)->{ServerProbe.run(e.getServer());}); }
}
