package dev.upgrade;
public final class Fixture {
 public static void login(net.minecraft.server.level.ServerPlayer p) {
  if(!Boolean.getBoolean("upgrade.keepServer")) return;
  p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.getAbilities().invulnerable=true;
  p.getInventory().clearContent();p.getInventory().setSelectedSlot(0);
  p.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG,32));p.inventoryMenu.broadcastChanges();
 }
}

