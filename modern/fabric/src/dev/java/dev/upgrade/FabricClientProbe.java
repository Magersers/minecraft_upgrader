package dev.upgrade;
public class FabricClientProbe implements net.fabricmc.api.ClientModInitializer {
 public void onInitializeClient() { net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc->{try { if(Boolean.getBoolean("upgrade.e2eClient")) dev.upgrade.client.LiveUpgradeTest.tick(); else dev.upgrade.client.ClientSmokeTest.tick(); } catch(Exception ex) {throw new RuntimeException(ex);} }); }
}
