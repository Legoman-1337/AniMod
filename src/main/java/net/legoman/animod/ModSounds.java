package net.legoman.animod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
    public static final ResourceLocation ANYA_SOB_ID = new ResourceLocation("animod", "anya_sob");
    public static final SoundEvent ANYA_SOB = SoundEvent.createVariableRangeEvent(ANYA_SOB_ID);

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, ANYA_SOB_ID, ANYA_SOB);
    }
}