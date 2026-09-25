package dev.upgrade;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
@Mod(Upgrade.ID)
public class Upgrade {
    public static final String ID="upgrade";
    public static final Logger LOGGER=LogUtils.getLogger();
    public Upgrade(){Network.init();MinecraftForge.EVENT_BUS.addListener(this::commands);MinecraftForge.EVENT_BUS.addListener(this::started);MinecraftForge.EVENT_BUS.addListener(this::sync);MinecraftForge.EVENT_BUS.addListener(this::stopped);MinecraftForge.EVENT_BUS.addListener(this::logout);MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::clonePlayer);}
    private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("upgrade").executes(ctx->{Network.catalog(ctx.getSource().getPlayerOrException(),true);return Command.SINGLE_SUCCESS;}));}
    private void started(ServerStartedEvent e){Economy.rebuild(e.getServer());}
    private void sync(OnDatapackSyncEvent e){if(e.getPlayer()==null){Economy.rebuild(e.getPlayerList().getServer());Network.clear();}}
    private void stopped(ServerStoppedEvent e){Network.clear();}
    private void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END){var server=ServerLifecycleHooks.getCurrentServer();if(server!=null)Network.tick(server);}}
    private void clonePlayer(PlayerEvent.Clone e){Network.copyPending(e.getOriginal(),e.getEntity());}
    private void logout(PlayerEvent.PlayerLoggedOutEvent e){Network.forget(e.getEntity().getUUID());}
}

