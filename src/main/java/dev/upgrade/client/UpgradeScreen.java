package dev.upgrade.client;
import dev.upgrade.Economy;
import dev.upgrade.Network;
import dev.upgrade.core.CostEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
@OnlyIn(Dist.CLIENT)
public class UpgradeScreen extends Screen {
    private Network.Catalog catalog;private Network.Entry target;private Network.Outcome outcome;private EditBox search;private Button spin;
    private int amount=1,x,y;private long start;private boolean pending;private String status="Возьмите ставку в основную руку";
    public UpgradeScreen(Network.Catalog catalog){super(Component.literal("UPGRADE / FORGE"));this.catalog=catalog;}
    public static void receive(Network.Catalog packet){Minecraft mc=Minecraft.getInstance();if(packet.open())mc.setScreen(new UpgradeScreen(packet));else if(mc.screen instanceof UpgradeScreen screen){screen.catalog=packet;screen.amount=Math.max(1,Math.min(screen.amount,packet.count()));}}
    public static void receive(Network.Outcome packet){if(Minecraft.getInstance().screen instanceof UpgradeScreen screen){screen.outcome=packet;screen.pending=false;if(packet.accepted())screen.start=System.currentTimeMillis();else{screen.status=packet.message();screen.refresh();}}}
    private static Component t(String s){return Component.literal(s);}
    private void refresh(){Network.CHANNEL.sendToServer(new Network.Query(search==null?"":search.getValue(),catalog.page()));}
    private boolean rolling(){return outcome!=null&&outcome.accepted()&&System.currentTimeMillis()-start<4000;}
    private double chance(){if(target==null||!target.available()||catalog.value()<=0)return 0;try{return CostEngine.chance(catalog.value()*amount,target.value(),Economy.EFFICIENCY);}catch(IllegalArgumentException ex){return 0;}}
    @Override protected void init(){
        x=(width-406)/2;y=(height-230)/2;search=new EditBox(font,x+244,y+171,130,18,t("Поиск по ID"));search.setMaxLength(64);addRenderableWidget(search);
        addRenderableWidget(Button.builder(t("→"),b->Network.CHANNEL.sendToServer(new Network.Query(search.getValue(),0))).bounds(x+377,y+171,22,18).build());
        addRenderableWidget(Button.builder(t("−"),b->amount=Math.max(1,amount-1)).bounds(x+16,y+105,22,18).build());
        addRenderableWidget(Button.builder(t("+"),b->amount=Math.min(catalog.count(),amount+1)).bounds(x+70,y+105,22,18).build());
        spin=addRenderableWidget(Button.builder(t("УЛУЧШИТЬ"),b->{if(chance()>0&&!pending&&!rolling()){pending=true;outcome=null;status="Ответ сервера…";Network.CHANNEL.sendToServer(new Network.Spin(catalog.token(),target.id(),amount));}}).bounds(x+133,y+131,140,20).build());
        addRenderableWidget(Button.builder(t("‹"),b->Network.CHANNEL.sendToServer(new Network.Query(search.getValue(),catalog.page()-1))).bounds(x+244,y+195,22,18).build());
        addRenderableWidget(Button.builder(t("›"),b->Network.CHANNEL.sendToServer(new Network.Query(search.getValue(),catalog.page()+1))).bounds(x+377,y+195,22,18).build());
    }
    @Override public void tick(){if(outcome!=null&&outcome.accepted()&&!rolling()){status=outcome.message();outcome=null;refresh();}boolean enabled=!pending&&!rolling();for(var child:children())if(child instanceof Button b)b.active=enabled;search.setEditable(enabled);spin.active=enabled&&chance()>0;}
    private ItemStack item(String id){ResourceLocation key=ResourceLocation.tryParse(id);var item=key==null?null:ForgeRegistries.ITEMS.getValue(key);return item==null?ItemStack.EMPTY:new ItemStack(item);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.fill(x,y,x+406,y+230,0xF512171C);g.fill(x,y,x+406,y+2,0xFFB4EB7E);
        g.drawString(font,"UPGRADE / FORGE",x+12,y+12,0xE7EADD);g.drawString(font,"1.20.1 · ALPHA",x+306,y+12,0x819289);
        g.fill(x+10,y+35,x+112,y+126,0xFF202830);g.fill(x+294,y+35,x+396,y+126,0xFF202830);
        g.drawString(font,"ВАША СТАВКА",x+17,y+42,0x94A395);g.drawString(font,"ЦЕЛЬ",x+303,y+42,0x94A395);g.renderItem(item(catalog.stake()),x+47,y+63);
        g.drawCenteredString(font,String.format(Locale.ROOT,"%.2f E",catalog.value()*amount),x+61,y+88,0xE4EDDC);g.drawCenteredString(font,""+amount,x+54,y+110,0xE4EDDC);
        if(target!=null){g.renderItem(item(target.id()),x+337,y+63);g.drawCenteredString(font,String.format(Locale.ROOT,"%.2f E",target.value()),x+345,y+88,0xB4EB7E);g.drawCenteredString(font,"1 предмет",x+345,y+106,0x829287);}
        double p=rolling()?outcome.chance():chance();int cx=x+203,cy=y+80;
        for(int d=0;d<360;d++){double a=Math.toRadians(d-90);int color=d<p*360?0xFFB4EB7E:0xFF36433D;for(int r=39;r<45;r++){int px=cx+(int)(Math.cos(a)*r),py=cy+(int)(Math.sin(a)*r);g.fill(px,py,px+2,py+2,color);}}
        double angle=-Math.PI/2;if(rolling()){double t=Math.min(1,(System.currentTimeMillis()-start)/4000.0);angle+=(4+outcome.roll())*Math.PI*2*(1-Math.pow(1-t,4));}
        int ax=cx+(int)(Math.cos(angle)*47),ay=cy+(int)(Math.sin(angle)*47);g.fill(ax-2,ay-2,ax+3,ay+3,0xFFFFC976);
        g.drawCenteredString(font,String.format(Locale.ROOT,"%.2f%%",p*100),cx,cy-5,0xE4EDDC);g.drawCenteredString(font,"шанс",cx,cy+8,0x829287);
        g.drawString(font,font.plainSubstrByWidth(rolling()?"Результат определён сервером…":status,390),x+10,y+156,0xB4C1AF);Network.Entry hovered=null;
        for(int i=0;i<catalog.entries().size();i++){var entry=catalog.entries().get(i);int sx=x+10+(i%12)*18,sy=y+177+(i/12)*23;g.fill(sx,sy,sx+17,sy+21,target!=null&&target.id().equals(entry.id())?0xFF536B41:0xFF28312E);g.renderItem(item(entry.id()),sx,sy);if(!entry.available())g.fill(sx,sy,sx+17,sy+21,0xAA111619);if(mx>=sx&&mx<sx+17&&my>=sy&&my<sy+21)hovered=entry;}
        g.drawCenteredString(font,(catalog.page()+1)+" / "+Math.max(1,(catalog.total()+23)/24),x+322,y+202,0x819289);g.drawString(font,"E = единицы усилий · эффективность 85%",x+10,y+218,0x819289);
        super.render(g,mx,my,partial);if(hovered!=null)g.renderComponentTooltip(font,List.of(t(hovered.id()),t(String.format(Locale.ROOT,"%.2f E · доверие %.0f%%",hovered.value(),hovered.confidence()*100)),t(hovered.reason())),mx,my);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(!pending&&!rolling()&&button==0)for(int i=0;i<catalog.entries().size();i++){int sx=x+10+i%12*18,sy=y+177+i/12*23;if(mx>=sx&&mx<sx+17&&my>=sy&&my<sy+21){target=catalog.entries().get(i);return true;}}return super.mouseClicked(mx,my,button);}
    @Override public boolean isPauseScreen(){return false;}
}
