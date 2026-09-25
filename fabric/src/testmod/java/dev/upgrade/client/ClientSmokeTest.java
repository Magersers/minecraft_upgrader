package dev.upgrade.client;

import dev.upgrade.Network;
import dev.upgrade.Upgrade;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import java.util.List;
import java.util.UUID;

/** Opt-in renderer smoke test. Test classes/resources are never included in the release JAR. */
public final class ClientSmokeTest implements net.fabricmc.api.ClientModInitializer {
    public void onInitializeClient() { net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> { try { tick(); } catch (Exception ex) { throw new RuntimeException(ex); } }); }
    private static int ticks;
    private static UUID token;
    private static boolean opened;
    private static void field(UpgradeScreen screen,String name,Object value) throws ReflectiveOperationException {
        var f=UpgradeScreen.class.getDeclaredField(name); f.setAccessible(true); f.set(screen,value);
    }
    public static void tick() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("upgrade.uiSmoke")) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.screen!=null && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) mc.setScreen(new TitleScreen());
        if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            opened=true; token=UUID.randomUUID();
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);
            mc.options.guiScale().set(2); mc.resizeDisplay();
            var entries=List.of(entry("iron_sword",16.5),entry("diamond_pickaxe",361),entry("diamond",120),
                    entry("chest",8),entry("iron_block",72),entry("golden_apple",162),entry("netherite_chestplate",2120),
                    entry("enchanting_table",250),entry("bookshelf",28),entry("bow",10.5),entry("crossbow",16),entry("compass",36));
            UpgradeScreen.receive(new Network.Catalog(token,entries,0,entries.size(),"minecraft:iron_ingot",32,8,
                    "Базовая цена добычи",true,0,inventory()));
            field((UpgradeScreen)mc.screen,"target",entries.get(2));
            field((UpgradeScreen)mc.screen,"rewardAmount",8);
        }
        if (!opened || !(mc.screen instanceof UpgradeScreen screen)) return;
        ticks++;
        if (ticks==30) Screenshot.grab(mc.gameDirectory,"upgrade-ready.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==33) { var m=UpgradeScreen.class.getDeclaredMethod("switchView",boolean.class); m.setAccessible(true); m.invoke(screen,false); }
        if (ticks==38) Screenshot.grab(mc.gameDirectory,"upgrade-catalog.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==40) {
            field(screen,"outcome",new Network.Outcome(token,true,true,.5,.25,""));
            field(screen,"started",Util.getMillis()-4000);
            field(screen,"settled",new Network.Settled(token,true,"minecraft:diamond",8));
            field(screen,"status","Предмет добавлен в инвентарь");
        }
        if (ticks==55) Screenshot.grab(mc.gameDirectory,"upgrade-win.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==65) {
            field(screen,"settled",new Network.Settled(token,false,"minecraft:diamond",8));
            field(screen,"status","Ставка потрачена. Можно попробовать ещё раз");
        }
        if (ticks==80) Screenshot.grab(mc.gameDirectory,"upgrade-loss.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==90) {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),640,480);
            mc.options.guiScale().set(2); mc.resizeDisplay();
        }
        if (ticks==105) Screenshot.grab(mc.gameDirectory,"upgrade-compact.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==120) { Upgrade.LOGGER.info("CLIENT SMOKE PASS"); mc.stop(); }
    }
    private static java.util.List<Network.InventoryEntry> inventory() {
        var result=new java.util.ArrayList<Network.InventoryEntry>();
        var items=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.IRON_INGOT,net.minecraft.world.item.Items.DIAMOND,net.minecraft.world.item.Items.OAK_LOG,net.minecraft.world.item.Items.COBBLESTONE};
        for (int slot=0;slot<=40;slot++) {
            if (slot>=36 && slot<40) continue;
            var stack=slot<20?new net.minecraft.world.item.ItemStack(items[slot%items.length],slot==0?32:slot+1):net.minecraft.world.item.ItemStack.EMPTY;
            result.add(new Network.InventoryEntry(slot,stack,stack.isEmpty()?0:8,"Цена добычи или крафта"));
        }
        return result;
    }
    private static Network.Entry entry(String id,double value) {
        return new Network.Entry("minecraft:"+id,value,.9,"Рецепт: minecraft:"+id+"\nminecraft:iron_ingot × 2 + minecraft:stick × 1",true);
    }
}
