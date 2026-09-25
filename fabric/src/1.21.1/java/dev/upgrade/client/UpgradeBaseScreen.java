package dev.upgrade.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public abstract class UpgradeBaseScreen extends Screen {
    protected UpgradeBaseScreen(Component title) { super(title); }
    protected void drawBackground(GuiGraphics g,int x,int y,float partial) { renderBackground(g,x,y,partial); }
    protected void tickSearch(net.minecraft.client.gui.components.EditBox search) { /* Blink timing uses the render clock in 1.21.1. */ }
    protected abstract boolean scroll(double x,double y,double delta);
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) { return scroll(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical); }
}
