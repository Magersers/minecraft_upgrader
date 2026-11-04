package dev.upgrade.client;

import dev.upgrade.Network;
import net.minecraft.client.gui.components.Button;
import dev.upgrade.Upgrade;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import java.util.List;
import java.util.UUID;

/** Opt-in renderer smoke test. Test classes/resources are never included in the release JAR. */
public final class ClientSmokeTest {
    private static int ticks;
    private static UUID token;
    private static boolean opened;
    private static boolean connecting;
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
    private static Object readField(UpgradeScreen screen,String name) throws ReflectiveOperationException {
        var f=UpgradeScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(screen);
    }
    private static void verifyNewControls(UpgradeScreen screen) throws Exception {
        var originalTarget=readField(screen,"target"); var originalAmount=readField(screen,"rewardAmount");
        double originalAngle=(double)readField(screen,"markerAngle");
        int x=(int)readField(screen,"x"),y=(int)readField(screen,"y");
        int width=(int)readField(screen,"panelWidth"),height=(int)readField(screen,"panelHeight");
        var buttons=(java.util.List<Button>)readField(screen,"styledButtons");
        for (var button:buttons) {
            if (button.getX()<x||button.getY()<y||button.getX()+button.getWidth()>x+width||button.getY()+button.getHeight()>y+height)
                throw new AssertionError("Button outside panel: "+button.getMessage().getString());
        }
        int rewardLeft=x+width-12-Math.min(150,(width-106)/2);
        int rewardBottom=y+(int)readField(screen,"controlsY")+18;
        for (var preset:(java.util.List<Button>)readField(screen,"multiplierButtons")) {
            if (preset.getX()<rewardLeft-2||preset.getX()+preset.getWidth()>x+width-12||preset.getY()<rewardBottom)
                throw new AssertionError("Multiplier is not below the reward card: "+preset.getMessage().getString());
        }
        var choose=UpgradeScreen.class.getDeclaredMethod("chooseMultiplier",double.class); choose.setAccessible(true);
        var chance=UpgradeScreen.class.getDeclaredMethod("chance"); chance.setAccessible(true);
        var displayChance=UpgradeScreen.class.getDeclaredMethod("displayChance"); displayChance.setAccessible(true);
        for (double multiplier:new double[]{1.5,2,5,10}) {
            choose.invoke(screen,multiplier);
            if (readField(screen,"target")==null||(double)chance.invoke(screen)<=0)
                throw new AssertionError("Multiplier did not select a valid reward: "+multiplier);
            if ((boolean)readField(screen,"pending")||readField(screen,"outcome")!=null)
                throw new AssertionError("A preview started an upgrade");
        }
        double before=(double)chance.invoke(screen);
        // Editing after a settled roll must discard its final rotation offset.
        field(screen,"outcome",new Network.Outcome(token,true,true,.5,.23,""));
        field(screen,"started",Util.getMillis()-5000);
        if ((double)displayChance.invoke(screen)!=.5) throw new AssertionError("Active roll did not retain its accepted odds");
        field(screen,"settled",new Network.Settled(token,true,"minecraft:diamond",1));
        if ((double)displayChance.invoke(screen)!=(double)chance.invoke(screen)) throw new AssertionError("Settled win hid current preview odds");
        boolean compact=(boolean)readField(screen,"compact");
        double cx=x+width/2,cy=y+(compact?62:71),radius=compact?29:38;
        var press=new net.minecraft.client.input.MouseButtonEvent(cx,cy-radius,new net.minecraft.client.input.MouseButtonInfo(1,0));
        var drag=new net.minecraft.client.input.MouseButtonEvent(cx+radius,cy,new net.minecraft.client.input.MouseButtonInfo(1,0));
        if (!screen.mouseClicked(press,false)||!screen.mouseDragged(drag,radius,radius)||!screen.mouseReleased(drag))
            throw new AssertionError("Wheel drag was not handled");
        if (Math.abs((double)readField(screen,"markerAngle")-Math.PI/2)>1e-8)
            throw new AssertionError("Wheel drag did not move the marker to the right");
        if (readField(screen,"outcome")!=null||readField(screen,"settled")!=null)
            throw new AssertionError("Dragging retained the completed roll offset");
        if ((double)chance.invoke(screen)!=before) throw new AssertionError("Moving arrow changed the chance");
        if (Math.abs(WheelPreferences.load(Minecraft.getInstance().gameDirectory.toPath())-Math.PI/2)>1e-8)
            throw new AssertionError("Wheel position was not persisted");
        field(screen,"outcome",new Network.Outcome(token,true,false,.2,.67,""));
        field(screen,"settled",new Network.Settled(token,false,"minecraft:diamond",1));
        if ((double)displayChance.invoke(screen)!=(double)chance.invoke(screen)) throw new AssertionError("Settled loss displayed stale odds");
        var reset=(Button)readField(screen,"markerReset");
        var resetClick=new net.minecraft.client.input.MouseButtonEvent(reset.getX()+9,reset.getY()+9,new net.minecraft.client.input.MouseButtonInfo(1,0));
        if (!screen.mouseClicked(resetClick,false)) throw new AssertionError("Reset button click was not handled");
        screen.mouseReleased(resetClick);
        if ((double)readField(screen,"markerAngle")!=0||readField(screen,"outcome")!=null||readField(screen,"settled")!=null)
            throw new AssertionError("Reset retained the completed roll offset");
        var originalStakeCount=readField(screen,"amount");
        var current=(Network.Catalog)readField(screen,"catalog");
        var allEntries=java.util.List.copyOf((java.util.List<Network.Entry>)readField(screen,"entries"));
        int selected=(int)readField(screen,"selectedSlot");
        var reduced=current.inventory().stream().map(slot -> {
            if (slot.slot()!=selected) return slot;
            var copy=slot.stack().copy(); copy.setCount(1);
            return new Network.InventoryEntry(slot.slot(),copy,slot.value(),slot.reason());
        }).toList();
        field(screen,"selectedMultiplier",2d);
        UpgradeScreen.receive(new Network.Catalog(current.token(),allEntries,0,allEntries.size(),current.stake(),1,
                current.value(),current.reason(),false,selected,reduced));
        if ((double)readField(screen,"selectedMultiplier")!=0||(int)readField(screen,"amount")!=1)
            throw new AssertionError("Catalog refresh left a stale multiplier after stake depletion");
        if ((double)displayChance.invoke(screen)!=(double)chance.invoke(screen)) throw new AssertionError("Depleted stake preview is stale");
        UpgradeScreen.receive(new Network.Catalog(current.token(),allEntries,0,allEntries.size(),current.stake(),current.count(),
                current.value(),current.reason(),false,selected,current.inventory()));
        field(screen,"amount",originalStakeCount);
        field(screen,"markerAngle",originalAngle); WheelPreferences.save(Minecraft.getInstance().gameDirectory.toPath(),originalAngle);
        field(screen,"target",originalTarget); field(screen,"rewardAmount",originalAmount); field(screen,"selectedMultiplier",0d);
    }
    public static void tick() throws Exception {
        if (!Boolean.getBoolean("upgrade.uiSmoke")) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) mc.gui.setScreen(new TitleScreen());
        if (!connecting && mc.gui.screen() instanceof TitleScreen && mc.gui.overlay()==null) {
            connecting=true; String address=System.getProperty("upgrade.testAddress","127.0.0.1:25633");
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.gui.screen(),mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("Upgrade test",address,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null); return;
        }
        if (!opened && mc.player!=null && mc.level!=null && mc.gui.screen()==null && mc.gui.overlay()==null) {
            if (!languageReady) {
                if (reload==null) {
                    mc.getLanguageManager().setSelected(language()); mc.options.languageCode=language();
                    reload=mc.reloadResourcePacks(); return;
                }
                if (!reload.isDone()) return;
                reload.join(); reload=null; languageReady=true; checkLanguage(mc);
            }
            opened=true; token=UUID.randomUUID();
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getWindow().setWindowed(1280,800); mc.options.guiScale().set(2); mc.resizeGui();
            var entries=List.of(entry("iron_sword",240.5),entry("diamond_pickaxe",6001),entry("diamond",2000),
                    entry("chest",8),entry("iron_block",1080),entry("golden_apple",4016),entry("netherite_chestplate",54001),
                    entry("enchanting_table",4120),entry("bookshelf",222),entry("bow",10.5),entry("crossbow",188.25),entry("compass",510));
            UpgradeScreen.receive(new Network.Catalog(token,entries,0,entries.size(),"minecraft:iron_ingot",32,120,
                    "Базовая цена добычи",true,0,inventory()));
            field((UpgradeScreen)mc.gui.screen(),"target",entries.get(2));
            field((UpgradeScreen)mc.gui.screen(),"rewardAmount",8);
            field((UpgradeScreen)mc.gui.screen(),"amount",32);

        }
        if (!opened || !(mc.gui.screen() instanceof UpgradeScreen screen)) return;
        mc.gui.toastManager().clear();
        ticks++;
        if (ticks==15||ticks==20||ticks==25) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-tooltip-"+ticks+".png",mc.gameRenderer.mainRenderTarget(),1,c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==30) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-ready.png",mc.gameRenderer.mainRenderTarget(),1,c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==32) verifyNewControls(screen);
        if (ticks==33) { var m=UpgradeScreen.class.getDeclaredMethod("switchView",boolean.class); m.setAccessible(true); m.invoke(screen,false); field(screen,"markerAngle",Math.PI/2); }
        if (ticks==38) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-catalog.png",mc.gameRenderer.mainRenderTarget(),1,c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==40) {
            field(screen,"outcome",new Network.Outcome(token,true,true,.204,.1,""));
            field(screen,"started",Util.getMillis());
            field(screen,"settled",null);
            field(screen,"status",L10n.text("upgrade.received",net.minecraft.world.item.Items.DIAMOND.getDefaultInstance().getHoverName().getString(),8));
        }
        if (ticks==55) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-win.png",mc.gameRenderer.mainRenderTarget(),1,c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==65) {
            var phase=UpgradeScreen.class.getDeclaredField("lastStep"); phase.setAccessible(true);
            if ((int)phase.get(screen)<10) throw new AssertionError("Wheel animation did not advance");
            field(screen,"started",Util.getMillis()-4000);
            field(screen,"settled",new Network.Settled(token,false,"minecraft:diamond",8));
            field(screen,"status",L10n.text("upgrade.lost"));
        }
        if (ticks==80) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-loss.png",mc.gameRenderer.mainRenderTarget(),1,c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==90) {
            mc.getWindow().setWindowed(640,480); mc.options.guiScale().set(2); mc.resizeGui();
        }
        if (ticks==105) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-compact.png",mc.gameRenderer.mainRenderTarget(),1,c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==108) { field(screen,"settled",null); field(screen,"outcome",null); verifyNewControls(screen); }
        if (ticks==110) {
            field(screen,"settled",null); field(screen,"outcome",null);
            UpgradeScreen.receive(new Network.Outcome(token,false,false,0,0,"upgrade.stale"));
            var status=UpgradeScreen.class.getDeclaredField("status"); status.setAccessible(true);
            if (!L10n.text("upgrade.stale").equals(status.get(screen))) throw new AssertionError("Rejection not localized");
        }
        if (ticks==115) Screenshot.grab(mc.gameDirectory,language()+"-upgrade-rejection.png",mc.gameRenderer.mainRenderTarget(),1,c -> Upgrade.LOGGER.info(c.getString()));
        if (ticks==120) {
            Upgrade.LOGGER.info("LOCALIZED CLIENT PASS {}",language());
            languageIndex++;
            if (languageIndex<LANGUAGES.length) {
                ticks=0; opened=false; languageReady=false; mc.gui.setScreen(null);
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
