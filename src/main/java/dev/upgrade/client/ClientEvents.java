package dev.upgrade.client;

import dev.upgrade.Network;
import dev.upgrade.Upgrade;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=Upgrade.ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    @SubscribeEvent
    public static void inventory(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            Button button=new Button(Math.max(2,screen.getGuiLeft()-24),screen.getGuiTop()+5,20,20,
                    Component.literal("Улучшение предметов"),b -> Network.CHANNEL.sendToServer(new Network.Query(true)),supplier -> supplier.get()) {
                @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial) {
                    // Follow the inventory when the recipe book changes its horizontal position.
                    setX(Math.max(2,screen.getGuiLeft()-24));
                    g.fill(getX(),getY(),getX()+20,getY()+20,isHoveredOrFocused()?0xFF467959:0xFF233C32);
                    int cx=getX()+10, yy=getY()+4;
                    g.fill(cx-2,yy+5,cx+2,yy+13,0xFFABF0BC);
                    for(int i=0;i<6;i++) g.fill(cx-i,yy+i,cx+i+1,yy+i+1,0xFFABF0BC);
                }
            };
            button.setTooltip(Tooltip.create(Component.literal("Апгрейд\nВозьмите ставку в основную руку и нажмите")));
            event.addListener(button);
        }
    }
}
