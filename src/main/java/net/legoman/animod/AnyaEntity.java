package net.legoman.animod;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AnyaEntity extends Ghast implements GeoEntity {
    public static final EntityType<AnyaEntity> ANYA = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            new ResourceLocation("animod", "anya"),
            EntityType.Builder.of(AnyaEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 1.0F)
                    .build("anya")
    );

    private static final EntityDataAccessor<Integer> REACTION_TICKS =
            SynchedEntityData.defineId(AnyaEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> REACTION_TYPE =
            SynchedEntityData.defineId(AnyaEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public final AnyaGifts gifts = new AnyaGifts(this);

    private int visualGiftTicks = 0;
    private int complaintCooldown = 12000;
    private int eatCooldown = 0;

    public AnyaEntity(EntityType<? extends AnyaEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            AniSaveData.getServerState(serverLevel).updateAnyaLocation(this);
        }
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(REACTION_TICKS, 0);
        entityData.define(REACTION_TYPE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AnyaFollowGoal(this));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    public boolean isCryingStage() {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
            return AniSaveData.getServerState(sl).storyStateId == 3;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide && tickCount % 20 == 0 && level() instanceof ServerLevel serverLevel) {
            AniSaveData.getServerState(serverLevel).updateAnyaLocation(this);
        }

        if (!level().isClientSide && getReactionTicks() > 0) {
            int ticks = getReactionTicks() - 1;
            entityData.set(REACTION_TICKS, ticks);
            if (ticks <= 0) {
                entityData.set(REACTION_TYPE, 0);
            }
        }

        if (!level().isClientSide) {
            // Реалистичные слёзы: деликатные прозрачные капли воды, формирующиеся у глаз и стекающие вниз
            if (isCryingStage() && this.level() instanceof ServerLevel sl) {
                if (this.tickCount % 6 == 0) {
                    double offsetX = (this.random.nextDouble() - 0.5) * 0.22;
                    double offsetZ = (this.random.nextDouble() - 0.5) * 0.22;
                    sl.sendParticles(ParticleTypes.DRIPPING_WATER,
                            this.getX() + offsetX, this.getY() + 0.65, this.getZ() + offsetZ, 1, 0.01, 0.01, 0.01, 0.0);
                }
                if (this.tickCount % 10 == 0) {
                    double offsetX = (this.random.nextDouble() - 0.5) * 0.22;
                    double offsetZ = (this.random.nextDouble() - 0.5) * 0.22;
                    sl.sendParticles(ParticleTypes.FALLING_WATER,
                            this.getX() + offsetX, this.getY() + 0.60, this.getZ() + offsetZ, 1, 0.01, 0.01, 0.01, 0.01);
                }
            }

            if (visualGiftTicks > 0) {
                visualGiftTicks--;
                if (visualGiftTicks == 0) {
                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }

            if (this.tickCount % 20 == 0) {
                if (complaintCooldown > 0) {
                    complaintCooldown -= 20;
                } else {
                    double penalty = gifts.calculateEncumbrancePenalty();
                    if (penalty >= 60.0) {
                        sendEncumbranceComplaint();
                        complaintCooldown = 12000 + this.random.nextInt(6000);
                    }
                }

                if (this.getHealth() < this.getMaxHealth() * 0.5f) {
                    if (eatCooldown > 0) {
                        eatCooldown -= 20;
                    } else {
                        tryConsumeFood();
                    }
                }
            }
        }
    }

    public void showVisualGift(ItemStack stack) {
        this.setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
        this.visualGiftTicks = 100;
    }

    private void sendEncumbranceComplaint() {
        Player nearest = this.level().getNearestPlayer(this, 16.0);
        if (nearest instanceof ServerPlayer sp) {
            int band = AnyaAIPrompts.getKarmaBand(KarmaManager.get());
            String msg = switch (band) {
                case 0 -> "...Слишком... тяжело...";
                case 1 -> "Я тебе что, вьючный мул?! Сам тащи свои булыжники!";
                case 2 -> "Эй! У меня спина отваливается это нести... Б-бака.";
                case 3 -> "Уф... тяжело... но я донесу, не волнуйся...";
                case 4 -> "Фух... Непросто нести столько вещей, но я справлюсь ради нас!";
                case 5 -> "Ради тебя я понесу даже гору, мой милый... Лишь бы ты был рядом.";
                default -> "Тяжело нести...";
            };
            sp.sendSystemMessage(Component.literal("§d[Аня] " + msg));
        }
    }

    private void tryConsumeFood() {
        for (int i = 0; i < gifts.mainInventory.getContainerSize(); i++) {
            ItemStack stack = gifts.mainInventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem().isEdible()) {
                net.minecraft.world.food.FoodProperties food = stack.getItem().getFoodProperties();
                if (food != null) {
                    this.heal((float) food.getNutrition());
                    stack.shrink(1);
                    this.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
                    this.eatCooldown = 100;

                    Player nearest = this.level().getNearestPlayer(this, 16.0);
                    if (nearest instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.literal("§7[Система] Аня съела " + stack.getHoverName().getString() + " из своего инвентаря, чтобы подлечиться."));
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return null; }

    @Override
    protected SoundEvent getDeathSound() { return null; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (isReacting()) {
                return state.setAndContinue(RawAnimation.begin().thenPlay("animation.anya.reaction"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop("animation.anya.idle"));
        }));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {

            // СЮЖЕТНАЯ СТАДИЯ СЛЁЗ: звук клика убран
            if (this.level() instanceof ServerLevel sl && AniSaveData.getServerState(sl).storyStateId == 3) {
                player.sendSystemMessage(Component.literal("§8§o*Аня закрыла лицо руками и тихо всхлипывает... не трогай её сейчас*"));
                return InteractionResult.SUCCESS;
            }

            if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
                if (player instanceof ServerPlayer) {
                    player.sendSystemMessage(Component.literal("§c[Аня] Я сейчас отдыхаю. (В мирном режиме инвентари отключены)"));
                }
                return InteractionResult.SUCCESS;
            }

            if (player.isShiftKeyDown()) {
                gifts.openGiftMenu((ServerPlayer) player);
            } else {
                gifts.openMainMenu((ServerPlayer) player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        gifts.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        gifts.load(tag);
    }

    public void triggerReaction(int type) {
        if (type < 1 || type > 2) return;
        entityData.set(REACTION_TYPE, type);
        entityData.set(REACTION_TICKS, 60);
    }

    public boolean isReacting() {
        return getReactionTicks() > 0 && getReactionType() > 0;
    }

    public int getReactionType() {
        return entityData.get(REACTION_TYPE);
    }

    public int getReactionTicks() {
        return entityData.get(REACTION_TICKS);
    }

    public float getReactionProgress(float partialTick) {
        int ticks = getReactionTicks();
        if (ticks <= 0) return 0.0F;
        return 60.0F - ticks + partialTick;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}