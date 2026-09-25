package dev.upgrade;
import dev.upgrade.core.CostEngine;
import dev.upgrade.client.UpgradeScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
    private static final String PROTOCOL="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(Upgrade.ID,"main"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    private static final SecureRandom RNG=new SecureRandom();
    private record Session(UUID token,UUID revision,int slot,ItemStack stake) {}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Integer> ROLL_TICK=new HashMap<>(),QUERY_TICK=new HashMap<>();
    public record Query(String search,int page) {}
    public record Spin(UUID token,String target,int count) {}
    public record Entry(String id,double value,double confidence,String reason,boolean available) {}
    public record Catalog(UUID token,List<Entry> entries,int page,int total,String stake,int count,double value,boolean open) {}
    public record Outcome(boolean accepted,boolean won,double chance,double roll,String message) {}
    public static void clear(){SESSIONS.clear();ROLL_TICK.clear();QUERY_TICK.clear();}
    public static void forget(UUID id){SESSIONS.remove(id);ROLL_TICK.remove(id);QUERY_TICK.remove(id);}
    public static void init(){
        CHANNEL.messageBuilder(Query.class,0,NetworkDirection.PLAY_TO_SERVER)
          .encoder((p,b)->{b.writeUtf(p.search,64);b.writeVarInt(p.page);}).decoder(b->new Query(b.readUtf(64),b.readVarInt()))
          .consumerMainThread((p,c)->{var player=c.get().getSender();if(player!=null)catalog(player,p.search,p.page,false);}).add();
        CHANNEL.messageBuilder(Spin.class,1,NetworkDirection.PLAY_TO_SERVER)
          .encoder((p,b)->{b.writeUUID(p.token);b.writeUtf(p.target,256);b.writeVarInt(p.count);}).decoder(b->new Spin(b.readUUID(),b.readUtf(256),b.readVarInt()))
          .consumerMainThread((p,c)->{var player=c.get().getSender();if(player!=null)spin(player,p);}).add();
        CHANNEL.messageBuilder(Catalog.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder(Network::encodeCatalog).decoder(Network::decodeCatalog)
          .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->UpgradeScreen.receive(p))).add();
        CHANNEL.messageBuilder(Outcome.class,3,NetworkDirection.PLAY_TO_CLIENT)
          .encoder((p,b)->{b.writeBoolean(p.accepted);b.writeBoolean(p.won);b.writeDouble(p.chance);b.writeDouble(p.roll);b.writeUtf(p.message,512);})
          .decoder(b->new Outcome(b.readBoolean(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readUtf(512)))
          .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->UpgradeScreen.receive(p))).add();
    }
    private static void encodeCatalog(Catalog p,FriendlyByteBuf b){
        b.writeUUID(p.token);b.writeVarInt(p.page);b.writeVarInt(p.total);b.writeUtf(p.stake,256);b.writeVarInt(p.count);b.writeDouble(p.value);b.writeBoolean(p.open);b.writeVarInt(p.entries.size());
        for(Entry e:p.entries){b.writeUtf(e.id,256);b.writeDouble(e.value);b.writeDouble(e.confidence);b.writeUtf(e.reason,512);b.writeBoolean(e.available);}
    }
    private static Catalog decodeCatalog(FriendlyByteBuf b){
        UUID token=b.readUUID();int page=b.readVarInt(),total=b.readVarInt();String stake=b.readUtf(256);int count=b.readVarInt();double value=b.readDouble();boolean open=b.readBoolean();int n=b.readVarInt();
        if(n<0||n>24)throw new IllegalArgumentException("catalog size");List<Entry> entries=new ArrayList<>();
        for(int i=0;i<n;i++)entries.add(new Entry(b.readUtf(256),b.readDouble(),b.readDouble(),b.readUtf(512),b.readBoolean()));
        return new Catalog(token,List.copyOf(entries),page,total,stake,count,value,open);
    }
    private static void send(ServerPlayer player,Object message){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),message);}
    public static void catalog(ServerPlayer player,String search,int requestedPage,boolean open){
        int tick=player.server.getTickCount();UUID id=player.getUUID();if(tick-QUERY_TICK.getOrDefault(id,-100)<4)return;QUERY_TICK.put(id,tick);
        String query=search.toLowerCase(Locale.ROOT);List<ResourceLocation> keys=ForgeRegistries.ITEMS.getKeys().stream().filter(k->k.toString().contains(query)).sorted().toList();
        int page=Math.max(0,Math.min(requestedPage,Math.max(0,(keys.size()-1)/24)));List<Entry> entries=new ArrayList<>();
        for(ResourceLocation key:keys.subList(Math.min(page*24,keys.size()),Math.min(page*24+24,keys.size()))){
            var item=ForgeRegistries.ITEMS.getValue(key);if(item==null)continue;var value=Economy.current.values().get(key.toString());boolean usable=Economy.usable(player,new ItemStack(item))!=null;
            String reason=value==null?"Нет проверенного источника":value.source();if(value!=null&&!Economy.unlocked(player,value))reason="Нужен этап: "+String.join(", ",value.gates());if(Economy.current.denied().contains(key.toString()))reason="Исключён профилем";
            entries.add(new Entry(key.toString(),value==null?0:value.cost(),value==null?0:value.confidence(),reason.substring(0,Math.min(512,reason.length())),usable));
        }
        ItemStack hand=player.getMainHandItem();var value=Economy.usable(player,hand);UUID token=UUID.randomUUID();
        SESSIONS.put(id,new Session(token,Economy.current.revision(),player.getInventory().selected,hand.copy()));
        send(player,new Catalog(token,List.copyOf(entries),page,keys.size(),Economy.id(hand),hand.getCount(),value==null?0:value.cost(),open));
    }
    private static void reject(ServerPlayer player,String reason){send(player,new Outcome(false,false,0,0,reason));}
    private static void spin(ServerPlayer player,Spin packet){
        Session session=SESSIONS.get(player.getUUID());if(session==null||!session.token.equals(packet.token)){reject(player,"Сессия устарела. Обновите каталог.");return;}
        SESSIONS.remove(player.getUUID());
        if(!session.revision.equals(Economy.current.revision())||!player.isAlive()||player.isSpectator()||player.isCreative()){reject(player,"Нужен режим выживания и актуальная оценка.");return;}
        if(player.server.getTickCount()-ROLL_TICK.getOrDefault(player.getUUID(),-1000)<80){reject(player,"Дождитесь окончания вращения.");return;}
        ItemStack hand=player.getMainHandItem();
        if(player.getInventory().selected!=session.slot||!ItemStack.matches(hand,session.stake)||packet.count<1||packet.count>64||packet.count>hand.getCount()){reject(player,"Предмет в руке изменился.");return;}
        ResourceLocation key=ResourceLocation.tryParse(packet.target);if(key==null||!ForgeRegistries.ITEMS.containsKey(key)){reject(player,"Неизвестная цель.");return;}
        ItemStack reward=new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(key)));var source=Economy.usable(player,hand);var target=Economy.usable(player,reward);
        if(source==null||target==null){reject(player,"Предмет заблокирован или недостаточно данных о цене.");return;}
        double chance;try{chance=CostEngine.chance(source.cost()*packet.count,target.cost(),Economy.EFFICIENCY);}catch(IllegalArgumentException ex){reject(player,"Стоимость цели должна превышать ставку.");return;}
        double roll=RNG.nextDouble();boolean won=roll<chance;hand.shrink(packet.count);if(won&&!player.getInventory().add(reward))player.drop(reward,false);player.inventoryMenu.broadcastChanges();ROLL_TICK.put(player.getUUID(),player.server.getTickCount());
        Upgrade.LOGGER.info("Upgrade player={} revision={} input={} count={} target={} chance={} won={}",player.getUUID(),session.revision,Economy.id(session.stake),packet.count,packet.target,chance,won);
        send(player,new Outcome(true,won,chance,roll,won?"Улучшение удалось! Предмет выдан.":"Неудача. Ставка потрачена."));
    }
}
