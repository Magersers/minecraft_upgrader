package dev.upgrade.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public abstract class UpgradeBaseScreen extends Screen {
    protected UpgradeBaseScreen(Component title) { super(title); }
    protected void drawBackground(GuiGraphics g,int x,int y,float partial) { renderBackground(g); }
    protected void tickSearch(net.minecraft.client.gui.components.EditBox search) { search.tick(); }
    protected abstract boolean scroll(double x,double y,double delta);
    @Override public boolean mouseScrolled(double x,double y,double delta) { return scroll(x,y,delta)||super.mouseScrolled(x,y,delta); }
}
