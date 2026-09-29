package dev.upgrade.client.legacy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
/** Narrow rendering adapter, retaining the current wheel layout on the PoseStack API. */
public final class GuiGraphics extends GuiComponent {
    private final PoseStack pose;
    public GuiGraphics(PoseStack pose) { this.pose=pose; }
    public PoseStack pose() { return pose; }
    public void flush() { Minecraft.getInstance().renderBuffers().bufferSource().endBatch(); }
    public void fill(int x,int y,int xx,int yy,int color) { GuiComponent.fill(pose,x,y,xx,yy,color); }
    public void fillGradient(int x,int y,int xx,int yy,int a,int b) { super.fillGradient(pose,x,y,xx,yy,a,b); }
    public void drawString(Font font,String text,int x,int y,int color) { font.drawShadow(pose,text,x,y,color); }
    public void drawCenteredString(Font font,String text,int x,int y,int color) { GuiComponent.drawCenteredString(pose,font,text,x,y,color); }
    public void renderItem(ItemStack item,int x,int y) {
        var model=RenderSystem.getModelViewStack(); model.pushPose(); model.mulPoseMatrix(pose.last().pose()); RenderSystem.applyModelViewMatrix();
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(item,x,y);
        model.popPose(); RenderSystem.applyModelViewMatrix();
    }
    public void renderItemDecorations(Font font,ItemStack item,int x,int y) { Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(font,item,x,y); }
    public void renderComponentTooltip(Font font,List<Component> lines,int x,int y) {
        var screen=Minecraft.getInstance().screen;
        if(screen!=null) screen.renderComponentTooltip(pose,lines,x,y);
    }
}
