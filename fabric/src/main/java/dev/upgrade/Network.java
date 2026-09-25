package dev.upgrade;


import dev.upgrade.core.CostEngine;
import dev.upgrade.core.RollTiming;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;




import net.minecraft.core.registries.BuiltInRegistries;
import java.security.SecureRandom;
import java.util.*;

public final class Network {
    private static final SecureRandom RNG = new SecureRandom();
    private static final int CHUNK = 64;
    private static final String PENDING = "upgradePendingRoll";
    private record Session(UUID token, UUID revision, Map<Integer, ItemStack> inventory) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> QUERY_TICK = new HashMap<>();
    public record Query(boolean open) {}
    public record Spin(UUID token, String target, int count, int slot, int rewardCount) {}
    public record InventoryEntry(int slot, ItemStack stack, double value, String reason) {}
    public record Finish(UUID token) {}
    public record Entry(String id, double value, double confidence, String reason, boolean available) {}
    public record Catalog(UUID token, List<Entry> entries, int offset, int total, String stake,
                          int count, double value, String reason, boolean open, int selectedSlot, List<InventoryEntry> inventory) {}
    public record Outcome(UUID token, boolean accepted, boolean won, double chance, double roll, String message) {}
    public record Settled(UUID token, boolean won, String target, int count) {}

