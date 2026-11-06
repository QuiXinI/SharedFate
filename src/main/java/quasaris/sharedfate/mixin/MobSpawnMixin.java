package quasaris.sharedfate.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Mob;
import quasaris.sharedfate.WorldStartPhase;

@Mixin(Mob.class)
public abstract class MobSpawnMixin {
    @Inject(method = "isEffectiveAi", at = @At("HEAD"), cancellable = true)
    private void sharedfate$freezeAllMobs(CallbackInfoReturnable<Boolean> cir) {
        if (WorldStartPhase.isActive()) {
            cir.setReturnValue(false);
        }
    }
}
