package dev.upgrade;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
public final class ServerTransport {
    public static Consumer<Object> clientReceiver = message -> {};
    public static final SimpleChannel CHANNEL=ChannelBuilder.named(Ids.of("upgrade","main")).networkProtocolVersion(8).simpleChannel();
    public record Request(Object message) {}
    public record Reply(Object message) {}
    public static void register() {
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder((p,b)->Wire.encode(b,p.message())).decoder(b->new Request(Wire.decode(b,false)))
            .consumerMainThread((p,c)->{ var player=c.getSender(); if(player!=null) Network.handle(player,p.message()); }).add();
        CHANNEL.messageBuilder(Reply.class,1,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->Wire.encode(b,p.message())).decoder(b->new Reply(Wire.decode(b,true)))
            .consumerMainThread((p,c)->clientReceiver.accept(p.message())).add();
    }
    public static void send(ServerPlayer player,Object message) {
        if(player.connection!=null) CHANNEL.send(new Reply(message),PacketDistributor.PLAYER.with(player));
    }
}
