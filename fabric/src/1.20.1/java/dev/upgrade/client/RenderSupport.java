package dev.upgrade.client;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
public final class RenderSupport {
    public static BufferBuilder begin() { var b=Tesselator.getInstance().getBuilder(); b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR); return b; }
    public static void vertex(BufferBuilder b,Matrix4f m,float x,float y,int color) { b.vertex(m,x,y,0).color(color).endVertex(); }
    public static void finish(BufferBuilder b) { BufferUploader.drawWithShader(b.end()); }
}
