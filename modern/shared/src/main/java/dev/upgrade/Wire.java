package dev.upgrade;
import net.minecraft.network.FriendlyByteBuf;

/** Versioned, directional packet schema. Serverbound decoding never constructs clientbound inventories. */
public final class Wire {
    public static void encode(FriendlyByteBuf b,Object packet) {
        b.writeVarInt(4);
        if (packet instanceof Network.Query p) { b.writeVarInt(0); b.writeBoolean(p.open()); }
        else if (packet instanceof Network.Spin p) { b.writeVarInt(1); b.writeUUID(p.token()); b.writeUtf(p.target(),256); b.writeVarInt(p.count()); b.writeVarInt(p.slot()); b.writeVarInt(p.rewardCount()); }
        else if (packet instanceof Network.Catalog p) { b.writeVarInt(2); Network.encodeCatalog(p,b); }
        else if (packet instanceof Network.Outcome p) { b.writeVarInt(3); b.writeUUID(p.token()); b.writeBoolean(p.accepted()); b.writeBoolean(p.won()); b.writeDouble(p.chance()); b.writeDouble(p.roll()); b.writeUtf(p.message(),512); }
        else if (packet instanceof Network.Finish p) { b.writeVarInt(4); b.writeUUID(p.token()); }
        else if (packet instanceof Network.Settled p) { b.writeVarInt(5); b.writeUUID(p.token()); b.writeBoolean(p.won()); b.writeUtf(p.target(),256); b.writeVarInt(p.count()); }
        else throw new IllegalArgumentException("Unknown upgrade packet");
    }
    public static Object decode(FriendlyByteBuf b,boolean clientbound) {
        if (b.readVarInt()!=4) throw new IllegalArgumentException("Update Upgrader on both client and server");
        int kind=b.readVarInt();
        if (clientbound) return switch (kind) {
            case 2 -> Network.decodeCatalog(b);
            case 3 -> new Network.Outcome(b.readUUID(),b.readBoolean(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readUtf(512));
            case 5 -> new Network.Settled(b.readUUID(),b.readBoolean(),b.readUtf(256),b.readVarInt());
            default -> throw new IllegalArgumentException("Unexpected server packet");
        };
        return switch (kind) {
            case 0 -> new Network.Query(b.readBoolean());
            case 1 -> new Network.Spin(b.readUUID(),b.readUtf(256),b.readVarInt(),b.readVarInt(),b.readVarInt());
            case 4 -> new Network.Finish(b.readUUID());
            default -> throw new IllegalArgumentException("Unexpected client packet");
        };
    }
}
