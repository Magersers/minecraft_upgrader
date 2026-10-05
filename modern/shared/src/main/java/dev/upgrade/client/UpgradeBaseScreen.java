package dev.upgrade.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public abstract class UpgradeBaseScreen extends Screen {
    protected UpgradeBaseScreen(Component title) { super(title); }
    protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float partial) { super.extractBackground(g,x,y,partial); }
    // Screen.render calls this again before widgets in 1.21; a second blur would obscure our panels.
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float partial) {}
    protected void tickSearch(net.minecraft.client.gui.components.EditBox search) { /* Blink timing uses the render clock in 1.21.1. */ }
    protected abstract boolean scroll(double x,double y,double delta);
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) { return scroll(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical); }
}
