package dev.upgrade.compat;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Queries Forge Energy without changing the stack or invoking machine/armour behaviour. */
public final class EnergyCompat {
    private EnergyCompat() {}
    public record Charge(long amount, long capacity) {
        public double fraction() { return capacity <= 0 ? 0 : Math.max(0, Math.min(1, amount / (double) capacity)); }
    }
    public static boolean abilityEnabled(String item, String ability) { return true; }
    public static Charge read(ItemStack stack) {
        try {
            var storage = net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
            if (storage == null) return null;
            long capacity = storage.getCapacityAsLong(), amount = storage.getAmountAsLong();
            return capacity > 0 && amount >= 0 ? new Charge(Math.min(amount, capacity), capacity) : null;
        } catch (RuntimeException ex) { return null; }
    }
}
