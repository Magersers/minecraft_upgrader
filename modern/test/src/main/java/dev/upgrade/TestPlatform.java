package dev.upgrade;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
public final class TestPlatform {
 public static ServerPlayer player(ServerLevel level) { return new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"UpgradeTest"),net.minecraft.server.level.ClientInformation.createDefault()) {
  @Override public void sendOverlayMessage(Component c) {}
 }; }
}
