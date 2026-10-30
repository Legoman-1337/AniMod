package net.legoman.animod.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.legoman.animod.client.StoryScreenOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    // Гарантированное окрашивание Луны и ночных небесных тел в теплый янтарно-медный цвет
    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", shift = At.Shift.AFTER))
    private void animod$tintMoonHarvest(PoseStack poseStack, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable skyFogSetup, CallbackInfo ci) {
        if (StoryScreenOverlay.isInsomniaSkyActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                long time = mc.level.getDayTime() % 24000L;
                if (time >= 12500L && time <= 23500L) {
                    // Медно-янтарный пылающий оттенок Harvest Moon
                    RenderSystem.setShaderColor(1.0F, 0.55F, 0.16F, 1.0F);
                }
            }
        }
    }
}