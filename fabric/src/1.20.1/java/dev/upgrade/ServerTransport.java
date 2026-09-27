package dev.upgrade;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ServerTransport {
    public static final ResourceLocation ID=Ids.of("upgrade:main_v4");
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ID,(server,player,handler,buf,sender) -> {
            try { Object packet=Wire.decode(buf,false); server.execute(() -> Network.handle(player,packet)); }
            catch (RuntimeException ex) { Upgrade.LOGGER.warn("Rejected invalid upgrade packet from {}",player.getUUID()); }
        });
    }
    public static void send(ServerPlayer player,Object packet) {
        if (player.connection==null) return;
        if (!ServerPlayNetworking.canSend(player,ID)) return;
        var buffer=PacketByteBufs.create(); Wire.encode(buffer,packet); ServerPlayNetworking.send(player,ID,buffer);
    }
}
