package dev.upgrade.compat;

import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.minecraft.world.item.ItemStack;

/** Read-only optional Fabric Energy API. Never extracts energy or calls armour tick/combat hooks. */
public final class EnergyCompat {
    private EnergyCompat() {}
    public record Charge(long amount,long capacity) { public double fraction() { return capacity<=0?0:Math.max(0,Math.min(1,amount/(double)capacity)); } }
    private static Class<?> api;
    private static ItemApiLookup<?,ContainerItemContext> lookup;
    static {
        try {
            api=Class.forName("team.reborn.energy.api.EnergyStorage");
            lookup=(ItemApiLookup<?,ContainerItemContext>)api.getField("ITEM").get(null);
        } catch (ReflectiveOperationException|LinkageError ignored) { api=null; lookup=null; }
    }
    public static boolean abilityEnabled(String item,String ability) {
        if (!item.startsWith("techreborn:quantum_")) return true;
        String field=switch(ability) { case "flight"->"quantumSuitEnableFlight"; case "speed"->item.endsWith("leggings")?"quantumSuitEnableSprint":""; default->""; };
        if (field.isEmpty()) return true;
        try { return Class.forName("techreborn.config.TechRebornConfig").getField(field).getBoolean(null); }
        catch (ReflectiveOperationException|LinkageError ex) { return false; }
    }
    public static Charge read(ItemStack stack) {
        if (lookup==null) return null;
        try {
            Object storage=lookup.find(stack,ContainerItemContext.withConstant(stack)); if (storage==null) return null;
            long amount=((Number)api.getMethod("getAmount").invoke(storage)).longValue();
            long capacity=((Number)api.getMethod("getCapacity").invoke(storage)).longValue();
            return capacity>0 && amount>=0?new Charge(Math.min(amount,capacity),capacity):null;
        } catch (ReflectiveOperationException|RuntimeException ex) { return null; }
    }
}
