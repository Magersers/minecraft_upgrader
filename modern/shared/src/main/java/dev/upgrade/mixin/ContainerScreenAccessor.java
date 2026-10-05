package dev.upgrade.mixin;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int upgrade$left();
    @Accessor("topPos") int upgrade$top();
    @Accessor("imageWidth") int upgrade$width();
}
