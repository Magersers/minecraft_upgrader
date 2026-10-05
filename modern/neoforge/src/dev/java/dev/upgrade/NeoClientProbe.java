package dev.upgrade;
@net.neoforged.fml.common.Mod(value="upgrade",dist=net.neoforged.api.distmarker.Dist.CLIENT)
public class NeoClientProbe {
 public NeoClientProbe() { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->{try{if(Boolean.getBoolean("upgrade.e2eClient")) dev.upgrade.client.LiveUpgradeTest.tick(); else dev.upgrade.client.ClientSmokeTest.tick();}catch(Exception ex){throw new RuntimeException(ex);}}); }
}
