package net.legoman.animod.client.mixin;

import net.legoman.animod.story.StoryManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void animod$harvestSkyColor(Vec3 cameraPos, float tickDelta, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (StoryManager.isInsomniaNightActive(level)) {
            // Глубокое темно-медное ночное небо вместо синего
            cir.setReturnValue(new Vec3(0.18, 0.07, 0.02));
        }
    }
}