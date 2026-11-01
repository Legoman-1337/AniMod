package net.legoman.animod.client.mixin;

import net.legoman.animod.client.StoryScreenOverlay;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    // Во время сюжетных кат-сцен гасим звуки мобов (HOSTILE/NEUTRAL), чтобы овцы,
    // зомби и прочие не портили вайб сцены. Реплики и звуки самой сцены идут через
    // каналы MASTER (UI) и PLAYERS — глушение их не задевает.
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void animod$silenceMobsDuringCutscene(SoundInstance instance, CallbackInfo ci) {
        SoundSource source = instance.getSource();
        // Акт 8: гробовая тишина — животные молчат, музыка заблокирована
        if (StoryScreenOverlay.isNoneMoonActive()
                && (source == SoundSource.NEUTRAL || source == SoundSource.MUSIC || source == SoundSource.RECORDS)) {
            ci.cancel();
            return;
        }
        if (!StoryScreenOverlay.isCutsceneActive()) return;
        if (source == SoundSource.HOSTILE || source == SoundSource.NEUTRAL) {
            ci.cancel();
        }
    }
}
