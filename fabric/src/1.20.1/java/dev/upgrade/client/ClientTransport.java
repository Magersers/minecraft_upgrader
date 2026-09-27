package dev.upgrade.client;
import dev.upgrade.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
public final class ClientTransport {
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ServerTransport.ID,(client,handler,buf,sender) -> {
            byte[] bytes=new byte[buf.readableBytes()]; buf.readBytes(bytes);
            client.execute(() -> {
                var b=new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                try { ClientEvents.receive(Wire.decode(b,true)); } finally { b.release(); }
            });
        });
    }
    public static void send(Object packet) {
        if (!ClientPlayNetworking.canSend(ServerTransport.ID)) { ClientEvents.unavailable(); return; }
        var b=PacketByteBufs.create(); Wire.encode(b,packet); ClientPlayNetworking.send(ServerTransport.ID,b);
    }
}
