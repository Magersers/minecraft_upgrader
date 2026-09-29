package dev.upgrade.client;

import dev.upgrade.Payloads;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientTransport {
    public static void send(Object packet) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(Payloads.Request.TYPE)) {
            ClientEvents.unavailable();
            return;
        }
        PacketDistributor.sendToServer(new Payloads.Request(packet));
    }
}
