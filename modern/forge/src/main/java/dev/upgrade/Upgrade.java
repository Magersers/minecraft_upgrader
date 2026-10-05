package dev.upgrade;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@net.minecraftforge.fml.common.Mod(Upgrade.ID)
public final class Upgrade {
    public static final String ID="upgrade";
    public static final Logger LOGGER=LoggerFactory.getLogger(ID);
    public Upgrade() {
        ServerTransport.register();

        net.minecraftforge.event.RegisterCommandsEvent.BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("upgrade").then(Commands.literal("hard").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx->{
                            boolean enabled=com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx,"enabled");
                            try { PricingPolicy.setHard(enabled); Economy.rebuild(ctx.getSource().getServer()); Network.clear();
                                ctx.getSource().sendSuccess(()->net.minecraft.network.chat.Component.translatable(enabled?"upgrade.hard_on":"upgrade.hard_off"),true); return 1;
                            } catch (java.io.IOException ex) { ctx.getSource().sendFailure(net.minecraft.network.chat.Component.translatable("upgrade.save_failed")); return 0; }
                        }))).then(Commands.literal("audit").requires(source->source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).executes(ctx->{
                    try { var path=EconomyAudit.write(); ctx.getSource().sendSuccess(()->net.minecraft.network.chat.Component.translatable("upgrade.audit",path.toString()),false); return 1; }
                    catch (java.io.IOException ex) { ctx.getSource().sendFailure(net.minecraft.network.chat.Component.translatable("upgrade.audit_failed",ex.getMessage())); return 0; }
                })).executes(ctx -> { Network.catalog(ctx.getSource().getPlayerOrException(),true); return Command.SINGLE_SUCCESS; })));
        net.minecraftforge.event.server.ServerStartedEvent.BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent event) -> Economy.rebuild(event.getServer()));
        net.minecraftforge.event.OnDatapackSyncEvent.BUS.addListener((net.minecraftforge.event.OnDatapackSyncEvent event) -> {
            if (event.getPlayer()==null) { Economy.rebuild(event.getPlayerList().getServer()); Network.clear(); }
        });
        net.minecraftforge.event.server.ServerStoppedEvent.BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) -> Network.clear());
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent.Post event) -> { Network.tick(event.server()); });
        net.minecraftforge.event.entity.player.PlayerEvent.Clone.BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.Clone event) -> {
            if (event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer oldPlayer &&
                    event.getEntity() instanceof net.minecraft.server.level.ServerPlayer newPlayer)
                Network.copyPending(oldPlayer,newPlayer);
        });
        net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> Network.forget(event.getEntity().getUUID()));
    }
}
