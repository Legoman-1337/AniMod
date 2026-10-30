package net.legoman.animod.client.mixin;

import net.legoman.animod.client.StoryScreenOverlay;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void animod$lockMouseHardware(CallbackInfo ci) {
        if (StoryScreenOverlay.isMouseLocked()) {
            ci.cancel(); // 100% аппаратная блокировка движения мыши
        }
    }
}