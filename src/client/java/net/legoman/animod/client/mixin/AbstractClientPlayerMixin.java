package net.legoman.animod.client.mixin;

import net.legoman.animod.client.StoryScreenOverlay;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void animod$modifyHeartbeatFov(CallbackInfoReturnable<Float> cir) {
        float multiplier = StoryScreenOverlay.getFovMultiplier();
        if (multiplier != 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * multiplier);
        }
    }
}