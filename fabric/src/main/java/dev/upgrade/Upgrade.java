package dev.upgrade;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Upgrade implements ModInitializer {
    public static final String ID="upgrade";
    public static final Logger LOGGER=LoggerFactory.getLogger(ID);
    @Override public void onInitialize() {
        ServerTransport.init();
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment) -> dispatcher.register(
                Commands.literal("upgrade").then(Commands.literal("audit").requires(source->source.hasPermission(2)).executes(ctx->{
                    try { var path=EconomyAudit.write(); ctx.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Отчёт цен: "+path),false); return 1; }
                    catch (java.io.IOException ex) { ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Не удалось записать отчёт: "+ex.getMessage())); return 0; }
                })).executes(ctx -> { Network.catalog(ctx.getSource().getPlayerOrException(),true); return Command.SINGLE_SUCCESS; })));
        ServerLifecycleEvents.SERVER_STARTED.register(Economy::rebuild);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server,resources,success) -> {
            if (success) { Economy.rebuild(server); Network.clear(); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Network.clear());
        ServerTickEvents.END_SERVER_TICK.register(Network::tick);
        ServerPlayerEvents.COPY_FROM.register((oldPlayer,newPlayer,alive) -> Network.copyPending(oldPlayer,newPlayer));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> Network.forget(handler.player.getUUID()));
    }
}
