package dev.upgrade;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(Upgrade.ID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    @GameTest(template="empty", timeoutTicks=200)
    public static void delayedAndExactlyOncePayout(GameTestHelper helper) throws ReflectiveOperationException {
        FakePlayer player=player(helper);
        try {
            UUID token=start(player);
            helper.assertTrue(player.getMainHandItem().getCount()==15,"Stake must be consumed once");
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)==0,"No reward during animation");
            var pending=player.getPersistentData().getCompound("upgradePendingRoll");
            pending.putBoolean("won",true); // Exercise a deterministic winning payout after a real accepted spin.
            long due=pending.getLong("due");
            Network.settle(player,due);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)==0,"Wait for client animation acknowledgement");
            pending.putBoolean("acknowledged",true);
            Network.settle(player,due-1);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)==0,"Early acknowledgement must not skip server delay");
            Network.settle(player,due);
            Network.settle(player,due+1);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)==1,"Deliver exactly one reward");
            spin(player,new Network.Spin(token,"minecraft:diamond",1));
            helper.assertTrue(player.getMainHandItem().getCount()==15,"Replayed Spin must not consume a second stake");
            helper.assertTrue(!player.getPersistentData().contains("upgradePendingRoll"),"Pending payout must be cleared");
            helper.succeed();
        } finally { Network.forget(player.getUUID()); }
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void lossAndInterruptedPlayerRecovery(GameTestHelper helper) throws ReflectiveOperationException {
        FakePlayer player=player(helper), clone=player(helper);
        try {
            start(player);
            var pending=player.getPersistentData().getCompound("upgradePendingRoll");
            pending.putBoolean("won",false);
            pending.putBoolean("acknowledged",true);
            Network.settle(player,pending.getLong("due"));
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)==0,"Losing roll must not pay a reward");
            helper.assertTrue(player.getMainHandItem().getCount()==15,"Losing roll consumes only its stake");
            Network.forget(player.getUUID());
            start(player);
            pending=player.getPersistentData().getCompound("upgradePendingRoll"); pending.putBoolean("won",true);
            Network.copyPending(player,clone);
            long due=pending.getLong("due");
            // Clone simulates death/recreation; no acknowledgement after a disconnected screen.
            Network.settle(clone,due+199);
            helper.assertTrue(clone.getInventory().countItem(Items.DIAMOND)==0,"Recovery timeout must be respected");
            Network.settle(clone,due+200); Network.settle(clone,due+201);
            helper.assertTrue(clone.getInventory().countItem(Items.DIAMOND)==1,"Interrupted player must recover prize exactly once");
            helper.succeed();
        } finally { Network.forget(player.getUUID()); Network.forget(clone.getUUID()); }
    }
    private static FakePlayer player(GameTestHelper helper) {
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"UpgradeTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,16));
        return player;
    }
    private static UUID start(FakePlayer player) throws ReflectiveOperationException {
        Network.catalog(player,true);
        var sessions=Network.class.getDeclaredField("SESSIONS"); sessions.setAccessible(true);
        Object session=((Map<?,?>)sessions.get(null)).get(player.getUUID());
        var tokenAccessor=session.getClass().getDeclaredMethod("token"); tokenAccessor.setAccessible(true);
        UUID token=(UUID)tokenAccessor.invoke(session);
        spin(player,new Network.Spin(token,"minecraft:diamond",1));
        if (!player.getPersistentData().contains("upgradePendingRoll")) throw new AssertionError("Spin was not accepted");
        return token;
    }
    private static void spin(FakePlayer player,Network.Spin packet) throws ReflectiveOperationException {
        var method=Network.class.getDeclaredMethod("spin",net.minecraft.server.level.ServerPlayer.class,Network.Spin.class);
        method.setAccessible(true); method.invoke(null,player,packet);
    }
}
