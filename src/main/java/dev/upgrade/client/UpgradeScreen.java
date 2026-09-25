package dev.upgrade.client;

import dev.upgrade.Economy;
import dev.upgrade.Network;
import dev.upgrade.core.CostEngine;
import dev.upgrade.core.RollTiming;
import dev.upgrade.core.SearchIndex;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
public class UpgradeScreen extends Screen {
    private static final int GREEN=0xFF9BE7AC, MUTED=0xFF94A5B9, TEXT=0xFFF1F5FA, RED=0xFFFF8292;
    private static final Pattern ITEM_ID=Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private Network.Catalog catalog;
    private final List<Network.Entry> entries=new ArrayList<>();
    private List<Network.Entry> filtered=List.of();
    private final Map<String,ItemStack> stacks=new HashMap<>();
    private Network.Entry target;
    private Network.Outcome outcome;
    private Network.Settled settled;
    private EditBox search;
    private Button spin, previous, next, available, sorting, less, more, maximum, refresh;
    private int amount=1, x, y, panelWidth, panelHeight, columns, rows, page, lastStep=-1;
    private int searchY, gridY;
    private long started, requested, lastCatalogRequest;
    private boolean pending, finishSent, onlyAvailable=true, descending, loading=true;
    private String status="Выберите цель дороже вашей ставки";

