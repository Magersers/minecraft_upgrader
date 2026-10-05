package dev.upgrade;
public class FabricProbe implements net.fabricmc.api.ModInitializer {
 public void onInitialize() { net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((h,sender,s)->Fixture.login(h.player)); net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s->{ServerProbe.run(s);}); }
}
