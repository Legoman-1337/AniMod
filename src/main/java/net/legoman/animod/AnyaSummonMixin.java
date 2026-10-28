package net.legoman.animod.mixin;

import net.legoman.animod.AniMod;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SummonCommand.class)
public abstract class AnyaSummonMixin {
    @Inject(method = "spawnEntity", at = @At("HEAD"))
    private static void animod$beginAnyaSummon(
            CommandSourceStack source,
            Holder.Reference<EntityType<?>> entityType,
            Vec3 position,
            CompoundTag tag,
            boolean initialize,
            CallbackInfoReturnable<Integer> cir
    ) {
        AniMod.beginVanillaAnyaSummon();
    }

    @Inject(method = "createEntity", at = @At("RETURN"))
    private static void animod$rememberCreatedAnya(
            CommandSourceStack source,
            Holder.Reference<EntityType<?>> entityType,
            Vec3 position,
            CompoundTag tag,
            boolean initialize,
            CallbackInfoReturnable<Entity> cir
    ) {
        if (entityType.value() == net.legoman.animod.AnyaEntity.ANYA) {
            AniMod.rememberCreatedAnya(cir.getReturnValue());
        }
    }

    @Inject(method = "spawnEntity", at = @At("RETURN"))
    private static void animod$removePreviousAfterSummon(
            CommandSourceStack source,
            Holder.Reference<EntityType<?>> entityType,
            Vec3 position,
            CompoundTag tag,
            boolean initialize,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (entityType.value() == net.legoman.animod.AnyaEntity.ANYA) {
            AniMod.finishVanillaAnyaSummon(source.getLevel(), cir.getReturnValue() > 0);
        }
    }
}
