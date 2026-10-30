package net.legoman.animod.story;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AniSaveData;
import net.legoman.animod.KarmaManager;
import net.legoman.animod.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public class StoryManager {
    public static final int TOTAL_CUTSCENE_TICKS = 1100;
    public static final ResourceKey<Level> POCKET_DIMENSION = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("animod", "pocket"));

    private static long lastWorldTime = -1L;
    private static int cutsceneTimer = 0;
    private static UUID victimPlayerUuid = null;

    private static boolean burialInProgress = false;
    private static int burialCenterX = 0;
    private static int burialCenterZ = 0;
    private static int currentDigY = 0;
    private static int digCooldown = 0;

    private static boolean inPocketDimension = false;
    private static int pocketDimensionTicks = 0;

    public static boolean isBurialActive(UUID playerUuid) {
        return (burialInProgress || inPocketDimension) && victimPlayerUuid != null && victimPlayerUuid.equals(playerUuid);
    }

    public static boolean isInsomniaNightActive(Level level) {
        if (level.isClientSide) return false;
        AniSaveData data = AniSaveData.getServerState((ServerLevel) level);
        if (!data.insomniaNightActive) return false;

        long time = level.getDayTime() % 24000L;
        if (time < 12500L || time > 23500L) {
            data.insomniaNightActive = false;
            data.setDirty();
            for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
                syncInsomniaSky(sp, false);
            }
            return false;
        }
        return true;
    }

    public static void syncInsomniaSky(ServerPlayer player, boolean active) {
        if (ServerPlayNetworking.canSend(player, AniMod.INSOMNIA_SKY_PACKET)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(active);
            ServerPlayNetworking.send(player, AniMod.INSOMNIA_SKY_PACKET, buf);
        }
    }

    public static void triggerBedGlitch(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(998); // 3-секундный кошмар
        buf.writeInt(60);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);

        player.sendSystemMessage(Component.literal("§4§l*ТЫ НЕ МОЖЕШЬ УСНУТЬ. ОНО ВСЁ ЕЩЁ СМОТРИТ НА ТЕБЯ ИЗ ТЕМНОТЫ...*"), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.7F);
    }

    public static void startPocketAudio(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(996); // Запуск звука входа и фонового эмбиента
        buf.writeInt(100);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
    }

    public static void stopPocketAudio(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(997); // Мгновенная остановка фонового эмбиента
        buf.writeInt(0);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
    }

    public static void onPlayerRespawn(ServerPlayer newPlayer) {
        if (victimPlayerUuid != null && victimPlayerUuid.equals(newPlayer.getUUID())) {
            stopPocketAudio(newPlayer);
            newPlayer.setInvulnerable(false);
            newPlayer.serverLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, newPlayer.serverLevel().getServer());
            burialInProgress = false;
            inPocketDimension = false;
            cutsceneTimer = 0;
            pocketDimensionTicks = 0;
            victimPlayerUuid = null;

            ServerLevel overworld = newPlayer.serverLevel().getServer().overworld();
            overworld.setDayTime(18000L); // Полночь

            AniSaveData data = AniSaveData.getServerState(overworld);
            data.insomniaNightActive = true;
            data.setDirty();

            syncInsomniaSky(newPlayer, true);
            newPlayer.sendSystemMessage(Component.literal("§4§o*Холодный пот прошиб тебя. Небо пылает медью, и уснуть этой ночью ты не сможешь...*"));
        }
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        AniSaveData data = AniSaveData.getServerState(overworld);

        long currentTime = overworld.getDayTime();

        if (lastWorldTime == -1L) {
            lastWorldTime = currentTime;
            return;
        }

        long dayDiff = (currentTime / 24000L) - (lastWorldTime / 24000L);
        if (dayDiff > 0) {
            lastWorldTime = currentTime;
            advanceStoryDay(server, data, (int) dayDiff);
        } else if (currentTime < lastWorldTime) {
            lastWorldTime = currentTime;
        }

        if (data.insomniaNightActive) {
            long time = overworld.getDayTime() % 24000L;
            if (time < 12500L || time > 23500L) {
                data.insomniaNightActive = false;
                data.setDirty();
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) syncInsomniaSky(sp, false);
            } else if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) syncInsomniaSky(sp, true);
            }
        }

        tickCurrentState(server, data);
    }

    public static void advanceStoryDay(MinecraftServer server, AniSaveData data, int daysToAdd) {
        data.storyDay += daysToAdd;
        data.setDirty();
        applyDayLogic(server, data, data.storyDay);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase("legoman")) {
                player.sendSystemMessage(Component.literal("§8§o*Наступил " + data.storyDay + "-й день вашего пути с Аней...*"));
            }
        }
    }

    public static void applyDayLogic(MinecraftServer server, AniSaveData data, int day) {
        int karma = KarmaManager.get();
        StoryState oldState = data.getStoryState();
        StoryState newState;

        if (day <= 4) {
            newState = StoryState.NORMAL;
            data.anyaEventHp = 20.0;
        } else if (day <= 6) {
            newState = StoryState.ANYA_IS_SCARED;
            data.anyaEventHp = 20.0;
        } else if (day <= 8) {
            newState = StoryState.ANYA_IS_CRYING;
            data.anyaEventHp = 16.0;
        } else if (day <= 13) {
            if (karma >= 50) {
                newState = StoryState.ANYA_IS_WATCHING;
                data.anyaEventHp = 18.0;
            } else {
                newState = StoryState.ANYA_IS_ANGRY;
                data.anyaEventHp = 14.0;
            }
        } else if (day <= 17) {
            newState = StoryState.ANYA_IS_NOT_HERE;
            data.anyaEventHp = 14.0;
        } else if (!data.bossDefeated) {
            newState = StoryState.ANYA_BODY_EVENT;
            data.anyaEventHp = 10.0;
        } else {
            newState = StoryState.STORY_COMPLETED;
            data.anyaEventHp = 20.0;
        }

        data.setStoryState(newState);
        data.setDirty();

        if (oldState != newState) {
            triggerStateTransition(server, data, oldState, newState);
        }
    }

    public static void triggerStateTransition(MinecraftServer server, AniSaveData data, StoryState oldState, StoryState newState) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getName().getString().equalsIgnoreCase("legoman")) continue;

            PlayerStoryHealthManager.applyHealthCap(player, data.anyaEventHp);

            if (newState == StoryState.ANYA_IS_CRYING) {
                cutsceneTimer = TOTAL_CUTSCENE_TICKS;
                victimPlayerUuid = player.getUUID();
                burialInProgress = false;
                inPocketDimension = false;
                pocketDimensionTicks = 0;

                player.setInvulnerable(true);
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, TOTAL_CUTSCENE_TICKS, 0, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, TOTAL_CUTSCENE_TICKS, 0, false, false, false));

                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeInt(newState.getId());
                buf.writeInt(TOTAL_CUTSCENE_TICKS);
                ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);

                player.sendSystemMessage(Component.literal("§8§o*Воздух внезапно стал леденящим... Совсем рядом раздался тихий, безысходный всхлип.*"));
            } else if (newState == StoryState.ANYA_IS_ANGRY) {
                player.sendSystemMessage(Component.literal("§4§o*Пространство вокруг содрогнулось. Что-то в её взгляде изменилось навсегда...*"));
            }
        }
    }

    private static void tickCurrentState(MinecraftServer server, AniSaveData data) {
        StoryState state = data.getStoryState();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getName().getString().equalsIgnoreCase("legoman")) continue;

            PlayerStoryHealthManager.applyHealthCap(player, data.anyaEventHp);

            // 1. АКТИВНАЯ ФАЗА КОШМАРА
            if (cutsceneTimer > 0) {
                cutsceneTimer--;
                int elapsed = TOTAL_CUTSCENE_TICKS - cutsceneTimer;

                player.setInvulnerable(true);
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 255, false, false, false));

                AABB protectBox = player.getBoundingBox().inflate(48.0);
                for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, protectBox)) {
                    if (mob.getTarget() == player) mob.setTarget(null);
                }

                if (elapsed == 440) {
                    server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
                    burialInProgress = true;
                    burialCenterX = player.getBlockX();
                    burialCenterZ = player.getBlockZ();
                    currentDigY = player.getBlockY() - 1;
                    digCooldown = 0;
                }

                // Бурение шахты 3х3 НАВЫЛЕТ В БЕЗДНУ СКВОЗЬ БЕДРОК
                if (elapsed >= 470 && burialInProgress && !inPocketDimension) {
                    player.teleportTo(burialCenterX + 0.5, player.getY(), burialCenterZ + 0.5);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false, false));

                    player.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL,
                            burialCenterX + 0.5, player.getY() - 1.0, burialCenterZ + 0.5, 14, 0.7, 0.3, 0.7, 0.14);
                    player.serverLevel().sendParticles(ParticleTypes.SONIC_BOOM,
                            burialCenterX + 0.5, player.getY() - 2.0, burialCenterZ + 0.5, 1, 0, 0, 0, 0);

                    if (player.getY() < -15 && player.getY() >= -66) {
                        player.setDeltaMovement(0, -0.22, 0);
                    }

                    if (digCooldown > 0) {
                        digCooldown--;
                    } else {
                        digCooldown = 1;
                        int targetY = Math.max(-66, Math.min(currentDigY, player.getBlockY() - 3));
                        for (int y = currentDigY; y >= targetY; y--) {
                            digLayer(player.serverLevel(), burialCenterX, burialCenterZ, y);
                        }
                        currentDigY = targetY - 1;
                    }
                }
            }

            // 2. ОТСЛЕЖИВАНИЕ ПАДЕНИЯ В БЕЗДНУ (работает даже когда кошмар уже закончился!)
            if (burialInProgress && !inPocketDimension) {
                player.setInvulnerable(true);

                // Докапывание ствола шахты до бездны, если игрок еще летит
                if (currentDigY >= -66) {
                    if (digCooldown > 0) {
                        digCooldown--;
                    } else {
                        digCooldown = 1;
                        int targetY = Math.max(-66, currentDigY - 4);
                        for (int y = currentDigY; y >= targetY; y--) {
                            digLayer(player.serverLevel(), burialCenterX, burialCenterZ, y);
                        }
                        currentDigY = targetY - 1;
                    }
                }

                // БЕЗДНА НАЧИНАЕТСЯ НА Y = -64.
                // При падении в бездну (Y <= -65.0) — МОМЕНТАЛЬНЫЙ ТЕЛЕПОРТ В КАРМАННОЕ ИЗМЕРЕНИЕ!
                if (player.getY() <= -65.0) {
                    burialInProgress = false;
                    inPocketDimension = true;
                    pocketDimensionTicks = 0;

                    // Скример во весь экран
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(999);
                    buf.writeInt(40);
                    ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);

                    // Запуск фонового эмбиента и звука входа
                    startPocketAudio(player);

                    // Моментальный перенос в земляной куб 32х32 (без урона пустоты!)
                    teleportToPocketDirtCube(player);
                }
            }

            // 3. 5 СЕКУНД (100 ТИКОВ) В ЗЕМЛЯНОМ КУБЕ В ИЗМЕРЕНИИ (работает независимо от таймера кошмара!)
            if (inPocketDimension) {
                pocketDimensionTicks++;
                player.setInvulnerable(true);
                player.fallDistance = 0.0F;

                if (pocketDimensionTicks % 25 == 0) {
                    player.sendSystemMessage(Component.literal("§8§o*Где я?... Что это за место?...*"), true);
                }

                if (pocketDimensionTicks >= 100) {
                    inPocketDimension = false;

                    // ОСТАНОВКА ЭМБИЕНТА ПОСЛЕ ВЫХОДА
                    stopPocketAudio(player);

                    ServerLevel overworld = server.overworld();
                    BlockPos safeSurface = overworld.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, new BlockPos(burialCenterX, 0, burialCenterZ));
                    int safeY = Math.max(overworld.getMinBuildHeight() + 5, safeSurface.getY() + 1);

                    player.teleportTo(overworld, burialCenterX + 0.5, safeY, burialCenterZ + 0.5, player.getYRot(), player.getXRot());
                    player.setDeltaMovement(0, 0, 0);
                    player.fallDistance = 0.0F;
                    onPlayerRespawn(player);
                }
            }

            if (state == StoryState.ANYA_IS_CRYING && server.getTickCount() % 30 == 0) {
                player.serverLevel().sendParticles(ParticleTypes.FALLING_WATER,
                        player.getX(), player.getY() + 1.8, player.getZ(), 2, 0.3, 0.2, 0.3, 0.01);
            }
        }
    }

    private static void teleportToPocketDirtCube(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        ServerLevel pocket = server.getLevel(POCKET_DIMENSION);

        boolean hasPocket = pocket != null;
        ServerLevel targetLevel = hasPocket ? pocket : server.overworld();

        int originX = 0;
        int originY = hasPocket ? 64 : 220;
        int originZ = 0;
        int size = 32;

        // Предзагрузка чанков для предотвращения задержек
        for (int cx = -1; cx <= 2; cx++) {
            for (int cz = -1; cz <= 2; cz++) {
                targetLevel.getChunk(cx, cz);
            }
        }

        // Построение полого куба 32х32 из земли
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    boolean isWall = (x == 0 || x == size - 1 || y == 0 || y == size - 1 || z == 0 || z == size - 1);
                    BlockPos p = new BlockPos(originX + x, originY + y, originZ + z);
                    targetLevel.setBlock(p, isWall ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        // Моментальный перенос со сбросом скорости и падения
        player.teleportTo(targetLevel, originX + 16.5, originY + 2.0, originZ + 16.5, player.getYRot(), 0.0F);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0.0F;
        player.setHealth(20.0F);
    }

    private static void digLayer(ServerLevel level, int cx, int cz, int y) {
        if (y < -66) return;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                if (level.getBlockEntity(pos) != null) continue;

                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        if (dx == 0 && dz == 0) {
                            level.playSound(null, pos, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
                        }
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                } else {
                    if (!level.getFluidState(pos).isEmpty()) {
                        level.setBlock(pos, (y < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}