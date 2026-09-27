package dev.upgrade;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class Payloads {
    public record Request(Object message) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(Ids.of("upgrade:request_v4"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> Wire.encode(b,p.message()),b -> new Request(Wire.decode(b,false)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Reply(Object message) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(Ids.of("upgrade:reply_v4"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,p) -> Wire.encode(b,p.message()),b -> new Reply(Wire.decode(b,true)));
        @Override public Type<Reply> type() { return TYPE; }
    }
}
