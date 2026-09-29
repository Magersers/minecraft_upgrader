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
@net.neoforged.fml.common.EventBusSubscriber(modid="upgrade", value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientSmokeTest {
    @net.neoforged.bus.api.SubscribeEvent
    public static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            try { tick(); } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void onRender(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
            if (!(event.getScreen() instanceof UpgradeScreen view) || ticks<10 || ticks>26) return;
            var item=ticks<17?net.minecraft.world.item.Items.DIAMOND_SWORD:
                    ticks<22?net.minecraft.world.item.Items.DIAMOND_CHESTPLATE:net.minecraft.world.item.Items.PINK_PETALS;
            double value=ticks<17?4000.5:ticks<22?16000:1;
            event.getGuiGraphics().renderComponentTooltip(Minecraft.getInstance().font,
                    ItemDetails.lines(new net.minecraft.world.item.ItemStack(item),value,false,true), view.width/2,view.height/2);
    }
    private static int ticks;
    private static UUID token;
    private static boolean opened;
    private static final String[] LANGUAGES=System.getProperty("upgrade.testLanguages","en_us").split(",");
    private static int languageIndex;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static boolean languageReady;
    private static String language() { return LANGUAGES[languageIndex]; }
    private static void checkLanguage(Minecraft mc) throws Exception {
        var resource=mc.getResourceManager().getResource(dev.upgrade.Ids.of("upgrade","lang/"+language()+".json")).orElseThrow();
        try (var reader=resource.openAsReader()) {
            var values=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry:values.entrySet()) {
                if (!entry.getValue().getAsString().equals(net.minecraft.locale.Language.getInstance().getOrDefault(entry.getKey())))
                    throw new AssertionError("Language resource not loaded: "+language()+" "+entry.getKey());
            }
        }
        String received=L10n.text("upgrade.received","DIAMOND",42);
        if (!received.contains("DIAMOND") || !received.contains("42") || received.contains("%"))
            throw new AssertionError("Localized result placeholders failed: "+language());
        if (L10n.text("upgrade.stale").equals("upgrade.stale")) throw new AssertionError("Untranslated rejection");
        if (!L10n.text("upgrade.missing_test_key").equals("upgrade.missing_test_key"))
            throw new AssertionError("Unknown-key fallback changed");
    }
    private static void field(UpgradeScreen screen,String name,Object value) throws ReflectiveOperationException {
        var f=UpgradeScreen.class.getDeclaredField(name); f.setAccessible(true); f.set(screen,value);
    }
    public static void tick() throws Exception {
        if (!Boolean.getBoolean("upgrade.uiSmoke")) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.screen!=null && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) mc.setScreen(new TitleScreen());
        if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay()==null) {
            if (!languageReady) {
                if (reload==null) {
                    mc.getLanguageManager().setSelected(language()); mc.options.languageCode=language();
                    reload=mc.reloadResourcePacks(); return;
                }
                if (!reload.isDone()) return;
                reload.join(); reload=null; languageReady=true; checkLanguage(mc);
            }
            opened=true; token=UUID.randomUUID();
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);
            mc.options.guiScale().set(2); mc.resizeDisplay();
            var entries=List.of(entry("iron_sword",240.5),entry("diamond_pickaxe",6001),entry("diamond",2000),
                    entry("chest",8),entry("iron_block",1080),entry("golden_apple",4016),entry("netherite_chestplate",54001),
                    entry("enchanting_table",4120),entry("bookshelf",222),entry("bow",10.5),entry("crossbow",188.25),entry("compass",510));
            UpgradeScreen.receive(new Network.Catalog(token,entries,0,entries.size(),"minecraft:iron_ingot",32,120,
                    "Базовая цена добычи",true,0,inventory()));
            field((UpgradeScreen)mc.screen,"target",entries.get(2));
            field((UpgradeScreen)mc.screen,"rewardAmount",8);
            field((UpgradeScreen)mc.screen,"amount",32);

        }
        if (!opened || !(mc.screen instanceof UpgradeScreen screen)) return;
        ticks++;
        if (ticks==15||ticks==20||ticks==25) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-tooltip-"+ticks+".png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==30) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-ready.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==33) { var m=UpgradeScreen.class.getDeclaredMethod("switchView",boolean.class); m.setAccessible(true); m.invoke(screen,false); }
        if (ticks==38) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-catalog.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==40) {
            field(screen,"outcome",new Network.Outcome(token,true,true,.204,.1,""));
            field(screen,"started",Util.getMillis()-4000);
            field(screen,"settled",new Network.Settled(token,true,"minecraft:diamond",8));
            field(screen,"status",L10n.text("upgrade.received",net.minecraft.world.item.Items.DIAMOND.getDescription().getString(),8));
        }
        if (ticks==55) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-win.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==65) {
            field(screen,"settled",new Network.Settled(token,false,"minecraft:diamond",8));
            field(screen,"status",L10n.text("upgrade.lost"));
        }
        if (ticks==80) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-loss.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==90) {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),640,480);
            mc.options.guiScale().set(2); mc.resizeDisplay();
        }
        if (ticks==105) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-compact.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==110) {
            field(screen,"settled",null); field(screen,"outcome",null);
            UpgradeScreen.receive(new Network.Outcome(token,false,false,0,0,"upgrade.stale"));
            var status=UpgradeScreen.class.getDeclaredField("status"); status.setAccessible(true);
            if (!L10n.text("upgrade.stale").equals(status.get(screen))) throw new AssertionError("Rejection not localized");
        }
        if (ticks==115) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-rejection.png",mc.getMainRenderTarget(),c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==120) {
            Upgrade.LOGGER.info("LOCALIZED CLIENT PASS {}",language());
            languageIndex++;
            if (languageIndex<LANGUAGES.length) {
                ticks=0; opened=false; languageReady=false; mc.setScreen(new TitleScreen());
            } else {
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("upgrade-ui-passed.txt"),"PASS "+String.join(",",LANGUAGES)+"\n");
                Upgrade.LOGGER.info("CLIENT SMOKE PASS"); mc.stop();
            }
        }
    }
    private static java.util.List<Network.InventoryEntry> inventory() {
        var result=new java.util.ArrayList<Network.InventoryEntry>();
        var items=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.IRON_INGOT,net.minecraft.world.item.Items.DIAMOND,net.minecraft.world.item.Items.OAK_LOG,net.minecraft.world.item.Items.COBBLESTONE};
        for (int slot=0;slot<=40;slot++) {
            if (slot>=36 && slot<40) continue;
            var stack=slot<20?new net.minecraft.world.item.ItemStack(items[slot%items.length],slot==0?32:slot+1):net.minecraft.world.item.ItemStack.EMPTY;
            result.add(new Network.InventoryEntry(slot,stack,stack.isEmpty()?0:new double[]{120,2000,4,1}[slot%items.length],"Цена добычи или крафта"));
        }
        return result;
    }
    private static Network.Entry entry(String id,double value) {
        return new Network.Entry("minecraft:"+id,value,.9,"Рецепт: minecraft:"+id+"\nminecraft:iron_ingot × 2 + minecraft:stick × 1",true);
    }
}
