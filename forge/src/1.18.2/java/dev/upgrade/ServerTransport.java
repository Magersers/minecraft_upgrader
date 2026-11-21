package dev.upgrade;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ServerTransport {
    public static Consumer<Object> clientReceiver = message -> {};
    private static final String PROTOCOL = "0.7.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Ids.of("upgrade", "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    public record Request(Object message) {}
    public record Reply(Object message) {}

    public static void register() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> Wire.encode(buffer, packet.message()))
                .decoder(buffer -> new Request(Wire.decode(buffer, false)))
                .consumer(ServerTransport::handleRequest).add();
        CHANNEL.messageBuilder(Reply.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> Wire.encode(buffer, packet.message()))
                .decoder(buffer -> new Reply(Wire.decode(buffer, true)))
                .consumer(ServerTransport::handleReply).add();
    }

    private static void handleRequest(Request packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) Network.handle(player, packet.message());
        });
        context.setPacketHandled(true);
    }

    private static void handleReply(Reply packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> clientReceiver.accept(packet.message()));
        context.setPacketHandled(true);
    }

    public static void send(ServerPlayer player, Object message) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Reply(message));
    }
}
