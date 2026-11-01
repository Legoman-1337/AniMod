package net.legoman.animod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.legoman.animod.AnyaEntity;
import net.legoman.animod.ModSounds;
import net.legoman.animod.client.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;

public class StoryScreenOverlay {
    private static final ResourceLocation VIGNETTE_LOCATION = new ResourceLocation("minecraft", "textures/misc/vignette.png");
    private static final ResourceLocation DESATURATE_SHADER = new ResourceLocation("minecraft", "shaders/post/desaturate.json");
    private static final ResourceLocation BLOOD_SHADER = new ResourceLocation("animod", "shaders/post/blood.json");
    private static final ResourceLocation HORROR_FACE = new ResourceLocation("animod", "textures/entity/face_horror.png");
    private static final ResourceLocation[] SCREAMER_FACES = {
            HORROR_FACE,
            new ResourceLocation("animod", "textures/entity/anya_abuse_face.png"),
            new ResourceLocation("animod", "textures/entity/face_75.png"),
            new ResourceLocation("animod", "textures/entity/face_yandere.png")
    };
    private static final Random RND = new Random();

    private static int activeCutsceneState = 0;
    private static int remainingTicks = 0;
    private static int totalDurationTicks = 0;

    private static float currentFovMultiplier = 1.0f;
    private static float targetFovMultiplier = 1.0f;

    private static int ticksUntilNextHeartbeat = 0;
    private static float redFlashAlpha = 0.0f;

    private static boolean shaderActive = false;
    private static ResourceLocation activeShader = null;
    private static boolean desaturateLoaded = false;
    private static boolean laughTriggered = false;
    private static int laughGlitchTicks = 0;

    private static int alarmLoopTimer = 0;
    private static int alarmCycleCount = 0;

    private static boolean mouseLockedHardware = false;

    // Скример
    private static boolean voidScreamerActive = false;
    private static int voidScreamerTicks = 0;
    private static ResourceLocation screamerFace = HORROR_FACE;

    // Кровь на экране в 1-м этапе кат-сцены
    private static float bloodScreenAlpha = 0.0f;

    // 3-секундный глитч кровати
    private static int bedGlitchTicks = 0;

    // Ночное небо Harvest Moon
    private static boolean insomniaSkyActive = false;

    // Токсичное небо (Акт 2: кислотный дождь / ядовитая луна)
    private static boolean toxicSkyActive = false;

    // Проклятия: 1 Пелена Бездны, 3 Цифровая Аритмия (сервер синкнет через curse_sync)
    private static int activeCurse = 0;
    private static int arrhythmiaNext = 120;
    private static int arrhythmiaBeats = 0;
    private static int arrhythmiaBeatDelay = 0;

    // Акт 4: "мыло" на глазах (0..0.8) и небо Космоса
    private static float visionSoap = 0.0F;
    private static boolean kosmosSkyActive = false;

    // Акт 8: Безлуние — ни луны, ни солнца, ни звёзд, тишина
    private static boolean noneMoonActive = false;

    // Акт 9: кровавая луна + срыв управления
    private static boolean bloodMoonActive = false;
    private static int controlGlitchTicks = 0;

    // Акт 7: аварийный сброс
    private static int bsodTicks = 0;
    private static long bsodStartMs = 0;

    // Акт 9: зацикленная музыка боссфайта
    private static SoundInstance bossMusicInstance = null;
    private static int bossMusicStage = 0;

    // Акт 4: тема чёрной дыры в Космосе
    private static boolean kosmosMusicWanted = false;
    private static SoundInstance kosmosMusicInstance = null;
    private static long kosmosMusicLastCheck = 0;

    // Акт 9: глобальный босс-бар (этап + сегменты глобального HP)
    private static int bossGlobalStage = 0;      // 0 = бар скрыт
    private static int bossGlobalSegments = 3;

    // Атмосфера карманного измерения (The Broken Script)
    private static boolean inPocketDimensionClient = false;
    private static boolean pocketSilencePhase = false;
    private static SoundInstance activePocketAmbient = null;

    public static boolean isMouseLocked() {
        return mouseLockedHardware;
    }

    public static boolean isInsomniaSkyActive() {
        return insomniaSkyActive;
    }

    /** Активна ли сейчас сюжетная кат-сцена (для глушения мобов и прочей атмосферы). */
    public static boolean isCutsceneActive() {
        return activeCutsceneState != 0 && remainingTicks > 0;
    }

