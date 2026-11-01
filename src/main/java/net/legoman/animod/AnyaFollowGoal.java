package net.legoman.animod;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AnyaFollowGoal extends Goal {

    private final AnyaEntity anya;
    private final double stopDistance = 2.2;
    private final double slowDistance = 4.0;

    public AnyaFollowGoal(AnyaEntity anya) {
        this.anya = anya;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void tick() {
        Player player = findPlayer();

        if (player == null) {
            anya.setDeltaMovement(anya.getDeltaMovement().scale(0.8));
            return;
        }

        Vec3 target = player.position().add(0.0, 1.3, 0.0);
        double distance = anya.position().distanceTo(target);

        // Если игрок улетел слишком далеко
        if (distance > 48.0) {
            anya.setDeltaMovement(Vec3.ZERO);
            return;
        }

        // Плавный расчет коэффициента скорости (никаких резких скачков 0 -> 0.2)
        double speedFactor = 0.0;
        if (distance > slowDistance) {
            speedFactor = 1.0;
        } else if (distance > stopDistance) {
            // Линейное плавное торможение при приближении к игроку
            speedFactor = (distance - stopDistance) / (slowDistance - stopDistance);
        }

        double totalPenaltyPercent = anya.gifts.calculateEncumbrancePenalty();
        double baseSpeed = 0.18;
        double currentSpeed = baseSpeed * (1.0 - (totalPenaltyPercent / 100.0)) * speedFactor;

        Vec3 direction = target.subtract(anya.position()).normalize().scale(currentSpeed);

        // Мягкое удержание инерции
        Vec3 movement = anya.getDeltaMovement().scale(0.75).add(direction);
        anya.setDeltaMovement(movement);

        // Плавный расчет взгляда на игрока
        double dx = target.x - anya.getX();
        double dz = target.z - anya.getZ();

        if (dx * dx + dz * dz > 0.001) {
            float targetYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            anya.setYRot(targetYaw);
            anya.setYHeadRot(targetYaw);
        }
    }

    private Player findPlayer() {
        // Аня ходит за сюжетным игроком (любой ник, фиксируется при первом входе)
        AniSaveData data = AniSaveData.getServerState((net.minecraft.server.level.ServerLevel) anya.level());
        if (data.storyPlayerUuid != null && !data.storyPlayerUuid.isEmpty()) {
            try {
                java.util.UUID storyUuid = java.util.UUID.fromString(data.storyPlayerUuid);
                for (Player player : anya.level().players()) {
                    if (player.getUUID().equals(storyUuid)) return player;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        // сюжетный игрок ещё не зафиксирован — первый не-наблюдатель рядом
        for (Player player : anya.level().players()) {
            if (!player.isSpectator()) return player;
        }
        return null;
    }
}