    public static void clear() { SESSIONS.clear(); QUERY_TICK.clear(); }
    public static void forget(UUID id) { SESSIONS.remove(id); QUERY_TICK.remove(id); }
    public static void handle(ServerPlayer player,Object message) {
        if (message instanceof Query p) catalog(player,p.open());
        else if (message instanceof Spin p) spin(player,p);
        else if (message instanceof Finish p && Platform.data(player).contains(PENDING)) {
            var pending=Platform.data(player).getCompound(PENDING);
            if (pending.hasUUID("token") && pending.getUUID("token").equals(p.token())) pending.putBoolean("acknowledged",true);
        }
    }
    static void encodeCatalog(Catalog p, FriendlyByteBuf b) {
        b.writeUUID(p.token()); b.writeVarInt(p.offset()); b.writeVarInt(p.total()); b.writeUtf(p.stake(),256);
        b.writeVarInt(p.count()); b.writeDouble(p.value()); b.writeUtf(p.reason(),2048); b.writeBoolean(p.open()); b.writeVarInt(p.entries().size());
        b.writeVarInt(p.selectedSlot()); b.writeVarInt(p.inventory().size());
        for (InventoryEntry e : p.inventory()) {
            b.writeVarInt(e.slot()); Platform.writeItem(b,e.stack()); b.writeDouble(e.value()); b.writeUtf(e.reason(),2048);
        }
        for (Entry e : p.entries()) {
            b.writeUtf(e.id(),256); b.writeDouble(e.value()); b.writeDouble(e.confidence()); b.writeUtf(e.reason(),2048); b.writeBoolean(e.available());
        }
    }
    static Catalog decodeCatalog(FriendlyByteBuf b) {
        UUID token=b.readUUID(); int offset=b.readVarInt(),total=b.readVarInt(); String stake=b.readUtf(256);
        int count=b.readVarInt(); double value=b.readDouble(); String reason=b.readUtf(2048); boolean open=b.readBoolean(); int n=b.readVarInt();
        if (n<0 || n>CHUNK || offset<0 || total<0 || total>100000 || offset+n>total) throw new IllegalArgumentException("catalog size");
        int selectedSlot=b.readVarInt(), slots=b.readVarInt();
        if (slots<0 || slots>37 || offset!=0 && slots!=0) throw new IllegalArgumentException("inventory size");
        List<InventoryEntry> inventory=new ArrayList<>();
        for (int i=0;i<slots;i++) inventory.add(new InventoryEntry(b.readVarInt(),Platform.readItem(b),b.readDouble(),b.readUtf(2048)));
        List<Entry> entries=new ArrayList<>();
        for(int i=0;i<n;i++) entries.add(new Entry(b.readUtf(256),b.readDouble(),b.readDouble(),b.readUtf(2048),b.readBoolean()));
        return new Catalog(token,List.copyOf(entries),offset,total,stake,count,value,reason,open,selectedSlot,List.copyOf(inventory));
    }
    private static void send(ServerPlayer player,Object message) { ServerTransport.send(player,message); }
    public static void catalog(ServerPlayer player, boolean open) {
        int tick=player.server.getTickCount(); UUID id=player.getUUID();
        if (tick-QUERY_TICK.getOrDefault(id,-100)<10) return;
        QUERY_TICK.put(id,tick);
        if (Platform.data(player).contains(PENDING)) {
            player.displayClientMessage(Component.literal("Дождитесь завершения текущего апгрейда."),true); return;
        }
        ItemStack hand=player.getMainHandItem(); var value=Economy.usable(player,hand); UUID token=UUID.randomUUID();
        Map<Integer,ItemStack> snapshot=new HashMap<>();
        List<InventoryEntry> inventory=new ArrayList<>();
        for (int slot=0;slot<=40;slot++) {
            if (slot>=36 && slot<40) continue; // Main inventory, hotbar and offhand; no equipped armour.
            ItemStack stack=player.getInventory().getItem(slot).copy();
            snapshot.put(slot,stack);
            var quote=Economy.usable(player,stack);
            inventory.add(new InventoryEntry(slot,stack,quote==null?0:quote.cost(),Economy.reason(player,stack)));
        }
        SESSIONS.put(id,new Session(token,Economy.current.revision(),Map.copyOf(snapshot)));
        // Search is local, against localized display names. Send bounded chunks once per refresh.
        List<Entry> entries=new ArrayList<>();
        for (ResourceLocation key : new TreeSet<>(BuiltInRegistries.ITEM.keySet())) {
            ItemStack stack=new ItemStack(Objects.requireNonNull(BuiltInRegistries.ITEM.get(key)));
            if (stack.isEmpty()) continue;
            var price=Economy.current.values().get(key.toString());
            entries.add(new Entry(key.toString(),price==null?0:price.cost(),price==null?0:price.confidence(),
                    Economy.reason(player,stack),Economy.usable(player,stack)!=null));
        }
        for (int offset=0; offset<entries.size() || offset==0; offset+=CHUNK) {
            send(player,new Catalog(token,List.copyOf(entries.subList(offset,Math.min(offset+CHUNK,entries.size()))),
                    offset,entries.size(),Economy.id(hand),hand.getCount(),value==null?0:value.cost(),Economy.reason(player,hand),open&&offset==0,player.getInventory().selected,offset==0?List.copyOf(inventory):List.of()));
        }
    }
    private static void reject(ServerPlayer player, UUID token, String reason) { Upgrade.LOGGER.debug("Upgrade rejected: {}",reason); send(player,new Outcome(token,false,false,0,0,reason)); }
    private static void spin(ServerPlayer player, Spin packet) {
        Session session=SESSIONS.get(player.getUUID());
        if(session==null || !session.token().equals(packet.token())) { reject(player,packet.token(),"Сессия устарела. Обновите каталог."); return; }
        if(Platform.data(player).contains(PENDING)) { reject(player,packet.token(),"Дождитесь окончания вращения."); return; }
        SESSIONS.remove(player.getUUID());
        QUERY_TICK.remove(player.getUUID());
        if(!session.revision().equals(Economy.current.revision()) || !player.isAlive() || player.isSpectator() || player.isCreative()) {
            reject(player,packet.token(),"Нужен режим выживания и актуальная оценка."); return;
        }
        ItemStack original=session.inventory().get(packet.slot());
        if (original==null) { reject(player,packet.token(),"Недопустимый слот инвентаря."); return; }
        ItemStack hand=player.getInventory().getItem(packet.slot());
        if(!ItemStack.matches(hand,original)
                || packet.count()<1 || packet.count()>64 || packet.count()>hand.getCount()) {
            reject(player,packet.token(),"Предмет в выбранном слоте изменился. Обновите инвентарь."); return;
        }
        ResourceLocation key=ResourceLocation.tryParse(packet.target());
        if(key==null || !BuiltInRegistries.ITEM.containsKey(key)) { reject(player,packet.token(),"Неизвестная цель."); return; }
        ItemStack reward=new ItemStack(Objects.requireNonNull(BuiltInRegistries.ITEM.get(key)));
        if (packet.rewardCount()<1 || packet.rewardCount()>Math.min(64,reward.getMaxStackSize())) {
            reject(player,packet.token(),"Недопустимое количество награды."); return;
        }
        reward.setCount(packet.rewardCount());
        var source=Economy.usable(player,hand); var target=Economy.usable(player,reward);
        if(source==null || target==null) { reject(player,packet.token(),"Предмет заблокирован или не оценён."); return; }
        double chance;
        try { chance=CostEngine.chance(source.cost()*packet.count(),target.cost()*packet.rewardCount(),Economy.EFFICIENCY); }
        catch(IllegalArgumentException ex) { reject(player,packet.token(),"Цена цели должна превышать ставку."); return; }
        double roll=RNG.nextDouble(); boolean won=roll<chance;
        CompoundTag pending=new CompoundTag();
        pending.putUUID("token",packet.token()); pending.putBoolean("won",won); pending.putString("target",packet.target());
        pending.putLong("due",player.server.overworld().getGameTime()+RollTiming.TICKS);
        pending.put("reward",Platform.saveItem(player,reward));
        // Keep a pending payout on the player across disconnect/restart and player cloning.
        Platform.data(player).put(PENDING,pending);
        hand.shrink(packet.count()); player.inventoryMenu.broadcastChanges();
        Upgrade.LOGGER.info("Upgrade player={} revision={} input={} count={} target={} rewardCount={} chance={} won={}",
                player.getUUID(),session.revision(),Economy.id(original),packet.count(),packet.target(),packet.rewardCount(),chance,won);
        send(player,new Outcome(packet.token(),true,won,chance,roll,""));
    }
    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) settle(player,server.overworld().getGameTime());
    }
    static void settle(ServerPlayer player, long now) {
        if (!player.isAlive() || !Platform.data(player).contains(PENDING)) return;
        CompoundTag pending=Platform.data(player).getCompound(PENDING);
        if (!RollTiming.canSettle(now,pending.getLong("due"),pending.getBoolean("acknowledged"))) return;
        // Remove before delivery: repeated Finish packets cannot deliver twice.
        Platform.data(player).remove(PENDING);
        boolean won=pending.getBoolean("won");
        int rewardCount=Platform.loadItem(player,pending.getCompound("reward")).getCount();
        if (won) {
            ItemStack reward=Platform.loadItem(player,pending.getCompound("reward"));
            if (!player.getInventory().add(reward)) player.drop(reward,false);
        }
        player.inventoryMenu.broadcastChanges();
        send(player,new Settled(pending.getUUID("token"),won,pending.getString("target"),rewardCount));
        player.displayClientMessage(Component.literal(won?"Победа! Предмет получен.":"Поражение. Ставка потрачена."),true);
    }
    public static void copyPending(net.minecraft.world.entity.player.Player from, net.minecraft.world.entity.player.Player to) {
        if (Platform.data(from).contains(PENDING)) Platform.data(to).put(PENDING,Platform.data(from).getCompound(PENDING).copy());
    }
}
