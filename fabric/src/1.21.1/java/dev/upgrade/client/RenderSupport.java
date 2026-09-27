package dev.upgrade.client;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
public final class RenderSupport {
    public static BufferBuilder begin() { return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR); }
    public static void vertex(BufferBuilder b,Matrix4f m,float x,float y,int color) { b.addVertex(m,x,y,0).setColor(color); }
    public static void finish(BufferBuilder b) { BufferUploader.drawWithShader(b.buildOrThrow()); }
}
