package quasaris.sharedfate.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;
import quasaris.sharedfate.DeathSync;

@Mixin(CombatTracker.class)
public abstract class CombatTrackerMixin {
    @Shadow
    @Final
    private LivingEntity mob;

    @Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
    private void sharedfate$customDeathMessage(CallbackInfoReturnable<Component> cir) {
        if (this.mob instanceof ServerPlayer player) {
            Component custom = DeathSync.pendingFor(player.getUUID());
            if (custom != null) {
                cir.setReturnValue(custom);
            }
        }
    }
}
