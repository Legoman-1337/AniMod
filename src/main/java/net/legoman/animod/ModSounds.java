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

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_SOB_ID, ANYA_SOB);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_LAUGH_ID, ANYA_LAUGH);
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_ALARM_ID, ANYA_ALARM);
        Registry.register(BuiltInRegistries.SOUND_EVENT, POCKET_ENTER_ID, POCKET_ENTER);
        Registry.register(BuiltInRegistries.SOUND_EVENT, POCKET_AMBIENT_ID, POCKET_AMBIENT);
    }
}