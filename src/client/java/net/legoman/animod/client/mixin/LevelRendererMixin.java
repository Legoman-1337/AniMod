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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long time = mc.level.getDayTime() % 24000L;
        if (time < 12500L || time > 23500L) return;

        if (StoryScreenOverlay.isBloodMoonActive()) {
            // Кровавая луна: алый диск
            RenderSystem.setShaderColor(1.0F, 0.08F, 0.05F, 1.0F);
            return;
        }

        if (StoryScreenOverlay.isNoneMoonActive()) {
            // Безлуние: луна и солнце почти невидимы (чёрные силуэты)
            RenderSystem.setShaderColor(0.01F, 0.01F, 0.01F, 1.0F);
            return;
        }

        if (StoryScreenOverlay.isToxicSkyActive()) {
            // Ядовито-зелёная Toxic Moon
            RenderSystem.setShaderColor(0.45F, 0.95F, 0.35F, 1.0F);
        } else if (StoryScreenOverlay.isInsomniaSkyActive()) {
            // Медно-янтарный пылающий оттенок Harvest Moon
            RenderSystem.setShaderColor(1.0F, 0.55F, 0.16F, 1.0F);
        }
    }
}