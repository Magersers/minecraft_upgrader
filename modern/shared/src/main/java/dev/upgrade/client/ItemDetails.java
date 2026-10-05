package dev.upgrade.client;

import static dev.upgrade.client.L10n.text;

import dev.upgrade.Platform;
import dev.upgrade.LoaderPlatform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;

import net.minecraft.world.item.ItemStack;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/** Compact player-facing facts; calculation diagnostics stay in the economy audit. */
final class ItemDetails {
    private static final int LABEL=0x9DAFC7, MOD=0x899DBF, GOLD=0xFFD68A;
    private static final Map<String,String> MOD_NAMES=new HashMap<>();

    static List<Component> lines(ItemStack stack,double value,boolean inventory,boolean available) {
        if (stack.isEmpty()) return List.of(Component.literal(text("upgrade.select_item")).withStyle(s->s.withColor(LABEL)));
        List<Component> lines=new ArrayList<>();
        lines.add(stack.getHoverName().copy().withStyle(s->s.withColor(0xF0F5FF).withBold(true)));
        String namespace=BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        lines.add(Component.literal(MOD_NAMES.computeIfAbsent(namespace,ItemDetails::modName))
                .withStyle(s->s.withColor(MOD).withItalic(true)));
        List<Component> stats=new ArrayList<>();
        // Some mods compute attributes from live world state; a missing stat must not break the screen.
        try {
            var hand=Platform.attributes(stack,EquipmentSlot.MAINHAND);
            double damage=hand.getOrDefault("attack_damage",0d);
            if (damage>0) stats.add(row(text("upgrade.damage"),number(damage),0xFFA18F));
            var equipped=Platform.attributes(stack,dev.upgrade.ModernItems.slot(stack));
            double armor=equipped.getOrDefault("armor",0d),toughness=equipped.getOrDefault("armor_toughness",0d);
            if (armor>0) stats.add(row(text("upgrade.armor"),number(armor),0x94D5FF));
            if (toughness>0) stats.add(row(text("upgrade.toughness"),number(toughness),0x94D5FF));
        } catch (RuntimeException ignored) { /* Keep the item name and server valuation available. */ }
        if (!stats.isEmpty()) { lines.add(Component.empty()); lines.addAll(stats); }
        lines.add(Component.empty());
        if (value>0 && Double.isFinite(value)) {
            lines.add(row(inventory?text("upgrade.stake_value"):text("upgrade.reward_value"),number(value)+" E",GOLD));
            lines.add(Component.literal(text("upgrade.per_item")).withStyle(s->s.withColor(MOD)));
        } else lines.add(Component.literal(text("upgrade.unavailable")).withStyle(s->s.withColor(0xFF9AAA)));
        if (value>0&&!available) lines.add(Component.literal(text("upgrade.unavailable")).withStyle(s->s.withColor(0xFF9AAA)));
        return lines;
    }

    private static MutableComponent row(String label,String value,int color) {
        return Component.literal(label+"  ").withStyle(s->s.withColor(LABEL))
                .append(Component.literal(value).withStyle(s->s.withColor(color).withBold(true)));
    }

    private static String number(double value) {
        var language=net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        var symbols=DecimalFormatSymbols.getInstance(Locale.forLanguageTag(language.replace('_','-')));
        if (value>0&&value<.01) return "<"+new DecimalFormat("0.00",symbols).format(.01);
        return new DecimalFormat("#,##0.##",symbols).format(value);
    }

    private static String modName(String namespace) {
        if (namespace.equals("minecraft")) return "Minecraft";
        return LoaderPlatform.modName(namespace)
                .orElseGet(()->{
                    String words=namespace.replace('_',' ').replace('-',' ');
                    return words.isEmpty()?text("upgrade.mod"):Character.toUpperCase(words.charAt(0))+words.substring(1);
                });
    }
}
