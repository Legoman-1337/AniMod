package net.legoman.animod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
    public static final ResourceLocation ANYA_SOB_ID = new ResourceLocation("animod", "anya_sob");
    public static final SoundEvent ANYA_SOB = SoundEvent.createVariableRangeEvent(ANYA_SOB_ID);

    public static final ResourceLocation ANYA_LAUGH_ID = new ResourceLocation("animod", "anya_laugh");
    public static final SoundEvent ANYA_LAUGH = SoundEvent.createVariableRangeEvent(ANYA_LAUGH_ID);

    public static final ResourceLocation ANYA_ALARM_ID = new ResourceLocation("animod", "anya_alarm");
    public static final SoundEvent ANYA_ALARM = SoundEvent.createVariableRangeEvent(ANYA_ALARM_ID);

    public static final ResourceLocation POCKET_ENTER_ID = new ResourceLocation("animod", "pocket_enter");
    public static final SoundEvent POCKET_ENTER = SoundEvent.createVariableRangeEvent(POCKET_ENTER_ID);

    public static final ResourceLocation POCKET_AMBIENT_ID = new ResourceLocation("animod", "pocket_ambient");
    public static final SoundEvent POCKET_AMBIENT = SoundEvent.createVariableRangeEvent(POCKET_AMBIENT_ID);

    // Голосовые шёпоты Ани
    public static final ResourceLocation ANYA_WHISPER_1_ID = new ResourceLocation("animod", "anya_whisper_1");
    public static final SoundEvent ANYA_WHISPER_1 = SoundEvent.createVariableRangeEvent(ANYA_WHISPER_1_ID);

    public static final ResourceLocation ANYA_WHISPER_2_ID = new ResourceLocation("animod", "anya_whisper_2");
    public static final SoundEvent ANYA_WHISPER_2 = SoundEvent.createVariableRangeEvent(ANYA_WHISPER_2_ID);

    public static final ResourceLocation ANYA_WHISPER_3_ID = new ResourceLocation("animod", "anya_whisper_3");
    public static final SoundEvent ANYA_WHISPER_3 = SoundEvent.createVariableRangeEvent(ANYA_WHISPER_3_ID);

    public static final ResourceLocation ANYA_WHISPER_4_ID = new ResourceLocation("animod", "anya_whisper_4");
    public static final SoundEvent ANYA_WHISPER_4 = SoundEvent.createVariableRangeEvent(ANYA_WHISPER_4_ID);

    // Музыка боссфайта по фазам (канон: сонг делится на этапы, следующий этап — после пробития HP-бара)
    public static final ResourceLocation ANYA_BOSS_STAGE_1_ID = new ResourceLocation("animod", "anya_boss_stage-1");
    public static final SoundEvent ANYA_BOSS_STAGE_1 = SoundEvent.createVariableRangeEvent(ANYA_BOSS_STAGE_1_ID);

    public static final ResourceLocation ANYA_BOSS_STAGE_2_ID = new ResourceLocation("animod", "anya_boss_stage-2");
    public static final SoundEvent ANYA_BOSS_STAGE_2 = SoundEvent.createVariableRangeEvent(ANYA_BOSS_STAGE_2_ID);

    public static final ResourceLocation ANYA_BOSS_STAGE_3_ID = new ResourceLocation("animod", "anya_boss_stage-3");
    public static final SoundEvent ANYA_BOSS_STAGE_3 = SoundEvent.createVariableRangeEvent(ANYA_BOSS_STAGE_3_ID);

    // Тема чёрной дыры (Космос, Акт 4) — «Sleeping Hummingbird»
    public static final ResourceLocation ANYA_KOSMOS_THEME_ID = new ResourceLocation("animod", "anya_kosmos_theme");
    public static final SoundEvent ANYA_KOSMOS_THEME = SoundEvent.createVariableRangeEvent(ANYA_KOSMOS_THEME_ID);

    // Озвученные реплики ElevenLabs (voice/{N}.ogg) — регистрируются пачкой
    public static final int[] VOICE_IDS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 25, 30, 31, 33, 34, 35, 36, 38, 39, 40};
    private static final java.util.Map<Integer, SoundEvent> VOICES = new java.util.HashMap<>();

    public static SoundEvent getVoice(int id) {
        return VOICES.get(id);
    }

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_SOB_ID, ANYA_SOB);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_LAUGH_ID, ANYA_LAUGH);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_ALARM_ID, ANYA_ALARM);
        Registry.register(BuiltInRegistries.SOUND_EVENT, POCKET_ENTER_ID, POCKET_ENTER);
        Registry.register(BuiltInRegistries.SOUND_EVENT, POCKET_AMBIENT_ID, POCKET_AMBIENT);

        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_WHISPER_1_ID, ANYA_WHISPER_1);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_WHISPER_2_ID, ANYA_WHISPER_2);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_WHISPER_3_ID, ANYA_WHISPER_3);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_WHISPER_4_ID, ANYA_WHISPER_4);

        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_BOSS_STAGE_1_ID, ANYA_BOSS_STAGE_1);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_BOSS_STAGE_2_ID, ANYA_BOSS_STAGE_2);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_BOSS_STAGE_3_ID, ANYA_BOSS_STAGE_3);

        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_KOSMOS_THEME_ID, ANYA_KOSMOS_THEME);

        for (int id : VOICE_IDS) {
            ResourceLocation rl = new ResourceLocation("animod", "voice_" + id);
            VOICES.put(id, SoundEvent.createVariableRangeEvent(rl));
            Registry.register(BuiltInRegistries.SOUND_EVENT, rl, VOICES.get(id));
        }
    }
}