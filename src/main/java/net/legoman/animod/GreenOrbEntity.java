package net.legoman.animod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Акт 9: ЗЕЛЁНЫЙ ФАЙРБОЛ (канон «Шипастика» из Geometry Dash) — летит в игрока,
 * отбивается мечом обратно в Аню (BossManager.tryDeflect) и пробивает её щит.
 * Движение рулит BossManager (гоминг каждый тик), поэтому свой физический тик отключён.
 */
public class GreenOrbEntity extends AbstractHurtingProjectile {

    public GreenOrbEntity(EntityType<? extends GreenOrbEntity> type, Level level) {
        super(type, level);
    }

    public GreenOrbEntity(Level level, double x, double y, double z, Vec3 velocity) {
        super(AnyaEntity.GREEN_ORB, level);
        moveTo(x, y, z, 0.0F, 0.0F);
        setDeltaMovement(velocity);
    }

    /** Своя кинематика: летим строго по deltaMovement (гоминг задаёт BossManager). */
    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    @Override
    protected boolean shouldBurn() {
        return false; // зелёный огонь ничего не поджигает
    }
}
