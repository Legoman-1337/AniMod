package net.legoman.animod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.legoman.animod.AnyaEntity;
import net.legoman.animod.ModSounds;
import net.legoman.animod.client.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;

public class StoryScreenOverlay {
    private static final ResourceLocation VIGNETTE_LOCATION = new ResourceLocation("minecraft", "textures/misc/vignette.png");
    private static final ResourceLocation WOBBLE_SHADER = new ResourceLocation("minecraft", "shaders/post/wobble.json");
    private static final ResourceLocation DESATURATE_SHADER = new ResourceLocation("minecraft", "shaders/post/desaturate.json");
    private static final ResourceLocation HORROR_FACE = new ResourceLocation("animod", "textures/entity/face_horror.png");
    private static final Random RND = new Random();

    private static int activeCutsceneState = 0;
    private static int remainingTicks = 0;
    private static int totalDurationTicks = 0;

    private static float currentFovMultiplier = 1.0f;
    private static float targetFovMultiplier = 1.0f;

    private static int ticksUntilNextHeartbeat = 0;
    private static float redFlashAlpha = 0.0f;

    private static boolean shaderActive = false;
    private static boolean desaturateLoaded = false;
    private static boolean laughTriggered = false;
    private static int laughGlitchTicks = 0;

    private static int alarmLoopTimer = 0;
    private static int alarmCycleCount = 0;

    private static boolean mouseLockedHardware = false;

    // Скример
    private static boolean voidScreamerActive = false;
    private static int voidScreamerTicks = 0;

    // 3-секундный глитч кровати
    private static int bedGlitchTicks = 0;

    // Ночное небо Harvest Moon
    private static boolean insomniaSkyActive = false;

    public static boolean isMouseLocked() {
        return mouseLockedHardware;
    }

    public static boolean isInsomniaSkyActive() {
        return insomniaSkyActive;
    }

    public static void setInsomniaSkyActive(boolean active) {
        insomniaSkyActive = active;
    }

