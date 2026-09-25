package dev.upgrade;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
public final class TestPlatform {
 public static ServerPlayer player(ServerLevel level) { return new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"UpgradeTest")) {
  @Override public void displayClientMessage(Component message,boolean actionBar) {}
 }; }
 public static net.minecraft.network.FriendlyByteBuf buffer(ServerLevel level) { return new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer()); }
 public static void name(ItemStack stack) { stack.setHoverName(Component.literal("Test")); }
}