    public static void setInsomniaSkyActive(boolean active) {
        insomniaSkyActive = active;
    }

    public static boolean isToxicSkyActive() {
        return toxicSkyActive;
    }

    public static void setToxicSkyActive(boolean active) {
        toxicSkyActive = active;
    }

    public static void setActiveCurse(int curse) {
        activeCurse = curse;
    }

    public static void setVisionSoap(float soap) {
        visionSoap = Math.max(0.0F, Math.min(1.0F, soap));
    }

    public static boolean isVisionSoaped() {
        return visionSoap > 0.01F;
    }

    public static boolean isKosmosSkyActive() {
        return kosmosSkyActive;
    }

    public static void setKosmosSkyActive(boolean active) {
        kosmosSkyActive = active;
    }

    public static boolean isNoneMoonActive() {
        return noneMoonActive;
    }

    public static void setNoneMoonActive(boolean active) {
        noneMoonActive = active;
    }

    /** Смена акта (снапшот): гасим ВСЕ транзиент-эффекты прошлого акта —
     * BSOD-оверлей, скримеры, глитчи, кат-сцены. Наслоение невозможно. */
    public static void resetTransients() {
        bsodTicks = 0;
        voidScreamerActive = false;
        voidScreamerTicks = 0;
        controlGlitchTicks = 0;
        bedGlitchTicks = 0;
        activeCutsceneState = 0;
        remainingTicks = 0;
        totalDurationTicks = 0;
        bloodScreenAlpha = 0.0f;
        cinematicText = "";
        cinematicStartMs = 0L;
        alarmCycleCount = 0;
        alarmLoopTimer = 0;
        laughGlitchTicks = 0;
        desaturateLoaded = false;
        shaderActive = false;
        activeShader = null;
        pocketSilencePhase = false;
        inPocketDimensionClient = false;
        stopPocketAmbientInstance(Minecraft.getInstance());
    }

    /** Глобальный босс-бар: stage 0 = скрыть. */
    public static void setBossGlobal(int stage, int segments) {
        bossGlobalStage = stage;
        bossGlobalSegments = segments;
    }

    public static boolean isBloodMoonActive() {
        return bloodMoonActive;
    }

    public static void setBloodMoonActive(boolean active) {
        bloodMoonActive = active;
    }

    /** Тема чёрной дыры: играет ЦЕЛИКОМ (структура сонга не ломается), после конца — мягкий повтор. */
    public static void playKosmosMusic(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        kosmosMusicWanted = on;
        if (!on) {
            if (kosmosMusicInstance != null) {
                mc.getSoundManager().stop(kosmosMusicInstance);
                kosmosMusicInstance = null;
            }
            return;
        }
        startKosmosTheme(mc);
    }

