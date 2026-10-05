package dev.upgrade;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
public final class ModernItems {
 public static EquipmentSlot slot(ItemStack stack) { var e=stack.get(DataComponents.EQUIPPABLE); return e==null?EquipmentSlot.MAINHAND:e.slot(); }
 public static boolean armor(ItemStack stack) { return Platform.attributes(stack,slot(stack)).getOrDefault("armor",0d)>0; }
 public static double stat(Item item,String key) { var s=item.getDefaultInstance(); return Platform.attributes(s,slot(s)).getOrDefault(key,0d); }
 public static double mining(ItemStack stack) { var t=stack.get(DataComponents.TOOL); return t==null?0:t.rules().stream().mapToDouble(r->r.speed().orElse(t.defaultMiningSpeed())).max().orElse(t.defaultMiningSpeed()); }
}
