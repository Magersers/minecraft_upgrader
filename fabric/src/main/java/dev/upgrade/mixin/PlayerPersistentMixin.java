package dev.upgrade.mixin;
import dev.upgrade.PersistentPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerPersistentMixin implements PersistentPlayer {
    @Unique private CompoundTag upgrade$data=new CompoundTag();
    @Override public CompoundTag upgrade$data() { return upgrade$data; }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void upgrade$save(CompoundTag nbt,CallbackInfo ci) { if (!upgrade$data.isEmpty()) nbt.put("UpgradeFabric",upgrade$data.copy()); }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void upgrade$load(CompoundTag nbt,CallbackInfo ci) { upgrade$data=nbt.getCompound("UpgradeFabric").copy(); }
}
