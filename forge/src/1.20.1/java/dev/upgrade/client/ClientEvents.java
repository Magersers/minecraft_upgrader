package dev.upgrade.client;

import static dev.upgrade.client.L10n.text;

import dev.upgrade.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;


@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="upgrade",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) { new ClientEvents(); }
    public ClientEvents() {
        dev.upgrade.ServerTransport.clientReceiver=ClientEvents::receive;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ScreenEvent.Init.Post event) -> {
            if (!(event.getScreen() instanceof InventoryScreen pos)) return;
            Button button=new Button(pos.getGuiLeft()+pos.getXSize()-23,pos.getGuiTop()+5,20,20,
                    Component.literal(text("upgrade.title")),b -> ClientTransport.send(new Network.Query(true)),supplier -> supplier.get()) {
                @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial) {
                    setX(pos.getGuiLeft()+pos.getXSize()-23); setY(pos.getGuiTop()+5);
                    g.fill(getX(),getY(),getX()+20,getY()+20,isHoveredOrFocused()?0xFF467959:0xFF233C32);
                    int cx=getX()+10,yy=getY()+4;
                    g.fill(cx-2,yy+5,cx+2,yy+13,0xFFABF0BC);
                    for (int i=0;i<6;i++) g.fill(cx-i,yy+i,cx+i+1,yy+i+1,0xFFABF0BC);
                }
            };
            button.setTooltip(Tooltip.create(Component.literal(text("upgrade.open_hint"))));
            event.addListener(button);
        });
    }
    public static void receive(Object packet) {
        if (packet instanceof Network.Catalog p) UpgradeScreen.receive(p);
        else if (packet instanceof Network.Outcome p) UpgradeScreen.receive(p);
        else if (packet instanceof Network.Settled p) UpgradeScreen.receive(p);
    }
    public static void unavailable() {
        var player=Minecraft.getInstance().player;
        if (player!=null) player.displayClientMessage(Component.literal(text("upgrade.server_missing")),true);
    }
}
