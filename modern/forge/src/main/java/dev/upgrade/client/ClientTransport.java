package dev.upgrade.client;
import dev.upgrade.ServerTransport;
import net.minecraft.client.Minecraft;
public final class ClientTransport {
    public static void send(Object packet) {
        var connection=Minecraft.getInstance().getConnection();
        if(connection==null || !ServerTransport.CHANNEL.isRemotePresent(connection.getConnection())) { ClientEvents.unavailable(); return; }
        ServerTransport.CHANNEL.send(new ServerTransport.Request(packet),net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }
}
