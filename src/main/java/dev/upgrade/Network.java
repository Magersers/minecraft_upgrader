package dev.upgrade;

import dev.upgrade.client.UpgradeScreen;
import dev.upgrade.core.CostEngine;
import dev.upgrade.core.RollTiming;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import java.security.SecureRandom;
import java.util.*;

public final class Network {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Upgrade.ID,"main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final SecureRandom RNG = new SecureRandom();
    private static final int CHUNK = 64;
    private static final String PENDING = "upgradePendingRoll";
    private record Session(UUID token, UUID revision, int slot, ItemStack stake) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> QUERY_TICK = new HashMap<>();
    public record Query(boolean open) {}
    public record Spin(UUID token, String target, int count) {}
    public record Finish(UUID token) {}
    public record Entry(String id, double value, double confidence, String reason, boolean available) {}
    public record Catalog(UUID token, List<Entry> entries, int offset, int total, String stake,
                          int count, double value, String reason, boolean open) {}
    public record Outcome(UUID token, boolean accepted, boolean won, double chance, double roll, String message) {}
    public record Settled(UUID token, boolean won, String target) {}

    public static void clear() { SESSIONS.clear(); QUERY_TICK.clear(); }
    public static void forget(UUID id) { SESSIONS.remove(id); QUERY_TICK.remove(id); }
    public static void init() {
        CHANNEL.messageBuilder(Query.class,0,NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b) -> b.writeBoolean(p.open())).decoder(b -> new Query(b.readBoolean()))
                .consumerMainThread((p,c) -> { var player=c.get().getSender(); if(player!=null) catalog(player,p.open()); }).add();
        CHANNEL.messageBuilder(Spin.class,1,NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b) -> { b.writeUUID(p.token()); b.writeUtf(p.target(),256); b.writeVarInt(p.count()); })
                .decoder(b -> new Spin(b.readUUID(),b.readUtf(256),b.readVarInt()))
                .consumerMainThread((p,c) -> { var player=c.get().getSender(); if(player!=null) spin(player,p); }).add();
        CHANNEL.messageBuilder(Catalog.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder(Network::encodeCatalog).decoder(Network::decodeCatalog)
                .consumerMainThread((p,c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> UpgradeScreen.receive(p))).add();
        CHANNEL.messageBuilder(Outcome.class,3,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p,b) -> { b.writeUUID(p.token()); b.writeBoolean(p.accepted()); b.writeBoolean(p.won()); b.writeDouble(p.chance()); b.writeDouble(p.roll()); b.writeUtf(p.message(),512); })
                .decoder(b -> new Outcome(b.readUUID(),b.readBoolean(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readUtf(512)))
                .consumerMainThread((p,c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> UpgradeScreen.receive(p))).add();
        CHANNEL.messageBuilder(Finish.class,4,NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b) -> b.writeUUID(p.token())).decoder(b -> new Finish(b.readUUID()))
                .consumerMainThread((p,c) -> {
                    var player=c.get().getSender();
                    if (player != null && player.getPersistentData().contains(PENDING)) {
                        var pending = player.getPersistentData().getCompound(PENDING);
                        if (pending.hasUUID("token") && pending.getUUID("token").equals(p.token())) pending.putBoolean("acknowledged",true);
                    }
                }).add();
        CHANNEL.messageBuilder(Settled.class,5,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p,b) -> { b.writeUUID(p.token()); b.writeBoolean(p.won()); b.writeUtf(p.target(),256); })
                .decoder(b -> new Settled(b.readUUID(),b.readBoolean(),b.readUtf(256)))
                .consumerMainThread((p,c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> UpgradeScreen.receive(p))).add();
    }
    private static void encodeCatalog(Catalog p, FriendlyByteBuf b) {
        b.writeUUID(p.token()); b.writeVarInt(p.offset()); b.writeVarInt(p.total()); b.writeUtf(p.stake(),256);
        b.writeVarInt(p.count()); b.writeDouble(p.value()); b.writeUtf(p.reason(),2048); b.writeBoolean(p.open()); b.writeVarInt(p.entries().size());
        for (Entry e : p.entries()) {
            b.writeUtf(e.id(),256); b.writeDouble(e.value()); b.writeDouble(e.confidence()); b.writeUtf(e.reason(),2048); b.writeBoolean(e.available());
        }
    }
    private static Catalog decodeCatalog(FriendlyByteBuf b) {
        UUID token=b.readUUID(); int offset=b.readVarInt(),total=b.readVarInt(); String stake=b.readUtf(256);
        int count=b.readVarInt(); double value=b.readDouble(); String reason=b.readUtf(2048); boolean open=b.readBoolean(); int n=b.readVarInt();
        if (n<0 || n>CHUNK || offset<0 || total<0 || total>100000 || offset+n>total) throw new IllegalArgumentException("catalog size");
        List<Entry> entries=new ArrayList<>();
        for(int i=0;i<n;i++) entries.add(new Entry(b.readUtf(256),b.readDouble(),b.readDouble(),b.readUtf(2048),b.readBoolean()));
        return new Catalog(token,List.copyOf(entries),offset,total,stake,count,value,reason,open);
    }
    private static void send(ServerPlayer player,Object message) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),message); }
    public static void catalog(ServerPlayer player, boolean open) {
        int tick=player.server.getTickCount(); UUID id=player.getUUID();
        if (tick-QUERY_TICK.getOrDefault(id,-100)<10) return;
        QUERY_TICK.put(id,tick);
        if (player.getPersistentData().contains(PENDING)) {
            player.displayClientMessage(Component.literal("Дождитесь завершения текущего апгрейда."),true); return;
        }
        ItemStack hand=player.getMainHandItem(); var value=Economy.usable(player,hand); UUID token=UUID.randomUUID();
        SESSIONS.put(id,new Session(token,Economy.current.revision(),player.getInventory().selected,hand.copy()));
        // Search is local, against localized display names. Send bounded chunks once per refresh.
        List<Entry> entries=new ArrayList<>();
        for (ResourceLocation key : new TreeSet<>(ForgeRegistries.ITEMS.getKeys())) {
            ItemStack stack=new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(key)));
            if (stack.isEmpty()) continue;
            var price=Economy.current.values().get(key.toString());
            entries.add(new Entry(key.toString(),price==null?0:price.cost(),price==null?0:price.confidence(),
                    Economy.reason(player,stack),Economy.usable(player,stack)!=null));
        }
        for (int offset=0; offset<entries.size() || offset==0; offset+=CHUNK) {
            send(player,new Catalog(token,List.copyOf(entries.subList(offset,Math.min(offset+CHUNK,entries.size()))),
                    offset,entries.size(),Economy.id(hand),hand.getCount(),value==null?0:value.cost(),Economy.reason(player,hand),open&&offset==0));
        }
    }
    private static void reject(ServerPlayer player, UUID token, String reason) { send(player,new Outcome(token,false,false,0,0,reason)); }
    private static void spin(ServerPlayer player, Spin packet) {
        Session session=SESSIONS.get(player.getUUID());
        if(session==null || !session.token().equals(packet.token())) { reject(player,packet.token(),"Сессия устарела. Обновите каталог."); return; }
        if(player.getPersistentData().contains(PENDING)) { reject(player,packet.token(),"Дождитесь окончания вращения."); return; }
        SESSIONS.remove(player.getUUID());
        QUERY_TICK.remove(player.getUUID());
        if(!session.revision().equals(Economy.current.revision()) || !player.isAlive() || player.isSpectator() || player.isCreative()) {
            reject(player,packet.token(),"Нужен режим выживания и актуальная оценка."); return;
        }
        ItemStack hand=player.getMainHandItem();
        if(player.getInventory().selected!=session.slot() || !ItemStack.matches(hand,session.stake())
                || packet.count()<1 || packet.count()>64 || packet.count()>hand.getCount()) {
            reject(player,packet.token(),"Предмет в руке изменился. Обновите каталог."); return;
        }
        ResourceLocation key=ResourceLocation.tryParse(packet.target());
        if(key==null || !ForgeRegistries.ITEMS.containsKey(key)) { reject(player,packet.token(),"Неизвестная цель."); return; }
        ItemStack reward=new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(key)));
        var source=Economy.usable(player,hand); var target=Economy.usable(player,reward);
        if(source==null || target==null) { reject(player,packet.token(),"Предмет заблокирован или не оценён."); return; }
        double chance;
        try { chance=CostEngine.chance(source.cost()*packet.count(),target.cost(),Economy.EFFICIENCY); }
        catch(IllegalArgumentException ex) { reject(player,packet.token(),"Цена цели должна превышать ставку."); return; }
        double roll=RNG.nextDouble(); boolean won=roll<chance;
        CompoundTag pending=new CompoundTag();
        pending.putUUID("token",packet.token()); pending.putBoolean("won",won); pending.putString("target",packet.target());
        pending.putLong("due",player.server.overworld().getGameTime()+RollTiming.TICKS);
        pending.put("reward",reward.save(new CompoundTag()));
        // Keep a pending payout on the player across disconnect/restart and player cloning.
        player.getPersistentData().put(PENDING,pending);
        hand.shrink(packet.count()); player.inventoryMenu.broadcastChanges();
        Upgrade.LOGGER.info("Upgrade player={} revision={} input={} count={} target={} chance={} won={}",
                player.getUUID(),session.revision(),Economy.id(session.stake()),packet.count(),packet.target(),chance,won);
        send(player,new Outcome(packet.token(),true,won,chance,roll,""));
    }
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) settle(player,server.overworld().getGameTime());
    }
    static void settle(ServerPlayer player, long now) {
        if (!player.isAlive() || !player.getPersistentData().contains(PENDING)) return;
        CompoundTag pending=player.getPersistentData().getCompound(PENDING);
        if (!RollTiming.canSettle(now,pending.getLong("due"),pending.getBoolean("acknowledged"))) return;
        // Remove before delivery: repeated Finish packets cannot deliver twice.
        player.getPersistentData().remove(PENDING);
        boolean won=pending.getBoolean("won");
        if (won) {
            ItemStack reward=ItemStack.of(pending.getCompound("reward"));
            if (!player.getInventory().add(reward)) player.drop(reward,false);
        }
        player.inventoryMenu.broadcastChanges();
        send(player,new Settled(pending.getUUID("token"),won,pending.getString("target")));
        player.displayClientMessage(Component.literal(won?"Победа! Предмет получен.":"Поражение. Ставка потрачена."),true);
    }
    public static void copyPending(net.minecraft.world.entity.player.Player from, net.minecraft.world.entity.player.Player to) {
        if (from.getPersistentData().contains(PENDING)) to.getPersistentData().put(PENDING,from.getPersistentData().getCompound(PENDING).copy());
    }
}
