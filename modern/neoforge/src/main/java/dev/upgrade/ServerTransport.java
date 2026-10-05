package dev.upgrade;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ServerTransport {
    // Installed only on the physical client; dedicated servers never resolve client classes.
    public static Consumer<Object> clientReceiver = message -> {};
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("0.8.0");
        registrar.playToServer(Payloads.Request.TYPE, Payloads.Request.CODEC,
                (payload, context) -> Network.handle((ServerPlayer) context.player(), payload.message()));
        registrar.playToClient(Payloads.Reply.TYPE, Payloads.Reply.CODEC,
                (payload, context) -> clientReceiver.accept(payload.message()));
    }
    public static void send(ServerPlayer player, Object packet) {
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new Payloads.Reply(packet));
    }
}
