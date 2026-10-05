package dev.upgrade;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
public final class ServerTransport {
    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Request.TYPE,Payloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Reply.TYPE,Payloads.Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Request.TYPE,(p,c) -> Network.handle(c.player(),p.message()));
    }
    public static void send(ServerPlayer player,Object packet) {
        if (player.connection==null) return;
        if (ServerPlayNetworking.canSend(player,Payloads.Reply.TYPE)) ServerPlayNetworking.send(player,new Payloads.Reply(packet));
    }
}
