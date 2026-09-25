package dev.upgrade.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.upgrade.Platform;
import dev.upgrade.Ids;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.regex.Pattern;

public class UpgradeScreen extends UpgradeBaseScreen {
    private static final int GREEN=0xFF90EDC1, MUTED=0xFF99ABC5, TEXT=0xFFF0F5FF, RED=0xFFFF8199, GOLD=0xFFFFD68A;
    private static final Pattern ITEM_ID=Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private Network.Catalog catalog;
    private final List<Network.Entry> entries=new ArrayList<>();
    private List<Network.Entry> filtered=List.of();
    private final Map<String,ItemStack> stacks=new HashMap<>();
    private Network.Entry target;
    private Network.Outcome outcome;
    private Network.Settled settled;
    private EditBox search;
    private Button spin,previous,next,available,sorting,less,more,maximum,refresh,rewardLess,rewardMore,rewardMax,inventoryTab,targetsTab;
    private int amount=1,rewardAmount=1,selectedSlot=-1,x,y,panelWidth,panelHeight,columns,rows,page,lastStep=-1;
    private int searchY,gridY,cell,controlsY,spinY,statusY,tabsY;
    private long started,requested,lastCatalogRequest;
    private boolean pending,finishSent,onlyAvailable=true,descending,loading=true,inventoryView=true,compact;
    private String status="Выберите ставку в своём инвентаре";

