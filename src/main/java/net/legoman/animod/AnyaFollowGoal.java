package net.legoman.animod;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class AnyaFollowGoal extends Goal {

    private final AnyaEntity anya;
    private final double followDist;
    private final double stayRadius;

    public AnyaFollowGoal(AnyaEntity anya) {
        this(anya, 3.0, 3.0);
    }

    public AnyaFollowGoal(AnyaEntity anya, double followDist, double stayRadius) {
        this.anya = anya;
        this.followDist = followDist;
        this.stayRadius = stayRadius;
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
            return;
        }

        Vec3 target = player.position().add(0.0, 1.2, 0.0);

        double distance = anya.position().distanceTo(target);

        if (distance <= followDist) {
            return;
        }

        Vec3 direction = target
                .subtract(anya.position())
                .normalize()
                .scale(0.20);

        Vec3 movement = anya.getDeltaMovement()
                .scale(0.6)
                .add(direction);

        anya.setDeltaMovement(movement);

        double dx = target.x - anya.getX();
        double dz = target.z - anya.getZ();

        float yaw = (float)
                (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;

        anya.setYRot(yaw);
        anya.setYHeadRot(yaw);
    }

    private Player findPlayer() {
        for (Player player : anya.level().players()) {
            if (player.getName().getString().equals("legoman")) {
                return player;
            }
        }

        return null;
    }
}