    public UpgradeScreen(Network.Catalog catalog) { super(t("Улучшение предметов")); this.catalog=catalog; }
    public static void receive(Network.Catalog packet) {
        Minecraft mc=Minecraft.getInstance();
        if (packet.open()) mc.setScreen(new UpgradeScreen(packet));
        if (!(mc.screen instanceof UpgradeScreen screen) || screen.busy()) return;
        if (packet.offset()==0) {
            screen.lastCatalogRequest=Util.getMillis();
            screen.catalog=packet; screen.entries.clear(); screen.loading=true;
            screen.amount=Math.max(1,Math.min(screen.amount,packet.count()));
        }
        if (!screen.catalog.token().equals(packet.token()) || packet.offset()!=screen.entries.size()) return;
        screen.entries.addAll(packet.entries());
        screen.loading=screen.entries.size()<packet.total();
        if (!screen.loading) {
            if (screen.target!=null) screen.target=screen.entries.stream().filter(e -> e.id().equals(screen.target.id())).findFirst().orElse(null);
            screen.filter();
        }
    }
    public static void receive(Network.Outcome packet) {
        if (!(Minecraft.getInstance().screen instanceof UpgradeScreen screen) || !screen.catalog.token().equals(packet.token())) return;
        screen.pending=false;
        if (packet.accepted()) {
            screen.outcome=packet; screen.started=Util.getMillis(); screen.lastStep=-1;
            screen.status="Прокрутка…";
        } else {
            screen.outcome=null; screen.status=packet.message(); screen.requestCatalog();
        }
    }
    public static void receive(Network.Settled packet) {
        if (!(Minecraft.getInstance().screen instanceof UpgradeScreen screen) || !screen.catalog.token().equals(packet.token())) return;
        screen.settled=packet;
        screen.status=packet.won()?"Предмет добавлен в инвентарь":"Ставка потрачена. Можно попробовать ещё раз";
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                packet.won()?SoundEvents.PLAYER_LEVELUP:SoundEvents.VILLAGER_NO,packet.won()?1.15f:.8f));
        screen.requestCatalog();
    }
    private static net.minecraft.network.chat.MutableComponent t(String text) { return Component.literal(text); }
    private boolean busy() { return pending || outcome!=null && settled==null; }
    private boolean rolling() { return outcome!=null && elapsed()<RollTiming.MILLIS; }
    private long elapsed() { return Util.getMillis()-started; }
    private double chance() {
        if (target==null || !target.available() || catalog.value()<=0) return 0;
        try { return CostEngine.chance(catalog.value()*amount,target.value(),Economy.EFFICIENCY); }
        catch (IllegalArgumentException ex) { return 0; }
    }
    private void requestCatalog() {
        loading=true; lastCatalogRequest=Util.getMillis();
        Network.CHANNEL.sendToServer(new Network.Query(false));
    }
    private ItemStack item(String id) {
        return stacks.computeIfAbsent(id,key -> {
            var location=ResourceLocation.tryParse(key); var item=location==null?null:ForgeRegistries.ITEMS.getValue(location);
            return item==null?ItemStack.EMPTY:new ItemStack(item);
        });
    }
    private String name(String id) { return item(id).getHoverName().getString(); }
    private void filter() {
        String query=search==null?"":search.getValue();
        Comparator<Network.Entry> order=Comparator.comparingDouble(e -> e.value()>0?e.value():Double.MAX_VALUE);
        if (descending) order=order.reversed();
        filtered=entries.stream().filter(e -> !onlyAvailable || e.available() && e.value()>catalog.value()*amount)
                .filter(e -> SearchIndex.matches(query,e.id(),name(e.id())))
                .sorted(order.thenComparing(e -> name(e.id())).thenComparing(Network.Entry::id)).toList();
        page=Math.max(0,Math.min(page,pages()-1));
    }
    private int pageSize() { return Math.max(1,columns*rows); }
    private int pages() { return Math.max(1,(filtered.size()+pageSize()-1)/pageSize()); }
    private void changeAmount(int value) {
        amount=Math.max(1,Math.min(Math.max(1,catalog.count()),value));
        settled=null; outcome=null; page=0; filter();
    }
    @Override protected void init() {
        String query=search==null?"":search.getValue();
        panelWidth=Math.min(480,width-12); panelHeight=Math.min(320,height-12);
        x=(width-panelWidth)/2; y=(height-panelHeight)/2;
        searchY=panelHeight<270?153:171; gridY=searchY+26;
        columns=Math.max(1,(panelWidth-24)/24); rows=Math.max(1,(panelHeight-gridY-28)/24);
        int card=Math.min(132,(panelWidth-106)/2);
        less=addRenderableWidget(Button.builder(t("−"),b -> changeAmount(amount-1)).bounds(x+12,y+90,20,18).build());
        more=addRenderableWidget(Button.builder(t("+"),b -> changeAmount(amount+1)).bounds(x+52,y+90,20,18).build());
        maximum=addRenderableWidget(Button.builder(t("Всё"),b -> changeAmount(catalog.count())).bounds(x+76,y+90,card-64,18).build());
        spin=addRenderableWidget(Button.builder(t("УЛУЧШИТЬ"),b -> {
            if (chance()<=0 || busy() || loading) return;
            pending=true; finishSent=false; outcome=null; settled=null; requested=Util.getMillis();
            status="Ожидание сервера…";
            Network.CHANNEL.sendToServer(new Network.Spin(catalog.token(),target.id(),amount));
        }).bounds(x+panelWidth/2-65,y+116,130,20).build());
        search=new EditBox(font,x+12,y+searchY,panelWidth-174,18,t("Поиск по названию или ID"));
        search.setMaxLength(100); search.setHint(t("Название или minecraft:…")); search.setValue(query);
        search.setResponder(s -> { page=0; filter(); }); addRenderableWidget(search);
        available=addRenderableWidget(Button.builder(t(onlyAvailable?"Доступные":"Все"),b -> {
            onlyAvailable=!onlyAvailable; b.setMessage(t(onlyAvailable?"Доступные":"Все")); page=0; filter();
        }).bounds(x+panelWidth-156,y+searchY,86,18).build());
        available.setTooltip(Tooltip.create(t("Доступные: только оценённые цели дороже ставки. Все: также причины блокировки.")));
        sorting=addRenderableWidget(Button.builder(t(descending?"Цена ↓":"Цена ↑"),b -> {
            descending=!descending; b.setMessage(t(descending?"Цена ↓":"Цена ↑")); page=0; filter();
        }).bounds(x+panelWidth-66,y+searchY,54,18).build());
        previous=addRenderableWidget(Button.builder(t("‹"),b -> page=Math.max(0,page-1)).bounds(x+12,y+panelHeight-24,22,18).build());
        next=addRenderableWidget(Button.builder(t("›"),b -> page=Math.min(pages()-1,page+1)).bounds(x+104,y+panelHeight-24,22,18).build());
        refresh=addRenderableWidget(Button.builder(t("Обновить"),b -> requestCatalog()).bounds(x+panelWidth-96,y+panelHeight-24,84,18).build());
        refresh.setTooltip(Tooltip.create(t("Заново оценить предмет в основной руке и обновить каталог")));
        filter();
    }
    @Override public void tick() {
        search.tick();
        if (outcome!=null && settled==null) {
            if (!rolling() && !finishSent) {
                finishSent=true; status="Прокрутка завершена · получение результата…";
                Network.CHANNEL.sendToServer(new Network.Finish(outcome.token()));
            } else if (rolling()) {
                double progress=RollTiming.progress(elapsed());
                int step=(int)(RollTiming.turns(progress,outcome.roll())*40);
                if (step!=lastStep) {
                    lastStep=step;
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.get(),(float)(1.65-progress*.8),.35f));
                }
            }
        }
        if (pending && Util.getMillis()-requested>10000) status="Сервер ещё отвечает…";
        // Retry a throttled refresh, or a catalog invalidated by datapack reload.
        if (loading && !busy() && Util.getMillis()-lastCatalogRequest>1500) requestCatalog();
        boolean enabled=!busy()&&!loading;
        for (var child : children()) if (child instanceof Button b) b.active=enabled;
        search.setEditable(!busy()); available.active=!busy(); sorting.active=!busy();
        previous.active=enabled&&page>0; next.active=enabled&&page+1<pages();
        less.active=enabled&&amount>1; more.active=enabled&&amount<catalog.count(); maximum.active=enabled&&amount<catalog.count();
        refresh.active=enabled&&Util.getMillis()-lastCatalogRequest>=600;
        spin.active=enabled&&chance()>0;
        spin.setMessage(t(busy()?"ПРОКРУТКА…":"УЛУЧШИТЬ"));
    }
    private void label(GuiGraphics g,String text,int center,int yy,int maxWidth,int color) {
        g.drawCenteredString(font,font.plainSubstrByWidth(text,maxWidth),center,yy,color);
    }
    private List<Component> details(String id,double value,String reason) {
        List<Component> lines=new ArrayList<>();
        lines.add(item(id).getHoverName()); lines.add(t(id).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        lines.add(t(value>0?String.format(Locale.ROOT,"Ценность: %.2f E",value):"Ценность пока не определена"));
        String translated=ITEM_ID.matcher(reason).replaceAll(match -> {
            String key=match.group(); ItemStack stack=item(key);
            return java.util.regex.Matcher.quoteReplacement(stack.isEmpty()?key:stack.getHoverName().getString());
        });
        for (String line : translated.split("\n")) {
            for (var wrapped : font.getSplitter().splitLines(t(line),Math.min(320,width-20),net.minecraft.network.chat.Style.EMPTY))
                lines.add(Component.literal(wrapped.getString()).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return lines.stream().limit(Math.max(5,(height-20)/10)).toList();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g);
        g.fillGradient(x,y,x+panelWidth,y+panelHeight,0xFA131B2A,0xFA0B111C);
        g.fill(x,y,x+panelWidth,y+2,GREEN);
        g.drawString(font,"UPGRADER",x+12,y+11,TEXT);
        g.drawString(font,"УЛУЧШЕНИЕ ПРЕДМЕТОВ",x+100,y+12,MUTED);
        int card=Math.min(132,(panelWidth-106)/2), right=x+panelWidth-12-card, cx=x+panelWidth/2, cy=y+68;
        g.fill(x+12,y+29,x+12+card,y+110,0xFF202C3E); g.fill(right,y+29,right+card,y+110,0xFF202C3E);
        label(g,"ВАША СТАВКА",x+12+card/2,y+35,card-6,MUTED);
        label(g,"ЦЕЛЬ · 1 ПРЕДМЕТ",right+card/2,y+35,card-6,MUTED);
        g.renderItem(item(catalog.stake()),x+12+card/2-8,y+48);
        label(g,name(catalog.stake()),x+12+card/2,y+67,card-8,TEXT);
        label(g,catalog.value()>0?String.format(Locale.ROOT,"%.2f E",catalog.value()*amount):"Нет оценки · наведите",x+12+card/2,y+79,card-8,catalog.value()>0?GREEN:RED);
        g.drawCenteredString(font,Integer.toString(amount),x+42,y+95,TEXT);
        if (target!=null) {
            g.renderItem(item(target.id()),right+card/2-8,y+49);
            label(g,name(target.id()),right+card/2,y+68,card-8,TEXT);
            label(g,target.value()>0?String.format(Locale.ROOT,"%.2f E",target.value()):"Нет оценки",right+card/2,y+82,card-8,GREEN);
            label(g,target.available()?"Выбрано":"Недоступно",right+card/2,y+96,card-8,MUTED);
        } else label(g,"Выберите ниже",right+card/2,y+65,card-8,MUTED);
        double p=outcome!=null?outcome.chance():chance();
        for (int d=0;d<360;d++) {
            double a=Math.toRadians(d-90); int color=d<p*360?GREEN:0xFF344256;
            for (int radius=31;radius<36;radius++) {
                int px=cx+(int)(Math.cos(a)*radius),py=cy+(int)(Math.sin(a)*radius); g.fill(px,py,px+2,py+2,color);
            }
        }
        double turns=outcome==null?0:RollTiming.turns(RollTiming.progress(elapsed()),outcome.roll());
        double angle=-Math.PI/2+turns*Math.PI*2;
        int ax=cx+(int)(Math.cos(angle)*38),ay=cy+(int)(Math.sin(angle)*38); g.fill(ax-2,ay-2,ax+3,ay+3,0xFFFFD58A);
        if (settled!=null && settled.won()) {
            g.pose().pushPose(); g.pose().translate(cx-12,cy-12,0); g.pose().scale(1.5f,1.5f,1); g.renderItem(item(settled.target()),0,0); g.pose().popPose();
        } else {
            g.drawCenteredString(font,String.format(Locale.ROOT,"%.1f%%",p*100),cx,cy-6,TEXT);
            g.drawCenteredString(font,"шанс",cx,cy+7,MUTED);
        }
        if (settled!=null) {
            int color=settled.won()?GREEN:RED;
            g.fill(x+12,y+140,x+panelWidth-12,y+searchY-4,settled.won()?0xFF203C31:0xFF402632);
            g.pose().pushPose();
            g.pose().translate(cx,y+141,0);
            float titleScale=searchY>160?1.25f:1.0f;
            g.pose().scale(titleScale,titleScale,1);
            g.drawCenteredString(font,settled.won()?"✦ ПОБЕДА! ✦":"ПОРАЖЕНИЕ",0,0,color);
            g.pose().popPose();
            if (searchY>160) label(g,status,cx,y+155,panelWidth-28,MUTED);
        } else {
            String hint=loading?"Загрузка каталога…":busy()?status:catalog.value()<=0?"Наведите на ставку, чтобы узнать причину отсутствия цены":status;
            label(g,hint,cx,y+141,panelWidth-24,MUTED);
            if (searchY>160) label(g,"Цена ставки × 0.85 / цена цели",cx,y+158,panelWidth-24,0xFF637A95);
        }
        Network.Entry hovered=null;
        int from=page*pageSize();
        for (int i=0;i<pageSize() && from+i<filtered.size();i++) {
            Network.Entry e=filtered.get(from+i); int sx=x+12+(i%columns)*24,sy=y+gridY+(i/columns)*24;
            boolean selected=target!=null&&target.id().equals(e.id()), over=mx>=sx&&mx<sx+22&&my>=sy&&my<sy+22;
            g.fill(sx,sy,sx+22,sy+22,selected?0xFF47785C:over?0xFF3A4D65:0xFF243145);
            g.renderItem(item(e.id()),sx+3,sy+2);
            g.fill(sx+2,sy+20,sx+20,sy+21,e.available()?GREEN:0xFF854654);
            if (!e.available()) g.fill(sx,sy,sx+22,sy+20,0x66101826);
            if (over) hovered=e;
        }
        if (filtered.isEmpty()&&!loading) label(g,"Ничего не найдено. Попробуйте «Все» или другой запрос.",cx,y+gridY+5,panelWidth-26,MUTED);
        g.drawCenteredString(font,(page+1)+" / "+pages(),x+69,y+panelHeight-19,MUTED);
        label(g,filtered.size()+" целей",cx,y+panelHeight-19,Math.max(20,panelWidth-260),MUTED);
        super.render(g,mx,my,partial);
        if (hovered!=null) {
            var tooltip=new ArrayList<>(details(hovered.id(),hovered.value(),hovered.reason()));
            if (hovered.available()&&catalog.value()>0&&hovered.value()>catalog.value()*amount)
                tooltip.add(1,t(String.format(Locale.ROOT,"Шанс: %.2f%%",CostEngine.chance(catalog.value()*amount,hovered.value(),Economy.EFFICIENCY)*100)));
            g.renderComponentTooltip(font,tooltip,mx,my);
        } else if (mx>=x+12&&mx<x+12+card&&my>=y+29&&my<y+89)
            g.renderComponentTooltip(font,details(catalog.stake(),catalog.value(),catalog.reason()),mx,my);
        else if (target!=null&&mx>=right&&mx<right+card&&my>=y+29&&my<y+110)
            g.renderComponentTooltip(font,details(target.id(),target.value(),target.reason()),mx,my);
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if (!busy()&&!loading&&button==0) {
            int from=page*pageSize();
            for (int i=0;i<pageSize()&&from+i<filtered.size();i++) {
                int sx=x+12+i%columns*24,sy=y+gridY+i/columns*24;
                if (mx>=sx&&mx<sx+22&&my>=sy&&my<sy+22) {
                    target=filtered.get(from+i); outcome=null; settled=null;
                    status=!target.available()?"Цель недоступна · подробности при наведении":chance()>0?"Всё готово. Нажмите «УЛУЧШИТЬ»":"Цена цели должна быть выше всей ставки";
                    return true;
                }
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta) {
        if (!busy()&&!loading&&my>=y+gridY) { page=Math.max(0,Math.min(pages()-1,page+(delta>0?-1:1))); return true; }
        return super.mouseScrolled(mx,my,delta);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (key==GLFW.GLFW_KEY_ENTER&&search.isFocused()) { filter(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean shouldCloseOnEsc() { return !busy(); }
    @Override public void onClose() { if (!busy()) super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
