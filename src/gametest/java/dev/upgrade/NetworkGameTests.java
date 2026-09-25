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
            spin(player,new Network.Spin(token,"minecraft:diamond",1,0,1));
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
    @GameTest(template="empty", timeoutTicks=200)
    public static void inventorySlotAndStackReward(GameTestHelper helper) throws ReflectiveOperationException {
        FakePlayer player=player(helper);
        try {
            player.getInventory().setItem(12,new ItemStack(Items.IRON_INGOT,10));
            UUID token=session(player);
            player.getInventory().selected=2; // Selection of the hotbar is unrelated to the chosen stake slot.
            spin(player,new Network.Spin(token,"minecraft:diamond",3,12,8));
            helper.assertTrue(player.getInventory().getItem(12).getCount()==7,"Consume chosen inventory slot only");
            helper.assertTrue(player.getInventory().getItem(0).getCount()==16,"Original hand must be untouched");
            var pending=player.getPersistentData().getCompound("upgradePendingRoll");
            helper.assertTrue(ItemStack.of(pending.getCompound("reward")).getCount()==8,"Persist complete prize stack");
            pending.putBoolean("won",true); pending.putBoolean("acknowledged",true);
            Network.settle(player,pending.getLong("due")-1);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)==0,"No stack before animation ends");
            Network.settle(player,pending.getLong("due")); Network.settle(player,Long.MAX_VALUE);
            helper.assertTrue(player.getInventory().countItem(Items.DIAMOND)==8,"Pay eight items exactly once");
            helper.succeed();
        } finally { Network.forget(player.getUUID()); }
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void priceUsesWholeRewardStack(GameTestHelper helper) throws ReflectiveOperationException {
        FakePlayer player=player(helper);
        try {
            spin(player,new Network.Spin(session(player),"minecraft:iron_block",16,0,2));
            helper.assertTrue(player.getPersistentData().contains("upgradePendingRoll"),"Two blocks cost more than 16 ingots, although one block does not");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(),"Consume all 16 selected ingots");
            helper.succeed();
        } finally { Network.forget(player.getUUID()); }
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void rejectForgedCountsAndChangedSlots(GameTestHelper helper) throws ReflectiveOperationException {
        for (String target:new String[]{"minecraft:diamond","minecraft:diamond_pickaxe","minecraft:snowball"}) {
            int limit=target.endsWith("diamond")?64:target.endsWith("pickaxe")?1:16;
            for (int count:new int[]{0,-1,limit+1,Integer.MAX_VALUE}) {
                FakePlayer player=player(helper);
                try {
                    spin(player,new Network.Spin(session(player),target,1,0,count));
                    helper.assertTrue(!player.getPersistentData().contains("upgradePendingRoll"),"Reject invalid reward count "+target+" / "+count);
                    helper.assertTrue(player.getInventory().getItem(0).getCount()==16,"Rejected request must not consume stake");
                } finally { Network.forget(player.getUUID()); }
            }
        }
        for (int slot:new int[]{-1,36,39,41,Integer.MAX_VALUE}) {
            FakePlayer player=player(helper);
            try {
                spin(player,new Network.Spin(session(player),"minecraft:diamond",1,slot,1));
                helper.assertTrue(!player.getPersistentData().contains("upgradePendingRoll"),"Reject nonexistent or equipped slot");
            } finally { Network.forget(player.getUUID()); }
        }
        FakePlayer changed=player(helper);
        try {
            UUID token=session(changed); changed.getInventory().getItem(0).shrink(1);
            spin(changed,new Network.Spin(token,"minecraft:diamond",1,0,1));
            helper.assertTrue(changed.getInventory().getItem(0).getCount()==15&&!changed.getPersistentData().contains("upgradePendingRoll"),"Reject stale inventory snapshot");
            Network.forget(changed.getUUID());
            changed.getInventory().setItem(40,new ItemStack(Items.IRON_INGOT,2));
            spin(changed,new Network.Spin(session(changed),"minecraft:diamond",1,40,1));
            helper.assertTrue(changed.getInventory().getItem(40).getCount()==1,"Offhand selection must work");
            helper.succeed();
        } finally { Network.forget(changed.getUUID()); }
    }
    private static FakePlayer player(GameTestHelper helper) {
        var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"UpgradeTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,16));
        return player;
    }
    private static UUID start(FakePlayer player) throws ReflectiveOperationException {
        UUID token=session(player);
        spin(player,new Network.Spin(token,"minecraft:diamond",1,0,1));
        if (!player.getPersistentData().contains("upgradePendingRoll")) throw new AssertionError("Spin was not accepted: iron="+Economy.reason(player,new ItemStack(Items.IRON_INGOT))+"; diamond="+Economy.reason(player,new ItemStack(Items.DIAMOND)));
        return token;
    }
    private static UUID session(FakePlayer player) throws ReflectiveOperationException {
        Network.catalog(player,true);
        var sessions=Network.class.getDeclaredField("SESSIONS"); sessions.setAccessible(true);
        Object session=((Map<?,?>)sessions.get(null)).get(player.getUUID());
        var tokenAccessor=session.getClass().getDeclaredMethod("token"); tokenAccessor.setAccessible(true);
        return (UUID)tokenAccessor.invoke(session);
    }
    private static void spin(FakePlayer player,Network.Spin packet) throws ReflectiveOperationException {
        var method=Network.class.getDeclaredMethod("spin",net.minecraft.server.level.ServerPlayer.class,Network.Spin.class);
        method.setAccessible(true);
        // FakePlayer advancement progress is deliberately a no-op. These tests exercise the protocol,
        // so make all price gates available temporarily, while retaining prices and revision.
        var original=Economy.current;
        var values=new java.util.HashMap<String,dev.upgrade.core.CostEngine.Value>();
        original.values().forEach((id,v) -> values.put(id,new dev.upgrade.core.CostEngine.Value(v.cost(),v.confidence(),v.source(),java.util.Set.of())));
        Economy.current=new Economy.Snapshot(original.revision(),values,original.denied(),original.explanations());
        try { method.invoke(null,player,packet); } finally { Economy.current=original; }
    }
}
