package dev.upgrade.client;
import dev.upgrade.client.legacy.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public abstract class UpgradeBaseScreen extends Screen {
    protected UpgradeBaseScreen(Component title) { super(title); }
    protected void drawBackground(GuiGraphics g,int x,int y,float partial) { renderBackground(g.pose()); }
    protected void tickSearch(net.minecraft.client.gui.components.EditBox search) { search.tick(); }
    @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose,int x,int y,float partial) { render(new GuiGraphics(pose),x,y,partial); }
    public void render(GuiGraphics g,int x,int y,float partial) { super.render(g.pose(),x,y,partial); }
    protected abstract boolean scroll(double x,double y,double delta);
    @Override public boolean mouseScrolled(double x,double y,double delta) { return scroll(x,y,delta)||super.mouseScrolled(x,y,delta); }
}
