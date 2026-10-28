package net.legoman.animod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.legoman.animod.ModSounds;
import net.legoman.animod.client.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.Random;

public class StoryScreenOverlay {
    private static final ResourceLocation VIGNETTE_LOCATION = new ResourceLocation("minecraft", "textures/misc/vignette.png");
    private static final ResourceLocation WOBBLE_SHADER = new ResourceLocation("minecraft", "shaders/post/wobble.json");
    private static final Random RND = new Random();

    private static int activeCutsceneState = 0;
    private static int remainingTicks = 0;
    private static int totalDurationTicks = 0;

    // Плавный зум камеры через AbstractClientPlayerMixin
    private static float currentFovMultiplier = 1.0f;
    private static float targetFovMultiplier = 1.0f;

    // Механика сердцебиения и вспышек
    private static int ticksUntilNextHeartbeat = 0;
    private static float redFlashAlpha = 0.0f;

    // Статусы деформации и шейдера
    private static boolean shaderActive = false;
    private static boolean secondVoiceTriggered = false;

    public static void startCutscene(int stateId, int durationTicks) {
        activeCutsceneState = stateId;
        remainingTicks = durationTicks;
        totalDurationTicks = durationTicks;
        ticksUntilNextHeartbeat = 18;
        redFlashAlpha = 0.0f;
        currentFovMultiplier = 1.0f;
        targetFovMultiplier = 1.0f;
        secondVoiceTriggered = false;

        Minecraft mc = Minecraft.getInstance();

        // Фаза 1: Чистый женский плач Ани
        if (stateId == 3) {
            mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.ANYA_SOB, 1.0F, 1.0F)
            );
        }
    }

    public static float getFovMultiplier() {
        return currentFovMultiplier;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (remainingTicks <= 0) {
            if (activeCutsceneState != 0) {
                activeCutsceneState = 0;
                currentFovMultiplier = 1.0f;
                targetFovMultiplier = 1.0f;
                shutdownShader(mc);
            }
            redFlashAlpha = 0.0f;
            return;
        }

        remainingTicks--;

        int elapsed = totalDurationTicks - remainingTicks;
        float progress = (float) elapsed / (float) Math.max(1, totalDurationTicks);

        // Плавная интерполяция зума камеры через миксин
        targetFovMultiplier = Mth.lerp(0.12f, targetFovMultiplier, 1.0f);
        currentFovMultiplier = Mth.lerp(0.20f, currentFovMultiplier, targetFovMultiplier);

        // Угасание вспышки красного
        if (redFlashAlpha > 0.0f) {
            redFlashAlpha = Math.max(0.0f, redFlashAlpha - 0.04f);
        }

        if (activeCutsceneState == 3) {
            // ФАЗА 2: Включение деформации голоса и расплавления экрана на 12-й секунде (тик 240)
            if (elapsed >= 240 && !secondVoiceTriggered) {
                secondVoiceTriggered = true;

                // Демоническое замедление звука плача (глубокий неестественный тон 0.58x)
                mc.getSoundManager().play(
                        SimpleSoundInstance.forUI(ModSounds.ANYA_SOB, 0.58F, 1.0F)
                );

                // Включение ванильного шейдера таяния/искривления мира
                loadWobbleShader(mc);
            }

            // Отключение шейдера перед выходом (на 18-й секунде, прогресс 0.78)
            if (progress >= 0.78f && shaderActive) {
                shutdownShader(mc);
            }

            // РАЗГОН ПУЛЬСА (до 18-й секунды)
            if (progress < 0.78f) {
                ticksUntilNextHeartbeat--;
                if (ticksUntilNextHeartbeat <= 0) {
                    float accelFactor = progress / 0.78f;
                    int nextDelay = (int) Mth.lerp(accelFactor, 36.0f, 9.0f);
                    ticksUntilNextHeartbeat = Math.max(7, nextDelay);

                    // Звук удара сердца с нарастанием панического тона
                    float pitch = Mth.lerp(accelFactor, 0.82f, 1.25f);
                    mc.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, pitch)
                    );

                    redFlashAlpha = 0.45f + (accelFactor * 0.20f);

                    // Микро-толчок камеры через миксин (на 5-6% вперед)
                    targetFovMultiplier = 0.94f;
                }
            } else if (progress >= 0.78f && progress < 0.86f && ticksUntilNextHeartbeat > 0) {
                // Затихающий последний удар
                ticksUntilNextHeartbeat--;
                if (ticksUntilNextHeartbeat == 0) {
                    mc.getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.70f)
                    );
                    redFlashAlpha = 0.35f;
                    targetFovMultiplier = 0.97f;
                }
            }
        }
    }

    private static void loadWobbleShader(Minecraft mc) {
        if (!shaderActive && mc.gameRenderer != null) {
            try {
                ((GameRendererAccessor) mc.gameRenderer).animod$loadEffect(WOBBLE_SHADER);
                shaderActive = true;
            } catch (Throwable ignored) {
                // Защита от конфликтов со сторонними шейдерпаками
            }
        }
    }

    private static void shutdownShader(Minecraft mc) {
        if (shaderActive && mc.gameRenderer != null) {
            try {
                mc.gameRenderer.shutdownEffect();
            } catch (Throwable ignored) {}
            shaderActive = false;
        }
    }

    public static void render(GuiGraphics graphics, float tickDelta) {
        if (remainingTicks <= 0 || activeCutsceneState == 0) return;

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        int elapsed = totalDurationTicks - remainingTicks;
        float progress = (float) elapsed / (float) Math.max(1, totalDurationTicks);

        // Плавный вход (1 сек) и ПЛАВНЫЙ ВЫХОД в норму (последние 5 секунд)
        float fadeIn = Math.min(1.0f, (float) elapsed / 25.0f);
        float fadeOut = Math.min(1.0f, (float) remainingTicks / 100.0f);
        float masterAlpha = Math.min(fadeIn, fadeOut);

        if (activeCutsceneState == 3) {
            // 1. КРУГЛАЯ ВИНЬЕТКА
            float vignetteDarkness = 0.90f * masterAlpha;
            float brightness = Math.max(0.0f, 1.0f - vignetteDarkness);

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ZERO,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );

            graphics.setColor(brightness, brightness, brightness, 1.0F);
            graphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();

            // 2. КРАСНЫЙ ПУЛЬС В ТАКТ СЕРДЦЕБИЕНИЮ
            if (redFlashAlpha > 0.01f) {
                int redA = (int) (redFlashAlpha * masterAlpha * 255.0f);
                int redColor = (redA << 24) | 0x00A00000;
                graphics.fill(0, 0, width, height, redColor);
            }

            // 3. ДЕФОРМИРУЮЩИЙ ХРОМАТИЧЕСКИЙ ГЛИТЧ (RGB TEARING)
            if (progress > 0.45f && progress < 0.80f) {
                long timeMs = System.currentTimeMillis();

                for (int i = 0; i < 3; i++) {
                    if (RND.nextInt(100) < 35) {
                        int sliceY = RND.nextInt(height);
                        int sliceH = 4 + RND.nextInt(16);
                        int shiftX = (RND.nextInt(14) - 7);

                        // Красный спектр
                        graphics.fill(Math.max(0, shiftX), sliceY, Math.min(width, width + shiftX), sliceY + sliceH, (45 << 24) | 0x00FF0033);
                        // Бирюзовый спектр со смещением в противоположную сторону
                        graphics.fill(Math.max(0, -shiftX), sliceY, Math.min(width, width - shiftX), sliceY + sliceH, (45 << 24) | 0x0000E5FF);
                    }
                }

                if (remainingTicks % 2 == 0) {
                    int lineY = (int) ((timeMs / 3) % height);
                    int glitchAlpha = (int) (35 * masterAlpha);
                    graphics.fill(0, lineY, width, lineY + 2, (glitchAlpha << 24) | 0x00FFFFFF);
                }
            }
        }
    }
}