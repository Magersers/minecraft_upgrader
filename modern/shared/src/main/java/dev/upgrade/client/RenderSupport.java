package dev.upgrade.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
/** Submit triangles as horizontal GUI spans to the extraction renderer. */
public final class RenderSupport {
 public static void triangle(GuiGraphicsExtractor g,double ax,double ay,double bx,double by,double cx,double cy,int color) {
  double[] xs={ax,bx,cx}, ys={ay,by,cy};
  for(int y=(int)Math.floor(Math.min(ay,Math.min(by,cy)));y<(int)Math.ceil(Math.max(ay,Math.max(by,cy)));y++) {
   double scan=y+.5,lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
   for(int i=0;i<3;i++) { int j=(i+1)%3; if((ys[i]<=scan&&ys[j]>scan)||(ys[j]<=scan&&ys[i]>scan)) { double x=xs[i]+(scan-ys[i])*(xs[j]-xs[i])/(ys[j]-ys[i]); lo=Math.min(lo,x);hi=Math.max(hi,x); } }
   if(lo<hi)g.fill((int)Math.ceil(lo-.5),y,(int)Math.ceil(hi-.5),y+1,color);
  }
 }
}
