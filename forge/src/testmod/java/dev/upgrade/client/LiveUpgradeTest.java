package dev.upgrade.client;

import dev.upgrade.Network;
import dev.upgrade.Upgrade;
import dev.upgrade.core.RollTiming;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.ScreenEvent;

/** Drives widgets in a real connected client; never fabricates Catalog/Outcome/Settled. */
@EventBusSubscriber(modid="upgrade",value=Dist.CLIENT)
public final class LiveUpgradeTest {
    private static int phase,round,observedSteps,lastStep=-1,frame;
    private static int beforeLogs,beforePlanks;
    private static long deadline=Util.getMillis()+180_000,clickedAt,settledAt;
    private static String screenshot;
    private static boolean finished;
    private static final StringBuilder report=new StringBuilder();

    private static Object get(UpgradeScreen screen,String name) throws Exception {
        var field=UpgradeScreen.class.getDeclaredField(name); field.setAccessible(true); return field.get(screen);
    }
    private static int number(UpgradeScreen screen,String name) throws Exception { return (Integer)get(screen,name); }
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static void click(net.minecraft.client.gui.screens.Screen screen,Button button) {
        check(button.active,"Button must be enabled: "+button.getMessage().getString());
        check(screen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0),"Widget click not handled");
        screen.mouseReleased(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);
    }
    private static void button(UpgradeScreen screen,String name) throws Exception { click(screen,(Button)get(screen,name)); }

    @SubscribeEvent
    public static void tick(ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!Boolean.getBoolean("upgrade.e2eClient") || finished) return;
        try { step(); }
        catch (Throwable ex) {
            finished=true; Upgrade.LOGGER.error("LIVE UPGRADE FAILED",ex);
            try { java.nio.file.Files.writeString(java.nio.file.Path.of("upgrade-e2e-failed.txt"),ex.toString()); } catch(Exception ignored) {}
            Minecraft.getInstance().stop();
        }
    }
    private static void step() throws Exception {
        var mc=Minecraft.getInstance();
        check(Util.getMillis()<deadline,"Timed out in phase "+phase+" on "+(mc.screen==null?"world":mc.screen.getClass().getSimpleName()));
        if (mc.screen!=null && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) mc.setScreen(new TitleScreen());
        if (phase==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);
            mc.options.guiScale().set(2); mc.options.renderDistance().set(2); mc.resizeDisplay();
            var address="127.0.0.1:25577";
            ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),
                    new ServerData("Upgrader E2E",address,false),false);
            phase=1;
        } else if (phase==1 && mc.player!=null && mc.level!=null && mc.player.getInventory().countItem(Items.OAK_LOG)==32-round) {
            mc.setScreen(new InventoryScreen(mc.player)); phase=2;
        } else if (phase==2 && mc.screen instanceof InventoryScreen inventory) {
            var arrow=inventory.children().stream().filter(c->c instanceof Button b && b.getMessage().getString().equals(L10n.text("upgrade.title")))
                    .map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Inventory upgrade button missing"));
            click(inventory,arrow); phase=3;
        } else if (phase>=3 && mc.screen instanceof UpgradeScreen screen) {
            if (phase==3 && !(Boolean)get(screen,"loading")) {
                check(((Network.Catalog)get(screen,"catalog")).inventory().stream().anyMatch(i->i.stack().is(Items.OAK_LOG)),"Real server inventory missing");
                button(screen,"targetsTab");
                ((EditBox)get(screen,"search")).setValue("minecraft:oak_planks"); phase=4;
            } else if (phase==4) {
                var filtered=(java.util.List<Network.Entry>)get(screen,"filtered");
                check(filtered.size()==1 && filtered.get(0).id().equals("minecraft:oak_planks"),"Reward filter mismatch");
                int x=number(screen,"x")+12+number(screen,"cell")/2,y=number(screen,"y")+number(screen,"gridY")+number(screen,"cell")/2;
                check(screen.mouseClicked(x,y,0),"Reward selection click not handled"); screen.mouseReleased(x,y,0); phase=5;
            } else if (phase==5) {
                check(((Network.Entry)get(screen,"target")).id().equals("minecraft:oak_planks"),"Reward not selected");
                for(int i=0;i<7;i++) button(screen,"rewardMore");
                phase=6;
            } else if (phase==6) {
                beforeLogs=mc.player.getInventory().countItem(Items.OAK_LOG);
                beforePlanks=mc.player.getInventory().countItem(Items.OAK_PLANKS);
                check(number(screen,"amount")==1 && number(screen,"rewardAmount")==8,"Wrong stake/reward quantities");
                clickedAt=Util.getMillis(); observedSteps=0; lastStep=-1; frame=0;
                button(screen,"spin"); check((Boolean)get(screen,"pending"),"Spin click did not send request"); phase=7;
            } else if (phase==7) {
                var outcome=(Network.Outcome)get(screen,"outcome");
                check(!(Boolean)get(screen,"rejected"),"Server rejected real spin: "+get(screen,"status"));
                if (outcome==null) return;
                check(outcome.accepted(),"Outcome not accepted");
                long elapsed=Util.getMillis()-(Long)get(screen,"started");
                int step=number(screen,"lastStep");
                if(elapsed<RollTiming.MILLIS && step!=lastStep) { observedSteps++; lastStep=step; }
                if(frame<3 && elapsed>new long[]{700,1700,2800}[frame] && elapsed<RollTiming.MILLIS) {
                    screenshot="live-roll-"+(round+1)+"-frame-"+(++frame)+".png";
                }
                var settled=(Network.Settled)get(screen,"settled");
                if(settled!=null) {
                    check(observedSteps>=5 && frame==3,"Wheel did not advance through the animation");
                    check((Boolean)get(screen,"finishSent"),"Client did not acknowledge animation completion");
                    check(Util.getMillis()-clickedAt>=3900,"Payout arrived before animation duration");
                    check(settled.won()==outcome.won() && settled.count()==8 && settled.target().equals("minecraft:oak_planks"),"Server result mismatch");
                    settledAt=Util.getMillis(); phase=8;
                }
            } else if (phase==8 && Util.getMillis()-settledAt>800) {
                var settled=(Network.Settled)get(screen,"settled");
                check(mc.player.getInventory().countItem(Items.OAK_LOG)==beforeLogs-1,"Stake was not consumed exactly once");
                check(mc.player.getInventory().countItem(Items.OAK_PLANKS)==beforePlanks+(settled.won()?8:0),"Actual inventory payout mismatch");
                report.append("Roll ").append(++round).append(": ").append(settled.won()?"WIN +8 planks":"LOSS +0 planks")
                        .append(", stake -1 log, animation steps=").append(observedSteps).append(", elapsed=")
                        .append(Util.getMillis()-clickedAt).append(" ms\n");
                screenshot="live-roll-"+round+"-result.png";
                phase=round==3?9:10;
            } else if (phase==10 && screenshot==null) {
                mc.setScreen(new InventoryScreen(mc.player)); phase=2;
            } else if (phase==9 && screenshot==null) {
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("upgrade-e2e-passed.txt"),report.toString());
                Upgrade.LOGGER.info("LIVE UPGRADE PASS\n{}",report); finished=true; mc.stop();
            }
        }
    }
    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        if(!Boolean.getBoolean("upgrade.e2eClient") || screenshot==null) return;
        var mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,screenshot,mc.getMainRenderTarget(),c->Upgrade.LOGGER.info(c.getString()));
        screenshot=null;
    }
}