    public UpgradeScreen(Network.Catalog catalog) { super(t("Улучшение предметов")); this.catalog=catalog; selectedSlot=catalog.selectedSlot(); }
    public static void receive(Network.Catalog packet) {
        Minecraft mc=Minecraft.getInstance();
        if (packet.open()) mc.setScreen(new UpgradeScreen(packet));
        if (!(mc.screen instanceof UpgradeScreen screen) || screen.busy()) return;
        if (packet.offset()==0) {
            screen.lastCatalogRequest=Util.getMillis(); screen.catalog=packet; screen.entries.clear(); screen.loading=true;
            screen.amount=Math.max(1,Math.min(screen.amount,Math.min(64,screen.stake().stack().getCount())));
        }
        if (!screen.catalog.token().equals(packet.token()) || packet.offset()!=screen.entries.size()) return;
        screen.entries.addAll(packet.entries()); screen.loading=screen.entries.size()<packet.total();
        if (!screen.loading) {
            if (screen.target!=null) screen.target=screen.entries.stream().filter(e -> e.id().equals(screen.target.id())).findFirst().orElse(null);
            screen.filter();
        }
    }
    public static void receive(Network.Outcome packet) {
        if (!(Minecraft.getInstance().screen instanceof UpgradeScreen screen) || !screen.catalog.token().equals(packet.token())) return;
        screen.pending=false;
        if (packet.accepted()) {
            screen.outcome=packet; screen.started=Util.getMillis(); screen.lastStep=-1; screen.status="Прокрутка…";
        } else { screen.outcome=null; screen.status=packet.message(); screen.requestCatalog(); }
    }
    public static void receive(Network.Settled packet) {
        if (!(Minecraft.getInstance().screen instanceof UpgradeScreen screen) || !screen.catalog.token().equals(packet.token())) return;
        screen.settled=packet;
        screen.status=packet.won()?"Получено: "+screen.name(packet.target())+" × "+packet.count():"Ставка потрачена. Можно попробовать ещё раз";
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(packet.won()?SoundEvents.PLAYER_LEVELUP:SoundEvents.VILLAGER_NO,packet.won()?1.15f:.8f));
        screen.requestCatalog();
    }
    private static net.minecraft.network.chat.MutableComponent t(String text) { return Component.literal(text); }
    private Network.InventoryEntry stake() {
        return catalog.inventory().stream().filter(e -> e.slot()==selectedSlot).findFirst()
                .orElse(new Network.InventoryEntry(-1,ItemStack.EMPTY,0,"Выберите предмет в инвентаре"));
    }
    private boolean busy() { return pending || outcome!=null && settled==null; }
    private boolean rolling() { return outcome!=null && elapsed()<RollTiming.MILLIS; }
    private long elapsed() { return Util.getMillis()-started; }
    private int rewardLimit() { return target==null?1:Math.min(64,item(target.id()).getMaxStackSize()); }
    private double chance() {
        if (target==null || !target.available() || stake().value()<=0) return 0;
        try { return CostEngine.chance(stake().value()*amount,target.value()*rewardAmount,Economy.EFFICIENCY); }
        catch (IllegalArgumentException ex) { return 0; }
    }
    private void requestCatalog() { loading=true; lastCatalogRequest=Util.getMillis(); ClientTransport.send(new Network.Query(false)); }
    private ItemStack item(String id) {
        return stacks.computeIfAbsent(id,key -> {
            var location=ResourceLocation.tryParse(key); var item=location==null?null:BuiltInRegistries.ITEM.get(location);
            return item==null?ItemStack.EMPTY:new ItemStack(item);
        });
    }
    private String name(String id) { return item(id).getHoverName().getString(); }
    private void filter() {
        String query=search==null?"":search.getValue();
        Comparator<Network.Entry> order=Comparator.comparingDouble(e -> e.value()>0?e.value():Double.MAX_VALUE);
        if (descending) order=order.reversed();
        // A cheap unit can still be a valid upgrade as a stack; never hide it by unit price.
        filtered=entries.stream().filter(e -> !onlyAvailable || e.available())
                .filter(e -> SearchIndex.matches(query,e.id(),name(e.id())))
                .sorted(order.thenComparing(e -> name(e.id())).thenComparing(Network.Entry::id)).toList();
        page=Math.max(0,Math.min(page,pages()-1));
    }
    private List<Network.InventoryEntry> inventory() {
        // Preserve familiar 9-column main inventory followed by the hotbar and offhand.
        List<Network.InventoryEntry> result=new ArrayList<>(catalog.inventory());
        result.sort(Comparator.comparingInt(e -> e.slot()<9?e.slot()+27:e.slot()==40?36:e.slot()-9));
        return result;
    }
    private int gridColumns() { return inventoryView?Math.min(9,columns):columns; }
    private int gridX() { return inventoryView?x+(panelWidth-gridColumns()*cell)/2:x+12; }
    private int pageSize() { return Math.max(1,gridColumns()*rows); }
    private int pages() { int size=inventoryView?catalog.inventory().size():filtered.size(); return Math.max(1,(size+pageSize()-1)/pageSize()); }
    private void resetResult() { outcome=null; settled=null; }
    private void changeAmount(int value) { amount=Math.max(1,Math.min(Math.max(1,Math.min(64,stake().stack().getCount())),value)); resetResult(); filter(); }
    private void changeReward(int value) { rewardAmount=Math.max(1,Math.min(rewardLimit(),value)); resetResult(); }
    private void switchView(boolean inventory) { inventoryView=inventory; page=0; search.setVisible(!inventory); available.visible=!inventory; sorting.visible=!inventory; }
    private Button button(String text,int xx,int yy,int w,java.util.function.Consumer<Button> action) {
        return addRenderableWidget(Button.builder(t(text),action::accept).bounds(xx,yy,w,18).build());
    }
    @Override protected void init() {
        String query=search==null?"":search.getValue();
        panelWidth=Math.min(540,width-12); panelHeight=Math.min(380,height-12); compact=panelHeight<290;
        x=(width-panelWidth)/2; y=(height-panelHeight)/2;
        controlsY=compact?83:98; spinY=compact?105:122; statusY=compact?128:149;
        tabsY=compact?141:185; searchY=tabsY+22; gridY=searchY+24; cell=compact?18:24;
        columns=Math.max(1,(panelWidth-24)/cell); rows=Math.max(1,(panelHeight-gridY-24)/cell);
        int card=Math.min(150,(panelWidth-106)/2),right=x+panelWidth-12-card;
        less=button("−",x+16,y+controlsY,18,b -> changeAmount(amount-1));
        more=button("+",x+58,y+controlsY,18,b -> changeAmount(amount+1));
        maximum=button("Всё",x+80,y+controlsY,Math.max(24,card-72),b -> changeAmount(stake().stack().getCount()));
        rewardLess=button("−",right+4,y+controlsY,18,b -> changeReward(rewardAmount-1));
        rewardMore=button("+",right+46,y+controlsY,18,b -> changeReward(rewardAmount+1));
        rewardMax=button("Макс",right+68,y+controlsY,Math.max(24,card-72),b -> changeReward(rewardLimit()));
        rewardMax.setTooltip(Tooltip.create(t("Максимальный размер стака цели. Шанс учитывает всю пачку.")));
        spin=button("УЛУЧШИТЬ",x+panelWidth/2-68,y+spinY,136,b -> {
            if (chance()<=0 || busy() || loading) return;
            pending=true; finishSent=false; resetResult(); requested=Util.getMillis(); status="Ожидание сервера…";
            ClientTransport.send(new Network.Spin(catalog.token(),target.id(),amount,selectedSlot,rewardAmount));
        });
        inventoryTab=button("МОЙ ИНВЕНТАРЬ",x+12,y+tabsY,(panelWidth-28)/2,b -> switchView(true));
        targetsTab=button("КАТАЛОГ НАГРАД",x+16+(panelWidth-28)/2,y+tabsY,(panelWidth-28)/2,b -> switchView(false));
        search=new EditBox(font,x+12,y+searchY,panelWidth-174,18,t("Поиск по названию или ID"));
        search.setMaxLength(100); search.setHint(t("Название или minecraft:…")); search.setValue(query);
        search.setResponder(s -> { page=0; filter(); }); addRenderableWidget(search);
        available=button(onlyAvailable?"Доступные":"Все",x+panelWidth-156,y+searchY,86,b -> {
            onlyAvailable=!onlyAvailable; b.setMessage(t(onlyAvailable?"Доступные":"Все")); page=0; filter();
        });
        available.setTooltip(Tooltip.create(t("Доступные: оценённые и открытые цели. Количество задаётся над каталогом.")));
        sorting=button(descending?"Цена ↓":"Цена ↑",x+panelWidth-66,y+searchY,54,b -> {
            descending=!descending; b.setMessage(t(descending?"Цена ↓":"Цена ↑")); page=0; filter();
        });
        previous=button("‹",x+12,y+panelHeight-22,22,b -> page=Math.max(0,page-1));
        next=button("›",x+104,y+panelHeight-22,22,b -> page=Math.min(pages()-1,page+1));
        refresh=button("Обновить",x+panelWidth-96,y+panelHeight-22,84,b -> requestCatalog());
        refresh.setTooltip(Tooltip.create(t("Обновить инвентарь и оценки предметов")));
        switchView(inventoryView); filter();
    }
    @Override public void tick() {
        tickSearch(search);
        if (outcome!=null && settled==null) {
            if (!rolling() && !finishSent) {
                finishSent=true; status="Получение результата…"; ClientTransport.send(new Network.Finish(outcome.token()));
            } else if (rolling()) {
                double progress=RollTiming.progress(elapsed()); int step=(int)(RollTiming.turns(progress,outcome.roll())*40);
                if (step!=lastStep) {
                    lastStep=step;
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(),(float)(1.65-progress*.8),.35f));
                }
            }
        }
        if (pending && Util.getMillis()-requested>10000) status="Сервер ещё отвечает…";
        if (loading && !busy() && Util.getMillis()-lastCatalogRequest>1500) requestCatalog();
        boolean enabled=!busy()&&!loading;
        for (var child : children()) if (child instanceof Button b) b.active=enabled;
        search.setEditable(!busy());
        previous.active=enabled&&page>0; next.active=enabled&&page+1<pages();
        less.active=enabled&&amount>1; more.active=maximum.active=enabled&&amount<Math.min(64,stake().stack().getCount());
        rewardLess.active=enabled&&rewardAmount>1; rewardMore.active=rewardMax.active=enabled&&target!=null&&rewardAmount<rewardLimit();
        refresh.active=enabled&&Util.getMillis()-lastCatalogRequest>=600;
        inventoryTab.active=enabled&&!inventoryView; targetsTab.active=enabled&&inventoryView;
        spin.active=enabled&&chance()>0; spin.setMessage(t(busy()?"ПРОКРУТКА…":"УЛУЧШИТЬ"));
    }
    private void label(GuiGraphics g,String text,int center,int yy,int maxWidth,int color) { g.drawCenteredString(font,font.plainSubstrByWidth(text,maxWidth),center,yy,color); }
    private List<Component> details(ItemStack stack,double value,String reason) {
        List<Component> lines=new ArrayList<>(); lines.add(stack.isEmpty()?t("Пустой слот"):stack.getHoverName());
        if (!stack.isEmpty()) lines.add(t(Economy.id(stack)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        lines.add(t(value>0?String.format(Locale.ROOT,"Ценность: %.2f E за 1 шт.",value):"Предмет недоступен для ставки"));
        String translated=ITEM_ID.matcher(reason).replaceAll(match -> {
            String key=match.group(); ItemStack found=item(key);
            return java.util.regex.Matcher.quoteReplacement(found.isEmpty()?key:found.getHoverName().getString());
        });
        for (String line : translated.split("\n")) for (var wrapped : font.getSplitter().splitLines(t(line),Math.min(340,width-20),net.minecraft.network.chat.Style.EMPTY))
            lines.add(t(wrapped.getString()).withStyle(net.minecraft.ChatFormatting.GRAY));
        return lines.stream().limit(Math.max(5,(height-20)/10)).toList();
    }
    private static void vertex(BufferBuilder b,Matrix4f matrix,double xx,double yy,int color) { RenderSupport.vertex(b,matrix,(float)xx,(float)yy,color); }
    private static void triangle(BufferBuilder b,Matrix4f m,double ax,double ay,double bx,double by,double cx,double cy,int color) {
        vertex(b,m,ax,ay,color); vertex(b,m,bx,by,color); vertex(b,m,cx,cy,color);
    }
    private static void ring(BufferBuilder b,Matrix4f m,int cx,int cy,double inner,double outer,double chance) {
        for (int d=0;d<360;d+=2) {
            double a=Math.toRadians(d-90),z=Math.toRadians(d+2-90); int color=d<chance*360?GREEN:0xFF344861;
            triangle(b,m,cx+Math.cos(a)*inner,cy+Math.sin(a)*inner,cx+Math.cos(a)*outer,cy+Math.sin(a)*outer,cx+Math.cos(z)*outer,cy+Math.sin(z)*outer,color);
            triangle(b,m,cx+Math.cos(a)*inner,cy+Math.sin(a)*inner,cx+Math.cos(z)*outer,cy+Math.sin(z)*outer,cx+Math.cos(z)*inner,cy+Math.sin(z)*inner,color);
        }
    }
    private void wheel(GuiGraphics g,int cx,int cy,double chance) {
        double radius=compact?29:38;
        g.flush(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b=RenderSupport.begin();
        Matrix4f m=g.pose().last().pose();
        ring(b,m,cx,cy,radius+2,radius+3,0); ring(b,m,cx,cy,radius-5,radius,chance); ring(b,m,cx,cy,radius-8,radius-7,0);
        for (int d=0;d<360;d+=15) {
            double a=Math.toRadians(d-90),r=radius-11;
            triangle(b,m,cx+Math.cos(a)*r,cy+Math.sin(a)*r,cx+Math.cos(a-.025)*(r-2),cy+Math.sin(a-.025)*(r-2),cx+Math.cos(a+.025)*(r-2),cy+Math.sin(a+.025)*(r-2),0xFF536A88);
        }
        double turns=outcome==null?0:RollTiming.turns(RollTiming.progress(elapsed()),outcome.roll());
        double a=-Math.PI/2+turns*Math.PI*2,rx=Math.cos(a),ry=Math.sin(a),base=radius+12;
        triangle(b,m,cx+rx*(radius-2)+1,cy+ry*(radius-2)+1,cx+rx*base-ry*5+1,cy+ry*base+rx*5+1,cx+rx*base+ry*5+1,cy+ry*base-rx*5+1,0xFF080E18);
        triangle(b,m,cx+rx*(radius-2),cy+ry*(radius-2),cx+rx*base-ry*5,cy+ry*base+rx*5,cx+rx*base+ry*5,cy+ry*base-rx*5,GOLD);
        RenderSupport.finish(b); RenderSystem.enableCull(); RenderSystem.disableBlend();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        drawBackground(g,mx,my,partial);
        g.fillGradient(x,y,x+panelWidth,y+panelHeight,0xFD172238,0xFD0B111E); g.fill(x,y,x+panelWidth,y+2,GREEN);
        g.drawString(font,"UPGRADER",x+12,y+10,TEXT); g.drawString(font,"УЛУЧШЕНИЕ ПРЕДМЕТОВ",x+100,y+11,MUTED);
        int card=Math.min(150,(panelWidth-106)/2),right=x+panelWidth-12-card,cx=x+panelWidth/2,cy=y+(compact?62:71),top=compact?26:31;
        g.fillGradient(x+12,y+top,x+12+card,y+controlsY+20,0xFF24374F,0xFF18283C);
        g.fillGradient(right,y+top,right+card,y+controlsY+20,0xFF303B53,0xFF1D2B41);
        label(g,"СТАВКА",x+12+card/2,y+top+4,card-6,MUTED); label(g,"НАГРАДА",right+card/2,y+top+4,card-6,GOLD);
        var stake=stake(); ItemStack stakeItem=stake.stack();
        g.renderItem(stakeItem,x+12+card/2-8,y+top+17);
        label(g,stakeItem.isEmpty()?"Выберите предмет":stakeItem.getHoverName().getString(),x+12+card/2,y+top+36,card-8,TEXT);
        label(g,stake.value()>0?String.format(Locale.ROOT,"%.2f E",stake.value()*amount):"Нет оценки",x+12+card/2,y+top+47,card-8,stake.value()>0?GREEN:RED);
        g.drawCenteredString(font,Integer.toString(amount),x+46,y+controlsY+5,TEXT);
        g.drawCenteredString(font,Integer.toString(rewardAmount),right+34,y+controlsY+5,TEXT);
        if (target!=null) {
            g.renderItem(item(target.id()),right+card/2-8,y+top+17);
            label(g,name(target.id()),right+card/2,y+top+36,card-8,TEXT);
            label(g,target.value()>0?String.format(Locale.ROOT,"%.2f E",target.value()*rewardAmount):"Нет оценки",right+card/2,y+top+47,card-8,target.available()?GOLD:RED);
        } else label(g,"Выберите в каталоге",right+card/2,y+top+35,card-8,MUTED);
        double p=outcome!=null?outcome.chance():chance(); wheel(g,cx,cy,p);
        if (settled!=null && settled.won()) {
            g.pose().pushPose(); g.pose().translate(cx-12,cy-13,0); g.pose().scale(1.5f,1.5f,1); g.renderItem(item(settled.target()),0,0); g.pose().popPose();
            g.drawCenteredString(font,"× "+settled.count(),cx,cy+14,GOLD);
        } else { g.drawCenteredString(font,String.format(Locale.ROOT,"%.1f%%",p*100),cx,cy-6,TEXT); g.drawCenteredString(font,"шанс",cx,cy+7,MUTED); }
        if (settled!=null) {
            g.fillGradient(x+12,y+statusY-3,x+panelWidth-12,y+tabsY-4,settled.won()?0xFF214C40:0xFF512D40,0xAA172238);
            label(g,settled.won()?"✦ ПОБЕДА! ✦":"ПОРАЖЕНИЕ",cx,y+statusY,panelWidth-28,settled.won()?GREEN:RED);
            if (!compact) label(g,status,cx,y+statusY+16,panelWidth-28,MUTED);
        } else {
            String hint=loading?"Загрузка…":busy()?status:stake.value()<=0?"Выберите оценённый предмет в инвентаре":target==null?"Выберите награду в каталоге":chance()>0?"Всё готово · шанс учитывает количество обоих предметов":"Награда должна стоить дороже ставки — увеличьте количество";
            label(g,hint,cx,y+statusY,panelWidth-24,MUTED);
            if (!compact) label(g,"СТАВКА × 0.85 / ПОЛНАЯ ЦЕНА НАГРАДЫ",cx,y+statusY+17,panelWidth-24,0xFF627D9F);
        }
        if (inventoryView) label(g,"Нажмите на предмет · последние 9 слотов — хотбар · + вторая рука",cx,y+searchY+5,panelWidth-28,MUTED);
        Network.Entry hovered=null; Network.InventoryEntry hoveredSlot=null;
        int from=page*pageSize(),size=inventoryView?inventory().size():filtered.size(),cols=gridColumns();
        List<Network.InventoryEntry> inventory=inventory();
        for (int i=0;i<pageSize() && from+i<size;i++) {
            Network.Entry entry=inventoryView?null:filtered.get(from+i); Network.InventoryEntry slot=inventoryView?inventory.get(from+i):null;
            int sx=gridX()+(i%cols)*cell,sy=y+gridY+(i/cols)*cell;
            boolean selected=inventoryView?slot.slot()==selectedSlot:target!=null&&target.id().equals(entry.id()),over=mx>=sx&&mx<sx+cell-1&&my>=sy&&my<sy+cell-1;
            boolean usable=inventoryView?slot.value()>0:entry.available(); ItemStack stack=inventoryView?slot.stack():item(entry.id());
            g.fill(sx,sy,sx+cell-1,sy+cell-1,selected?0xFF467B70:over?0xFF3C5472:0xFF24344C);
            int pad=(cell-16)/2;
            g.renderItem(stack,sx+pad,sy+pad); if (inventoryView) g.renderItemDecorations(font,stack,sx+pad,sy+pad);
            if (!compact && !stack.isEmpty()) g.fill(sx+2,sy+cell-3,sx+cell-3,sy+cell-2,usable?GREEN:0xFF8C5065);
            if (inventoryView && !compact && slot.slot()==40) g.drawString(font,"Вторая рука",sx+cell+6,sy+8,MUTED);
            if (inventoryView && !compact && slot.slot()==0 && sx-x>65) g.drawString(font,"Хотбар",sx-46,sy+8,MUTED);
            if (!stack.isEmpty()&&!usable) g.fill(sx,sy,sx+cell-1,sy+cell-1,0x55101826);
            if (over) { hovered=entry; hoveredSlot=slot; }
        }
        if (!inventoryView&&filtered.isEmpty()&&!loading) label(g,"Ничего не найдено",cx,y+gridY+5,panelWidth-26,MUTED);
        g.drawCenteredString(font,(page+1)+" / "+pages(),x+69,y+panelHeight-17,MUTED);
        if (panelWidth>350) label(g,inventoryView?"37 слотов":filtered.size()+" целей",cx,y+panelHeight-17,panelWidth-260,MUTED);
        super.render(g,mx,my,partial);
        if (hoveredSlot!=null) g.renderComponentTooltip(font,details(hoveredSlot.stack(),hoveredSlot.value(),hoveredSlot.reason()),mx,my);
        else if (hovered!=null) g.renderComponentTooltip(font,details(item(hovered.id()),hovered.value(),hovered.reason()),mx,my);
        else if (mx>=x+12&&mx<x+12+card&&my>=y+top&&my<y+controlsY) g.renderComponentTooltip(font,details(stakeItem,stake.value(),stake.reason()),mx,my);
        else if (target!=null&&mx>=right&&mx<right+card&&my>=y+top&&my<y+controlsY) g.renderComponentTooltip(font,details(item(target.id()),target.value(),target.reason()),mx,my);
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if (!busy()&&!loading&&button==0) {
            int from=page*pageSize(),size=inventoryView?inventory().size():filtered.size(),cols=gridColumns();
            for (int i=0;i<pageSize()&&from+i<size;i++) {
                int sx=gridX()+i%cols*cell,sy=y+gridY+i/cols*cell;
                if (mx>=sx&&mx<sx+cell-1&&my>=sy&&my<sy+cell-1) {
                    resetResult();
                    if (inventoryView) { var slot=inventory().get(from+i); if (!slot.stack().isEmpty()) { selectedSlot=slot.slot(); changeAmount(1); } }
                    else { target=filtered.get(from+i); rewardAmount=Math.min(rewardAmount,rewardLimit()); }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override protected boolean scroll(double mx,double my,double delta) {
        if (!busy()&&!loading&&my>=y+gridY) { page=Math.max(0,Math.min(pages()-1,page+(delta>0?-1:1))); return true; }
        return false;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) { if (key==GLFW.GLFW_KEY_ENTER&&search.isFocused()) { filter(); return true; } return super.keyPressed(key,scan,modifiers); }
    @Override public boolean shouldCloseOnEsc() { return !busy(); }
    @Override public void onClose() { if (!busy()) super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