    public static void startPocketAudio() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.POCKET_ENTER, 1.2F, 1.0F));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.POCKET_AMBIENT, 1.0F, 1.0F));
        }
    }

    public static void stopPocketAudio() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().stop(ModSounds.POCKET_AMBIENT_ID, null);
            mc.getSoundManager().stop(ModSounds.POCKET_ENTER_ID, null);
        }
    }

    public static void startCutscene(int stateId, int durationTicks) {
        if (stateId == 996) {
            startPocketAudio();
            return;
        }

        if (stateId == 997) {
            stopPocketAudio();
            return;
        }

        if (stateId == 999) {
            triggerVoidScreamer();
            return;
        }

        if (stateId == 998) {
            triggerBedGlitch();
            return;
        }

        activeCutsceneState = stateId;
        remainingTicks = durationTicks;
        totalDurationTicks = durationTicks;
        ticksUntilNextHeartbeat = 25;
        redFlashAlpha = 0.0f;
        currentFovMultiplier = 1.0f;
        targetFovMultiplier = 1.0f;
        mouseLockedHardware = true;

        shaderActive = false;
        desaturateLoaded = false;
        laughTriggered = false;
        laughGlitchTicks = 0;
        alarmLoopTimer = 0;
        alarmCycleCount = 0;
        voidScreamerActive = false;
        voidScreamerTicks = 0;

        Minecraft mc = Minecraft.getInstance();
        if (stateId == 3) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_SOB, 1.0F, 1.0F));
        }
    }

    public static void triggerBedGlitch() {
        bedGlitchTicks = 60;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_ALARM, 0.85F, 1.2F));
    }

    public static void triggerVoidScreamer() {
        voidScreamerActive = true;
        voidScreamerTicks = 40;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_ROAR, 1.5F, 1.25F));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 1.4F));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_ALARM, 1.6F, 1.7F));
    }

    public static float getFovMultiplier() {
        return currentFovMultiplier;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (voidScreamerTicks > 0) {
            voidScreamerTicks--;
            if (voidScreamerTicks <= 0) voidScreamerActive = false;
        }

        if (bedGlitchTicks > 0) {
            bedGlitchTicks--;
        }

        if (laughGlitchTicks > 0) {
            laughGlitchTicks--;
        }

        if (remainingTicks <= 0) {
            if (activeCutsceneState != 0) {
                activeCutsceneState = 0;
                currentFovMultiplier = 1.0f;
                targetFovMultiplier = 1.0f;
                mouseLockedHardware = false;
                shutdownShader(mc);
            }
            redFlashAlpha = 0.0f;
            return;
        }

        remainingTicks--;
        int elapsed = totalDurationTicks - remainingTicks;

        targetFovMultiplier = Mth.lerp(0.12f, targetFovMultiplier, 1.0f);
        currentFovMultiplier = Mth.lerp(0.20f, currentFovMultiplier, targetFovMultiplier);

        if (redFlashAlpha > 0.0f) {
            redFlashAlpha = Math.max(0.0f, redFlashAlpha - 0.04f);
        }

        if (activeCutsceneState == 3) {
            // ЭТАП 1 (0-22 сек): В ГЛАЗА АНИ
            if (elapsed < 440) {
                mouseLockedHardware = true;
                if (mc.player != null && mc.level != null) {
                    AABB box = mc.player.getBoundingBox().inflate(16.0);
                    List<AnyaEntity> anyas = mc.level.getEntitiesOfClass(AnyaEntity.class, box);
                    if (!anyas.isEmpty()) {
                        AnyaEntity anya = anyas.get(0);
                        double dx = anya.getX() - mc.player.getX();
                        double dy = (anya.getY() + 0.32) - mc.player.getEyeY();
                        double dz = anya.getZ() - mc.player.getZ();
                        double distXZ = Math.sqrt(dx * dx + dz * dz);

                        float targetYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                        float targetPitch = (float) -(Math.atan2(dy, distXZ) * (180.0 / Math.PI));

                        if (elapsed >= 300) {
                            targetYaw += (RND.nextFloat() - 0.5f) * 1.4f;
                            targetPitch += (RND.nextFloat() - 0.5f) * 1.4f;
                        }

                        mc.player.setYRot(targetYaw);
                        mc.player.setXRot(targetPitch);
                    }
                }
            } else {
                // ЭТАП 2: ВЗГЛЯД В ПОЛ
                mouseLockedHardware = true;
                if (mc.player != null) {
                    mc.player.setXRot(89.5F);
                    if (elapsed >= 470 && elapsed < 700) {
                        mc.player.setYRot(mc.player.getYRot() + (RND.nextFloat() - 0.5f) * 2.2f);
                    }
                }
            }

            // 10-я секунда (тик 200) — Ч/Б ДЕСАТУРАЦИЯ
            if (elapsed >= 200 && elapsed < 440 && !desaturateLoaded) {
                desaturateLoaded = true;
                loadShader(mc, DESATURATE_SHADER);
            }

            // 20-я секунда (тик 400) — ЖУТКИЙ СМЕШОК
            if (elapsed >= 400 && !laughTriggered) {
                laughTriggered = true;
                laughGlitchTicks = 8;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_LAUGH, 1.1F, 0.9F));
                targetFovMultiplier = 0.82F;
            }

            // 22-я секунда (тик 440) — ВОББЛ И СИРЕНА
            if (elapsed >= 440) {
                if (!shaderActive) {
                    loadShader(mc, WOBBLE_SHADER);
                }

                alarmLoopTimer--;
                if (alarmLoopTimer <= 0) {
                    alarmLoopTimer = 55;
                    alarmCycleCount++;

                    float pitch = switch (alarmCycleCount % 4) {
                        case 1 -> 0.70F;
                        case 2 -> 1.35F;
                        case 3 -> 0.85F;
                        default -> 1.0F;
                    };

                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_ALARM, pitch, 1.45F));
                    redFlashAlpha = 0.35F;
                    targetFovMultiplier = 0.86F;
                }
            }

            // ПУЛЬС С FOV-КИКОМ
            ticksUntilNextHeartbeat--;
            if (ticksUntilNextHeartbeat <= 0) {
                float accel = Math.min(1.0f, (float) elapsed / 750.0f);
                int nextDelay = (int) Mth.lerp(accel, 38.0f, 6.0f);
                ticksUntilNextHeartbeat = Math.max(5, nextDelay);

                float pitch = Mth.lerp(accel, 0.75f, 1.50f);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, pitch));

                targetFovMultiplier = (elapsed < 440) ? 0.88F : 0.84F;
                redFlashAlpha = Math.max(redFlashAlpha, 0.25f);
            }
        }
    }

    private static void loadShader(Minecraft mc, ResourceLocation shader) {
        if (mc.gameRenderer != null) {
            try {
                ((GameRendererAccessor) mc.gameRenderer).animod$loadEffect(shader);
                shaderActive = true;
            } catch (Throwable ignored) {}
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
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // 1. СКРИМЕР — СТРОГО 90%, АККУРАТНО НАД ХОТБАРОМ
        if (voidScreamerActive && voidScreamerTicks > 0) {
            graphics.fill(0, 0, width, height, 0xEE50000A);

            int jitterX = RND.nextInt(26) - 13;
            int jitterY = RND.nextInt(26) - 13;

            int maxAvailableHeight = height - 28;
            int faceSize = (int) (Math.min(width, maxAvailableHeight) * 0.90f);

            int fx = (width - faceSize) / 2 + jitterX;
            int fy = (maxAvailableHeight - faceSize) / 2 + jitterY;

            graphics.blit(HORROR_FACE, fx, fy, 0, 0, faceSize, faceSize, faceSize, faceSize);
            return;
        }

        // 3-СЕКУНДНЫЙ ГЛИТЧ КРОВАТИ
        if (bedGlitchTicks > 0) {
            int glitchSeed = (int) (System.currentTimeMillis() / 45);
            Random glitchRand = new Random(glitchSeed);
            for (int i = 0; i < 6; i++) {
                int sliceY = glitchRand.nextInt(height);
                int sliceH = 2 + glitchRand.nextInt(10);
                graphics.fill(0, sliceY, width, sliceY + sliceH, 0xCC5A000E);
            }
            graphics.fill(0, 0, width, height, 0x33550000);
        }

        if (laughGlitchTicks > 0) {
            int glitchFaceSize = (int) (Math.min(width, height) * 0.68f);
            int gx = (width - glitchFaceSize) / 2 + (RND.nextInt(16) - 8);
            int gy = (height - glitchFaceSize) / 2 + (RND.nextInt(16) - 8);
            graphics.blit(HORROR_FACE, gx, gy, 0, 0, glitchFaceSize, glitchFaceSize, glitchFaceSize, glitchFaceSize);
        }

        if (remainingTicks <= 0 || activeCutsceneState == 0) return;

        int elapsed = totalDurationTicks - remainingTicks;
        float masterAlpha = Math.min(1.0f, (float) elapsed / 60.0f);

        if (activeCutsceneState == 3) {
            float vignetteDarkness = 0.95f * masterAlpha;
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

            // Бордово-кровавые вспышки пульса
            if (redFlashAlpha > 0.01f) {
                int redA = (int) (redFlashAlpha * masterAlpha * 255.0f);
                int redColor = (redA << 24) | 0x005E000A;
                graphics.fill(0, 0, width, height, redColor);
            }

            // ТВ-ПОМЕХИ И СМЕЩЕНИЕ ВИЗУАЛА (с 15-й секунды на 1 и 2 этапах)
            if (elapsed >= 300) {
                for (int y = 0; y < height; y += 3) {
                    graphics.fill(0, y, width, y + 1, 0x1E000000);
                }

                int glitchSeed = (int) (System.currentTimeMillis() / 40);
                Random glitchRand = new Random(glitchSeed);
                int tearCount = 6 + glitchRand.nextInt(6);

                for (int i = 0; i < tearCount; i++) {
                    int tearY = glitchRand.nextInt(height);
                    int tearH = 2 + glitchRand.nextInt(8);
                    int tearAlpha = (int) ((60 + glitchRand.nextInt(100)) * masterAlpha);

                    int tearColor = switch (glitchRand.nextInt(4)) {
                        case 0 -> 0x002B0824;
                        case 1 -> 0x004A000A;
                        case 2 -> 0x001A1A1A;
                        default -> 0x00050505;
                    };

                    int offsetX = glitchRand.nextInt(26) - 13;
                    graphics.fill(Math.max(0, offsetX), tearY, Math.min(width, width + offsetX), tearY + tearH, (tearAlpha << 24) | tearColor);
                }
            }
        }
    }
}