    private static void startKosmosTheme(Minecraft mc) {
        if (mc.level == null) return;
        kosmosMusicInstance = new SimpleSoundInstance(
                ModSounds.ANYA_KOSMOS_THEME_ID, SoundSource.MUSIC, 0.85F, 1.0F, mc.level.random, false,
                0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
        mc.getSoundManager().play(kosmosMusicInstance);
    }

    /** Мягкое «зацикливание»: трек доиграл — пауза тишины 3 сек — снова с начала. */
    private static void tickKosmosMusic(Minecraft mc) {
        if (!kosmosMusicWanted) return;
        // вышли из Космоса (смерть/телепорт) — глушим
        if (mc.player == null || !mc.player.level().dimension().location().getPath().contains("kosmos")) {
            kosmosMusicWanted = false;
            if (kosmosMusicInstance != null) {
                mc.getSoundManager().stop(kosmosMusicInstance);
                kosmosMusicInstance = null;
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - kosmosMusicLastCheck < 1000) return;
        kosmosMusicLastCheck = now;
        if (kosmosMusicInstance == null || !mc.getSoundManager().isActive(kosmosMusicInstance)) {
            kosmosMusicInstance = null;
            startKosmosTheme(mc);
        }
    }

    /** Музыка боссфайта по этапам: этап не сменится, пока не пробит HP-бар фазы.
     * Луп бесшовный (static + AL_LOOPING), ватчдог страхует после дисконнекта. */
    public static void playBossMusic(int stage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        if (bossMusicStage == stage && bossMusicInstance != null
                && mc.getSoundManager().isActive(bossMusicInstance)) return;
        bossMusicStage = stage;
        if (bossMusicInstance != null) {
            mc.getSoundManager().stop(bossMusicInstance);
            bossMusicInstance = null;
        }
        if (stage <= 0) return;

        net.minecraft.resources.ResourceLocation id = switch (stage) {
            case 2 -> ModSounds.ANYA_BOSS_STAGE_2_ID;
            case 3 -> ModSounds.ANYA_BOSS_STAGE_3_ID;
            default -> ModSounds.ANYA_BOSS_STAGE_1_ID;
        };
        if (mc.level == null) return;
        bossMusicInstance = new SimpleSoundInstance(
                id, SoundSource.MUSIC, 0.9F, 1.0F, mc.level.random, true,
                0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
        mc.getSoundManager().play(bossMusicInstance);
    }

    public static void startPocketAudio() {
        Minecraft mc = Minecraft.getInstance();
        inPocketDimensionClient = true;
        pocketSilencePhase = false;

        if (mc.getSoundManager() != null) {
            // Звук входа в измерение
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.POCKET_ENTER, 1.2F, 1.0F));

            // Зацикленный стриминговый эмбиент
            stopPocketAmbientInstance(mc);
            if (mc.level != null) {
                activePocketAmbient = new SimpleSoundInstance(
                        ModSounds.POCKET_AMBIENT_ID, SoundSource.AMBIENT,
                        1.0F, 1.0F, mc.level.random, true,
                        0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
                mc.getSoundManager().play(activePocketAmbient);
            }
        }
    }

    public static void stopPocketAudio() {
        Minecraft mc = Minecraft.getInstance();
        pocketSilencePhase = true;
        stopPocketAmbientInstance(mc);
    }

    private static void stopPocketAmbientInstance(Minecraft mc) {
        if (mc.getSoundManager() != null) {
            if (activePocketAmbient != null) {
                mc.getSoundManager().stop(activePocketAmbient);
                activePocketAmbient = null;
            }
            mc.getSoundManager().stop(ModSounds.POCKET_AMBIENT_ID, null);
            mc.getSoundManager().stop(ModSounds.POCKET_ENTER_ID, null);
        }
    }

    public static void playVoiceWhisper(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            var sound = switch (index) {
                case 1 -> ModSounds.ANYA_WHISPER_1;
                case 2 -> ModSounds.ANYA_WHISPER_2;
                case 3 -> ModSounds.ANYA_WHISPER_3;
                default -> ModSounds.ANYA_WHISPER_4;
            };
            // Воспроизводим прямо в уши через UI-канал без затухания
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 1.0F));
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

        // Голосовые шёпоты
        if (stateId >= 991 && stateId <= 994) {
            playVoiceWhisper(stateId - 990);
            return;
        }

        if (stateId == 999) {
            triggerVoidScreamer(0);
            return;
        }

        if (stateId == 995) {
            triggerVoidScreamer(-1);
            return;
        }

        if (stateId == 998) {
            triggerBedGlitch();
            return;
        }

        // 988: срыв управления на боссфайте — камеру крутит
        if (stateId == 988) {
            controlGlitchTicks = durationTicks;
            return;
        }

        // 990: предупреждение перед BSOD — открываем экран с кнопкой [Я ГОТОВ]
        if (stateId == 990) {
            Minecraft.getInstance().setScreen(new BSODWarningScreen());
            return;
        }

        // 989: сам Синий Экран Смерти
        if (stateId == 989) {
            bsodTicks = durationTicks;
            bsodStartMs = System.currentTimeMillis();
            // Канон: звук зависшего драйвера
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(ModSounds.ANYA_ALARM, 0.5F, 1.8F));
            return;
        }

        // 986: музыка боссфайта по этапам (stage 1/2/3 — включить, 0 — выключить)
        if (stateId == 986) {
            playBossMusic(durationTicks);
            return;
        }

        // 987: тема чёрной дыры в Космосе (1 — включить, 0 — выключить)
        if (stateId == 987) {
            playKosmosMusic(durationTicks == 1);
            return;
        }

