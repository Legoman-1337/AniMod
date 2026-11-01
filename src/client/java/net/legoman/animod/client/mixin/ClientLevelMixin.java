package net.legoman.animod.client.mixin;

import net.legoman.animod.client.StoryScreenOverlay;
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
        if (StoryScreenOverlay.isBloodMoonActive()) {
            // Кровавая луна: горящее багровое небо
            long time = level.getDayTime() % 24000L;
            boolean night = time >= 12500L && time <= 23500L;
            cir.setReturnValue(night ? new Vec3(0.25, 0.02, 0.02) : new Vec3(0.45, 0.12, 0.08));
            return;
        }
        if (StoryScreenOverlay.isNoneMoonActive()) {
            // Безлуние: абсолютная тьма
            cir.setReturnValue(new Vec3(0.01, 0.01, 0.015));
            return;
        }
        if (StoryScreenOverlay.isKosmosSkyActive()) {
            // Космос: чёрная бездна с багровым дыханием чёрной дыры
            cir.setReturnValue(new Vec3(0.02, 0.005, 0.03));
            return;
        }
        if (StoryScreenOverlay.isToxicSkyActive()) {
            // Ядовитое небо Акта 2: болотная зелень днём (кислотный дождь), чёрная гниль ночью
            long time = level.getDayTime() % 24000L;
            boolean night = time >= 12500L && time <= 23500L;
            cir.setReturnValue(night ? new Vec3(0.05, 0.10, 0.03) : new Vec3(0.35, 0.45, 0.25));
            return;
        }
        if (StoryManager.isInsomniaNightActive(level)) {
            // Глубокое темно-медное ночное небо вместо синего
            cir.setReturnValue(new Vec3(0.18, 0.07, 0.02));
        }
    }
}