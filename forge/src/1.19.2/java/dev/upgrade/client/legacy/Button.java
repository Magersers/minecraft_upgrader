package dev.upgrade.client.legacy;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
public class Button extends net.minecraft.client.gui.components.Button {
    private Tooltip tooltip;
    public Button(int x,int y,int width,int height,Component text,OnPress press) { super(x,y,width,height,text,press); }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x=x; }
    public void setY(int y) { this.y=y; }
    public void setTooltip(Tooltip tooltip) { this.tooltip=tooltip; }
    @Override public void renderButton(PoseStack pose,int mx,int my,float partial) {
        super.renderButton(pose,mx,my,partial);
        var mc=Minecraft.getInstance();
        if(isHoveredOrFocused() && tooltip!=null && mc.screen!=null) mc.screen.renderTooltip(pose,tooltip.text(),mx,my);
    }
    public static Builder builder(Component text,OnPress action) { return new Builder(text,action); }
    public static final class Builder {
        private final Component text; private final OnPress action;
        private int x,y,width,height;
        Builder(Component text,OnPress action) { this.text=text; this.action=action; }
        public Builder bounds(int x,int y,int width,int height) { this.x=x; this.y=y; this.width=width; this.height=height; return this; }
        public Button build() { return new Button(x,y,width,height,text,action); }
    }
}
