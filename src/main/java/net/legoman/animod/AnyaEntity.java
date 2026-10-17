package net.legoman.animod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AnyaEntity extends Ghast implements GeoEntity {
    public static final EntityType<AnyaEntity> ANYA=Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation("animod","anya"),
            EntityType.Builder.of(AnyaEntity::new,MobCategory.CREATURE)
                    .sized(0.5F,1.0F)
                    .build("anya")
    );

    private static final EntityDataAccessor<Integer> REACTION_TICKS=
            SynchedEntityData.defineId(AnyaEntity.class,EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> REACTION_TYPE=
            SynchedEntityData.defineId(AnyaEntity.class,EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);

    public AnyaEntity(EntityType<? extends AnyaEntity> type,Level level){
        super(type,level);
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(){
        super.defineSynchedData();
        entityData.define(REACTION_TICKS,0);
        entityData.define(REACTION_TYPE,0);
    }

    @Override
    protected void registerGoals(){
        goalSelector.addGoal(1,new AnyaFollowGoal(this));
    }

    @Override
    public boolean hurt(DamageSource source,float amount){
        return false;
    }

    @Override
    public void tick(){
        super.tick();

        if(!level().isClientSide && getReactionTicks()>0){
            int ticks=getReactionTicks()-1;
            entityData.set(REACTION_TICKS,ticks);

            if(ticks<=0){
                entityData.set(REACTION_TYPE,0);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound(){
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source){
        return null;
    }

    @Override
    protected SoundEvent getDeathSound(){
        return null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers){
        controllers.add(new AnimationController<>(this,"main",0,state->{
            if(isReacting()){
                return state.setAndContinue(
                        RawAnimation.begin().thenPlay("animation.anya.reaction")
                );
            }

            return state.setAndContinue(
                    RawAnimation.begin().thenLoop("animation.anya.idle")
            );
        }));
    }

    public void triggerReaction(int type){
        if(type<1||type>2) return;
        entityData.set(REACTION_TYPE,type);
        entityData.set(REACTION_TICKS,60);
    }

    public boolean isReacting(){
        return getReactionTicks()>0&&getReactionType()>0;
    }

    public int getReactionType(){
        return entityData.get(REACTION_TYPE);
    }

    public int getReactionTicks(){
        return entityData.get(REACTION_TICKS);
    }

    public float getReactionProgress(float partialTick){
        int ticks=getReactionTicks();
        if(ticks<=0) return 0.0F;
        return 60.0F-ticks+partialTick;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache(){
        return cache;
    }
}