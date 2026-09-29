package dev.upgrade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Only supplies a survival inventory; no changes to RNG, prices, packets, or payouts. */
@EventBusSubscriber(modid="upgrade")
public final class LiveServerFixture {
    @SubscribeEvent
    public static void ready(ServerStartedEvent event) throws java.io.IOException {
        if (Boolean.getBoolean("upgrade.e2eServer"))
            java.nio.file.Files.writeString(java.nio.file.Path.of("upgrade-e2e-ready.txt"), "READY\n");
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("upgrade.e2eServer") || !(event.getEntity() instanceof ServerPlayer player)) return;
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(true);
        player.getInventory().clearContent();
        player.getInventory().selected=0;
        player.getInventory().setItem(0,new ItemStack(Items.OAK_LOG,32));
        player.inventoryMenu.broadcastChanges();
    }
}