        // Акт X.5 «Осколок памяти»: кинематографичные полосы, мышь НЕ захвачена
        if (stateId == 900) {
            activeCutsceneState = 900;
            remainingTicks = durationTicks;
            totalDurationTicks = durationTicks;
            mouseLockedHardware = false;
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
        activeShader = null;
        desaturateLoaded = false;
        laughTriggered = false;
        laughGlitchTicks = 0;
        alarmLoopTimer = 0;
        alarmCycleCount = 0;
        voidScreamerActive = false;
        voidScreamerTicks = 0;
        screamerFace = HORROR_FACE;
        bloodScreenAlpha = 0.0f;

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

    public static void triggerVoidScreamer(int variant) {
        voidScreamerActive = true;
        voidScreamerTicks = 40;
        Minecraft mc = Minecraft.getInstance();

        int v = variant >= 0 ? Math.min(variant, SCREAMER_FACES.length - 1)
                : (mc.level != null ? mc.level.random.nextInt(SCREAMER_FACES.length) : 0);
        screamerFace = SCREAMER_FACES[v];

        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_ROAR, 1.5F, 1.25F));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_ALARM, 1.4F + RND.nextFloat() * 0.4F, 1.6F));
        if (v % 2 == 1) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_LAUGH, 1.2F, 0.8F));
        }
    }

    public static float getFovMultiplier() {
        return currentFovMultiplier;
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();

        // В измерении The Broken Script можно только идти — бег заблокирован
        if (inPocketDimensionClient && mc.player != null) {
            mc.options.keySprint.setDown(false);
            mc.player.setSprinting(false);
        }

        // Тема чёрной дыры: авто-повтор и стоп при выходе из Космоса
        tickKosmosMusic(mc);

        // Цифровая Аритмия: сбивчивый хаотичный пульс («тук... тук-тук... ТУК!»)
        if (activeCurse == 3 && mc.level != null) {
            if (arrhythmiaBeats > 0) {
                if (--arrhythmiaBeatDelay <= 0) {
                    float pitch = (arrhythmiaBeats % 2 == 0) ? 1.45F + RND.nextFloat() * 0.2F : 0.7F + RND.nextFloat() * 0.2F;
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, pitch));
                    arrhythmiaBeats--;
                    arrhythmiaBeatDelay = 5 + RND.nextInt(5);
                }
            } else if (--arrhythmiaNext <= 0) {
                arrhythmiaBeats = 1 + RND.nextInt(3);
                arrhythmiaBeatDelay = 0;
                arrhythmiaNext = 160 + RND.nextInt(260);
            }
        }

        if (mc.level == null || (mc.player != null && !mc.player.level().dimension().location().getPath().contains("pocket"))) {
            inPocketDimensionClient = false;
            pocketSilencePhase = false;
            stopPocketAmbientInstance(mc);
        }

        if (voidScreamerTicks > 0) {
            voidScreamerTicks--;
            if (voidScreamerTicks <= 0) voidScreamerActive = false;
        }

        if (bedGlitchTicks > 0) {
            bedGlitchTicks--;
        }

        // Срыв управления: камера уходит в штопор
        if (controlGlitchTicks > 0) {
            controlGlitchTicks--;
            if (mc.player != null) {
                mc.player.setYRot(mc.player.getYRot() + 7.0F);
                mc.player.setXRot(Mth.clamp(mc.player.getXRot() + (RND.nextFloat() - 0.5F) * 6.0F, -90.0F, 90.0F));
            }
        }

        if (laughGlitchTicks > 0) {
            laughGlitchTicks--;
        }

        // Акт 7: BSOD держится ~20 секунд, затем игра закрывается
        if (bsodTicks > 0) {
            bsodTicks--;
            if (bsodTicks == 0) {
                mc.stop();
                return;
            }
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
            // 1 ЭТАП: В ГЛАЗА АНИ
            if (elapsed < 440) {
                mouseLockedHardware = true;

                // «Аня закрывает лицо руками»: редкие всхлипы за кадром.
                // ВАЖНО: стоят далеко друг от друга и от стартового всхлипа,
                // чтобы файл успевал доиграть и звук не накладывался сам на себя.
                if (elapsed == 240 || elapsed == 380) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_SOB, 1.0F, 0.8F));
                }

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
                // 2 ЭТАП: ВЗГЛЯД В ПОЛ
                mouseLockedHardware = true;
                if (mc.player != null) {
                    mc.player.setXRot(89.5F);
                    if (elapsed >= 470 && elapsed < 700) {
                        mc.player.setYRot(mc.player.getYRot() + (RND.nextFloat() - 0.5f) * 2.2f);
                    }
                }
            }

            // 10-я секунда: дальтонизм
            if (elapsed >= 200 && elapsed < 300 && !desaturateLoaded) {
                desaturateLoaded = true;
                loadShader(mc, DESATURATE_SHADER);
            }
            if (elapsed >= 200) {
                bloodScreenAlpha = Math.min(1.0f, bloodScreenAlpha + 0.012f);
            }

            // 15-я секунда: кровавый шейдер
            if (elapsed >= 300 && activeShader != BLOOD_SHADER) {
                loadShader(mc, BLOOD_SHADER);
                alarmLoopTimer = 0;
            }

            // 20-я секунда: смешок
            if (elapsed >= 400 && !laughTriggered) {
                laughTriggered = true;
                laughGlitchTicks = 8;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ANYA_LAUGH, 1.1F, 0.9F));
                targetFovMultiplier = 0.82F;
            }

            // 22-я секунда: сирена
            if (elapsed >= 440) {
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

            // Пульс
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
                activeShader = shader;
            } catch (Throwable ignored) {}
        }
    }

    private static void shutdownShader(Minecraft mc) {
        if (shaderActive && mc.gameRenderer != null) {
            try {
                mc.gameRenderer.shutdownEffect();
            } catch (Throwable ignored) {}
            shaderActive = false;
            activeShader = null;
        }
    }

    public static void render(GuiGraphics graphics, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();


        // 0. АКТА 7: СИНИЙ ЭКРАН СМЕРТИ
        if (bsodTicks > 0) {
            graphics.fill(0, 0, width, height, 0xFF0007A0);
            long t = (System.currentTimeMillis() - bsodStartMs) / 100;
            graphics.drawString(mc.font, ":(", width / 2 - 30, height / 3, 0xFFFFFF, false);
            graphics.drawString(mc.font, "На вашем ПК возникла проблема, и его необходимо перезагрузить.", width / 6, height / 3 + 40, 0xFFFFFF, false);
            graphics.drawString(mc.font, "Код остановки: ANYA_EROSION_404", width / 6, height / 3 + 60, 0xFFFFFF, false);
            graphics.drawString(mc.font, "Что было сломано: anya_core.sys", width / 6, height / 3 + 78, 0xFFFFFF, false);
            if (t % 3 == 0) {
                graphics.fill(0, RND.nextInt(height), width, RND.nextInt(height), 0x30FFFFFF);
            }
            // Канон: лицо Ани проступает сквозь синий экран
            long facePhase = (System.currentTimeMillis() - bsodStartMs) / 700;
            if (facePhase % 4 == 3) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.22F);
                int fs = (int) (Math.min(width, height) * 0.5F);
                graphics.blit(HORROR_FACE, width / 2 - fs / 2 + RND.nextInt(10) - 5,
                        height / 2 - fs / 2 + RND.nextInt(10) - 5, fs, fs, fs, fs);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            return;
        }

        // 1. СКРИМЕР
        if (voidScreamerActive && voidScreamerTicks > 0) {
            graphics.fill(0, 0, width, height, 0xEE50000A);

            int jitterX = RND.nextInt(26) - 13;
            int jitterY = RND.nextInt(26) - 13;

            int maxAvailableHeight = height - 28;
            int faceSize = (int) (Math.min(width, maxAvailableHeight) * 0.90f);

            int fx = (width - faceSize) / 2 + jitterX;
            int fy = (maxAvailableHeight - faceSize) / 2 + jitterY;

            graphics.blit(screamerFace, fx, fy, 0, 0, faceSize, faceSize, faceSize, faceSize);
            return;
        }

        // 1.5. АКТ 9: ГЛОБАЛЬНЫЙ БОСС-БАР (стиль Anime Vanguards: имя сверху, орнатная рамка, сегменты)
        if (bossGlobalStage > 0 && bossGlobalSegments > 0 && !voidScreamerActive && mc.player != null) {
            int bw = Math.max(220, (int) (width * 0.5F));
            int bh = 10;
            int bx = (width - bw) / 2;
            int by = 32; // ниже ванильного босс-бара этапа

            graphics.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, 0xE01A0606);
            // орнатная золотисто-алая рамка
            graphics.fill(bx - 3, by - 3, bx + bw + 3, by - 2, 0xFFC9A227);
            graphics.fill(bx - 3, by + bh + 2, bx + bw + 3, by + bh + 3, 0xFFC9A227);
            graphics.fill(bx - 3, by - 3, bx - 2, by + bh + 3, 0xFFC9A227);
            graphics.fill(bx + bw + 2, by - 3, bx + bw + 3, by + bh + 3, 0xFFC9A227);
            // 3 сегмента глобального HP
            int segW = (bw - 4) / 3;
            for (int i = 0; i < 3; i++) {
                int x0 = bx + 2 + i * segW + (i > 0 ? 1 : 0);
                int x1 = bx + 2 + (i + 1) * segW - (i < 2 ? 1 : 0);
                boolean alive = i < bossGlobalSegments;
                graphics.fill(x0, by, x1, by + bh, alive ? 0xFFC81E1E : 0xFF300A0A);
                if (alive) {
                    graphics.fill(x0, by, x1, by + 2, 0xFFFF6A6A); // блик сверху
                }
            }
            graphics.drawCenteredString(mc.font, "§4§lВОПЛОЩЕНИЕ СБОЯ", width / 2, by - 12, 0xFFFFFF);
            graphics.drawCenteredString(mc.font, "§cСБОЙ " + bossGlobalSegments + "/3", width / 2, by + bh + 3, 0xFFFFFF);
        }

        // 2. ГЛИТЧ КРОВАТИ
        if (bedGlitchTicks > 0) {            int glitchSeed = (int) (System.currentTimeMillis() / 45);
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

        // 3. АТМОСФЕРА THE BROKEN SCRIPT (Туннельная тьма)
        if (inPocketDimensionClient) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ZERO,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );

            float pocketDarkness = pocketSilencePhase ? 0.98f : 0.90f;
            float b = 1.0f - pocketDarkness;
            graphics.setColor(b, b, b, 1.0F);
            graphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();

            for (int y = 0; y < height; y += 4) {
                graphics.fill(0, y, width, y + 1, 0x14000000);
            }
        }

        // ПЕЛЕНА БЕЗДНЫ: зрение срезано на ~50% (постоянная густая виньетка)
        if (activeCurse == 1 && mc.player != null) {            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ZERO,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            float veilBrightness = 0.5f;
            graphics.setColor(veilBrightness, veilBrightness, veilBrightness, 1.0F);
            graphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();
        }

        // АКТ 4: "мыло" от бессонницы — чем сильнее, тем мутнее мир
        if (visionSoap > 0.01F && mc.player != null) {
            int a = (int) (visionSoap * 150.0F);
            graphics.fill(0, 0, width, height, (a << 24) | 0x00C8C8D2);
            // тёмная кайма, когда зрение почти ушло
            if (visionSoap >= 0.6F) {
                int edge = (int) ((visionSoap - 0.6F) / 0.2F * 120.0F);
                graphics.fill(0, 0, width, (int) (height * 0.12), (edge << 24) | 0x00000000);
                graphics.fill(0, (int) (height * 0.88), width, height, (edge << 24) | 0x00000000);
            }
        }

        // КИНОМАТОГРАФ: рисуется здесь (проверено живым рендером — работает именно в этой точке)
        renderCinematic(graphics);
        renderActFade(graphics);

        if (remainingTicks <= 0 || activeCutsceneState == 0) return;

        int elapsed = totalDurationTicks - remainingTicks;
        float masterAlpha = Math.min(1.0f, (float) elapsed / 60.0f);

        // Акт X.5: тёмные кинематографичные полосы сверху и снизу
        if (activeCutsceneState == 900) {
            int bar = (int) (height * 0.09f);
            graphics.fill(0, 0, width, bar, 0xF2000000);
            graphics.fill(0, height - bar, width, height, 0xF2000000);
            return;
        }

        if (activeCutsceneState == 3) {
            float vignetteDarkness = 0.95f * masterAlpha;
            float brightness = Math.max(0.0f, 1.0f - vignetteDarkness);

            // «Аня закрывает лицо руками» — пульсирующая тьма (1 этап кат-сцены)
            if (elapsed < 440) {
                double phase = (System.currentTimeMillis() % 1600) / 1600.0;
                float cover = (float) (0.16 + 0.13 * Math.sin(phase * Math.PI * 2)) * masterAlpha;
                graphics.fill(0, 0, width, height, (int) (cover * 255.0f) << 24);
            }

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

            // Кровь на экране (1 этап)
            if (bloodScreenAlpha > 0.01f) {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.ZERO,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                );

                float g = 1.0f - 0.75f * bloodScreenAlpha;
                graphics.setColor(1.0f, g, g, 1.0f);
                graphics.blit(VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, width, height, width, height);
                graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.defaultBlendFunc();

                long dripSeed = System.currentTimeMillis() / 400;
                Random dripRand = new Random((int) dripSeed);
                int dripCount = 4 + (int) (9 * bloodScreenAlpha);
                int maxLen = (int) (height * 0.5f * bloodScreenAlpha);
                int dripAlpha = (int) (150 * bloodScreenAlpha);
                for (int i = 0; i < dripCount; i++) {
                    int dx = dripRand.nextInt(width);
                    int dw = 2 + dripRand.nextInt(5);
                    int dh = maxLen > 0 ? dripRand.nextInt(maxLen) : 0;
                    graphics.fill(dx, 0, dx + dw, dh, (dripAlpha << 24) | 0x005E000A);
                }
            }

            if (redFlashAlpha > 0.01f) {
                int redA = (int) (redFlashAlpha * masterAlpha * 255.0f);
                int redColor = (redA << 24) | 0x005E000A;
                graphics.fill(0, 0, width, height, redColor);
            }

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

    // ================= КИНОМАТОГРАФ =================
    // Letterbox-полосы + субтитры снизу экрана: весь сюжетный текст (Голос Истины,
    // подписи сцен) идёт КИНО, а не строками в чате. Тайминги — в миллисекундах.

    private static String cinematicText = "";
    private static long cinematicStartMs = 0L;
    private static long cinematicDurationMs = 0L;
    private static long actFadeStartMs = 0L;
    private static final long ACT_FADE_MS = 2500L;

    public static void showCinematic(String text, int ticks) {
        net.legoman.animod.AniMod.LOGGER.info("[Cinema] Клиент получил субтитры: {}", text.substring(0, Math.min(40, text.length())));
        cinematicText = text;
        cinematicStartMs = System.currentTimeMillis();
        cinematicDurationMs = Math.max(1000, ticks * 50L);
    }

    /** Кинематографичный fade при смене акта (пакет STORY_ACT). */
    public static void triggerActFade() {
        actFadeStartMs = System.currentTimeMillis();
    }

    private static void renderCinematic(GuiGraphics g) {
        if (cinematicText.isEmpty() || cinematicStartMs == 0L) return;
        long now = System.currentTimeMillis();
        long elapsed = now - cinematicStartMs;
        if (elapsed >= cinematicDurationMs) {
            cinematicText = "";
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // плавное появление/уход по 0.5с
        float fadeIn = Math.min(1.0F, elapsed / 500.0F);
        long remain = cinematicDurationMs - elapsed;
        float fadeOut = Math.min(1.0F, remain / 500.0F);
        float alphaF = Math.min(fadeIn, fadeOut);

        // letterbox-полосы
        int bar = 46;
        int barAlpha = (int) (alphaF * 200.0F) << 24;
        g.fill(0, 0, width, bar, barAlpha);
        g.fill(0, height - bar, width, height, barAlpha);

        // субтитры: перенос по словам на ширину ~78% экрана
        var font = mc.font;
        int maxW = (int) (width * 0.78F);
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : cinematicText.split(" ")) {
            String probe = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(probe) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(probe);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());

        int lineH = 11;
        int baseY = height - bar - 8 - lines.size() * lineH;
        int textColor = 0xFFFFFF | ((int) (alphaF * 255.0F) << 24);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // ручное центрирование (drawCenteredString в HUD-слое вёл себя криво)
            g.drawString(font, line, width / 2 - font.width(line) / 2, baseY + i * lineH, textColor);
        }
    }

    /** Чёрный fade при смене акта: 1.25с в чёрный, 1.25с обратно. */
    private static void renderActFade(GuiGraphics g) {
        if (actFadeStartMs == 0L) return;
        long elapsed = System.currentTimeMillis() - actFadeStartMs;
        if (elapsed >= ACT_FADE_MS) {
            actFadeStartMs = 0L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        float f = elapsed < ACT_FADE_MS / 2 ? elapsed / (ACT_FADE_MS / 2.0F) : (ACT_FADE_MS - elapsed) / (ACT_FADE_MS / 2.0F);
        int a = (int) (Math.min(1.0F, f) * 255.0F) << 24;
        g.fill(0, 0, width, height, a);
    }
}