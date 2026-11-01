package net.legoman.animod.story;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AniSaveData;
import net.legoman.animod.AnyaEntity;
import net.legoman.animod.story.BossManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;
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
    private static int pocketScreamerNext = 0;
    private static boolean chestTriggered = false;
    private static boolean pocketStunned = false; // ступор после взятия часов: до выброса из измерения
    private static final Random POCKET_RND = new Random();

    public static final int POCKET_AMBIENT_END_TICKS = 2674;
    public static final int POCKET_TOTAL_STAY_TICKS = 3000;

    // Блокируем клики ТОЛЬКО пока бурится шахта в Оверворлде! В карманном мире сундуки РАБОТАЮТ!
    public static boolean isBurialActive(UUID playerUuid) {
        return burialInProgress && victimPlayerUuid != null && victimPlayerUuid.equals(playerUuid);
    }

    public static boolean isInsomniaNightActive(Level level) {
        if (level.isClientSide) return false;
        AniSaveData data = AniSaveData.getServerState((ServerLevel) level);
        // Бессонница круглосуточна: кровати кошмарят и днём тоже
        return data.insomniaNightActive;
    }

    public static void syncInsomniaSky(ServerPlayer player, boolean active) {
        if (ServerPlayNetworking.canSend(player, AniMod.INSOMNIA_SKY_PACKET)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(active);
            ServerPlayNetworking.send(player, AniMod.INSOMNIA_SKY_PACKET, buf);
        }
    }

    public static void syncToxicSky(ServerPlayer player, boolean active) {
        if (ServerPlayNetworking.canSend(player, AniMod.TOXIC_SKY_PACKET)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(active);
            ServerPlayNetworking.send(player, AniMod.TOXIC_SKY_PACKET, buf);
        }
    }

    public static void syncNoneSky(ServerPlayer player, boolean active) {
        if (ServerPlayNetworking.canSend(player, AniMod.NONE_SKY_PACKET)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(active);
            ServerPlayNetworking.send(player, AniMod.NONE_SKY_PACKET, buf);
        }
    }

    /**
     * ПОЛНЫЙ СНАПШОТ сюжетного состояния → клиенту. Клиент выставляет РОВНО его:
     * любое расхождение (наслоившееся небо, чужой бар, чужая луна) самоликвидируется
     * за ≤5 секунд. Отправляется при входе, на смене акта и раз в 5 секунд каждому.
     */
    public static void sendStorySync(ServerPlayer p, AniSaveData data) {
        int act = data.currentAct;
        boolean blood = false;
        int bossStage = 0;
        int bossSegments = 0;
        if (!data.bossDefeated && data.getStoryState() == StoryState.ANYA_BODY_EVENT) {
            blood = true;
            bossStage = Math.max(1, data.bossStage);
            bossSegments = (int) Math.max(0, data.bossGlobalHp);
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(act);
        buf.writeBoolean(data.chatCoreFixed);
        buf.writeBoolean(data.insomniaNightActive);
        buf.writeBoolean(data.toxicMoonActive);
        buf.writeBoolean(data.noneMoonActive);
        buf.writeBoolean(data.kosmosActive);
        buf.writeBoolean(blood);
        buf.writeInt(bossStage);
        buf.writeInt(bossSegments);
        ServerPlayNetworking.send(p, AniMod.STORY_ACT_PACKET, buf);
    }

    public static void triggerBedGlitch(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(998);
        buf.writeInt(60);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);

        player.sendSystemMessage(Component.literal("§4§l*ТЫ НЕ МОЖЕШЬ УСНУТЬ. ОНО ВСЁ ЕЩЁ СМОТРИТ НА ТЕБЯ ИЗ ТЕМНОТЫ...*"), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.7F);
    }

    public static void startPocketAudio(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(996);
        buf.writeInt(100);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
    }

    public static void stopPocketAudio(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(997);
        buf.writeInt(0);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
    }

    public static void sendVoiceWhisper(ServerPlayer player, int whisperId) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(whisperId);
        buf.writeInt(40);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
    }

    /** Режим разработчика: мгновенный вход в The Broken Script (сектор + карманная логика шёпотов). */
    public static void debugEnterBrokenScript(ServerPlayer player) {
        teleportToBrokenScriptSector(player);
        inPocketDimension = true;
        pocketDimensionTicks = 0;
        chestTriggered = false;
        pocketStunned = false;
        pocketScreamerNext = 240 + POCKET_RND.nextInt(300);
    }

    // Триггер открытия сундука: мгновенный обрыв музыки и запуск шёпотов
    public static void onChestOpenedByPlayer(ServerPlayer player) {
        if (inPocketDimension && !chestTriggered) {
            chestTriggered = true;
            pocketDimensionTicks = POCKET_AMBIENT_END_TICKS;
            pocketStunned = true; // игрок берёт часы и застывает в ступоре до выброса
            stopPocketAudio(player);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 0.5F);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 1.0F, 0.5F);
        }
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
            chestTriggered = false;
            pocketStunned = false;
            victimPlayerUuid = null;

            // Часы выполнили свою роль: исчезают из инвентаря (keepInventory уже был включён,
            // поэтому остальной инвентарь не выпадал — теперь можно вернуть false)
            removeStoppedClock(newPlayer);

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

        restoreAct3State(data);
        persistAct3State(data);

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

        // Медная бессонница: окрас неба ДНЁМ И НОЧЬЮ (канон юзера: тинт только у луны бессонницы,
        // сутки продолжают идти). Флаг гасят только endInsomnia/devTransitionCleanup.
        if (data.insomniaNightActive && server.getTickCount() % 20 == 0) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) syncInsomniaSky(sp, true);
        }

        if (data.acidRainActive) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) syncToxicSky(sp, true);
        }

        // ЖЕЛЕЗНЫЙ ЗАКОН «ОДИН ХОЗЯИН ВРЕМЕНИ»: менеджер акта получает тики ТОЛЬКО
        // в своём акте. Никакой дождь не может тикать в Космосе, а босс — в Акте 2.
        int actNowTick = data.currentAct;
        switch (actNowTick) {
            case 2 -> AcidRainManager.tick(server, data);
            case 4 -> { InsomniaManager.tick(server, data); KosmosManager.tick(server, data); }
            case 5, 6, 90 -> InterludeX5.tick(server);
            case 9 -> BossManager.tick(server, data);
            default -> {}
        }

        // АКТ 5: мобы в игре застывают и смотрят только в сторону Ани (канон)
        int actNow = actIndexForDay(data.storyDay);
        if (actNow == 5 && server.getTickCount() % 40 == 0) {
            ServerPlayer anchor = StoryPlayers.get(server);
            if (anchor != null) {
                AABB box = anchor.getBoundingBox().inflate(48.0);
                List<AnyaEntity> anyas = overworld.getEntitiesOfClass(AnyaEntity.class, box);
                if (!anyas.isEmpty()) {
                    net.minecraft.world.phys.Vec3 anyaPos = anyas.get(0).position();
                    for (Mob mob : overworld.getEntitiesOfClass(Mob.class, box)) {
                        if (mob instanceof AnyaEntity) continue;
                        mob.setNoAi(true);
                        mob.setTarget(null);
                        double dx = anyaPos.x - mob.getX();
                        double dz = anyaPos.z - mob.getZ();
                        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                        mob.setYRot(yaw);
                        mob.yHeadRot = yaw;
                        mob.yBodyRot = yaw;
                    }
                }
            }
        }

        // После Акта 5 размораживаем мобов: NoAI персистится в NBT, иначе они остаются
        // статуями навсегда (боссовые орбы с именем и Anya не трогаем)
        if (actNow != 5 && data.getStoryState() != StoryState.ANYA_BODY_EVENT && server.getTickCount() % 200 == 0) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                for (Mob mob : overworld.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(48.0))) {
                    if (mob instanceof AnyaEntity || mob.hasCustomName()) continue;
                    if (mob.isNoAi()) mob.setNoAi(false);
                }
            }
        }

        // Акт 6: если условие акта выполнено, а следов на диске нет (перезапуск мира) — проявляем их
        if (!data.chatCoreFixed && actNow == 6) {
            net.legoman.animod.story.ArgFilesManager.ensureDump(server);
        }

        tickCurrentState(server, data);

        // АКТ-ДИРИЖЁР: цели актов проверяем раз в секунду
        if (server.getTickCount() % 20 == 0) actDirectorTick(server, data);


        // Конвергенция клиентских оверлеев: раз в 5 секунд каждый игрок получает
        // полный снапшот — всё, что наслойлось, самоликвидируется
        if (server.getTickCount() % 100 == 0) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                sendStorySync(sp, data);
            }
        }

        if (server.getTickCount() % 40 == 0) data.setDirty();
    }

    // === Персист Акта 3: без него релог/краш посреди сцены навсегда запирает игрока в измерении ===
    private static boolean act3Restored = false;

    private static void restoreAct3State(AniSaveData data) {
        if (act3Restored) return;
        act3Restored = true;
        if (data.act3Victim == null || data.act3Victim.isEmpty()) return;
        UUID victim;
        try {
            victim = UUID.fromString(data.act3Victim);
        } catch (IllegalArgumentException e) {
            return;
        }
        victimPlayerUuid = victim;
        cutsceneTimer = data.act3CutsceneTimer;
        burialInProgress = data.act3Burial;
        burialCenterX = data.act3BurialX;
        burialCenterZ = data.act3BurialZ;
        currentDigY = data.act3DigY;
        inPocketDimension = data.act3InPocket;
        pocketDimensionTicks = data.act3PocketTicks;
        chestTriggered = data.act3ChestTriggered;
        pocketStunned = data.act3PocketStunned;
    }

    private static void persistAct3State(AniSaveData data) {
        data.act3Victim = victimPlayerUuid == null ? "" : victimPlayerUuid.toString();
        data.act3CutsceneTimer = cutsceneTimer;
        data.act3Burial = burialInProgress;
        data.act3BurialX = burialCenterX;
        data.act3BurialZ = burialCenterZ;
        data.act3DigY = currentDigY;
        data.act3InPocket = inPocketDimension;
        data.act3PocketTicks = pocketDimensionTicks;
        data.act3ChestTriggered = chestTriggered;
        data.act3PocketStunned = pocketStunned;
    }

    /** Сетка дней (StoryManager.applyDayLogic): см. диздок Часть 1. */

    /** Ручная очистка стадий для /test clean: сбросить всё, что не относится к текущему акту. */
    public static void cleanStages(MinecraftServer server, AniSaveData data) {
        devTransitionCleanup(server, data, actIndexForDay(data.storyDay));
    }

    /**
     * Дев-гигиена переходов: при перескоке по дням (/ani story setDay) сбрасываем
     * состояния чужих актов, чтобы эффекты не наслаивались. Идемпотентно, безопасно
     * и при обычной смене дня (там всё уже согласовано).
     */
    private static void devTransitionCleanup(MinecraftServer server, AniSaveData data, int actNow) {
        // Кислотный дождь — только Акт 2
        if (data.acidRainActive && actNow != 2) {
            AcidRainManager.endAcidRain(server, data, false);
        }

        // Бессонница и Космос — только Акт 4
        if (actNow != 4 && (data.insomniaNightActive || data.kosmosActive)) {
            data.insomniaNightActive = false;
            data.kosmosActive = false;
            data.kosmosBeaconsHit = 0;
            data.kosmosTicks = 0;
            data.setDirty();
            ServerLevel kosmos = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    new net.minecraft.resources.ResourceLocation("animod", "kosmos")));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                syncInsomniaSky(p, false);
                if (ServerPlayNetworking.canSend(p, AniMod.KOSMOS_PACKET)) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBoolean(false);
                    ServerPlayNetworking.send(p, AniMod.KOSMOS_PACKET, buf);
                }
                // тема чёрной дыры глохнет и при принудительном выходе (иначе играет поверх босса)
                if (ServerPlayNetworking.canSend(p, AniMod.STORY_CUTSCENE_PACKET)) {
                    FriendlyByteBuf m = new FriendlyByteBuf(Unpooled.buffer());
                    m.writeInt(987);
                    m.writeInt(0);
                    ServerPlayNetworking.send(p, AniMod.STORY_CUTSCENE_PACKET, m);
                }
                FriendlyByteBuf v = new FriendlyByteBuf(Unpooled.buffer());
                v.writeFloat(0.0F);
                ServerPlayNetworking.send(p, AniMod.VISION_PACKET, v);
                if (kosmos != null && p.serverLevel() == kosmos) {
                    BlockPos safe = InsomniaManager.safeSurface(server.overworld(), p.getBlockX(), p.getBlockZ());
                    p.teleportTo(server.overworld(), safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, p.getYRot(), p.getXRot());
                    p.sendSystemMessage(Component.literal("§7§o*Небо схлопывается, и тебя выносит обратно в мир...*"));
                }
            }
        }

        // Безлуние — только Акт 8
        if (actNow != 8 && data.noneMoonActive) {
            data.noneMoonActive = false;
            data.setDirty();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) syncNoneSky(p, false);
        }

        // Боссфайт — только Акт 9
        if (actNow != 9) {
            BossManager.stop(server, data);
        }

        // Сцена Акта 3 (погребение / The Broken Script) — только Акт 3
        if (actNow != 3 && (cutsceneTimer > 0 || burialInProgress || inPocketDimension)) {
            cutsceneTimer = 0;
            burialInProgress = false;
            inPocketDimension = false;
            pocketStunned = false;
            chestTriggered = false;
            ServerPlayer sp = StoryPlayers.get(server);
            if (sp != null) {
                stopPocketAudio(sp);
                sp.setInvulnerable(false);
                // застрявшего в карманном измерении выносим на поверхность
                ServerLevel pocket = server.getLevel(POCKET_DIMENSION);
                if (pocket != null && sp.serverLevel() == pocket) {
                    BlockPos safe = InsomniaManager.safeSurface(server.overworld(), sp.getBlockX(), sp.getBlockZ());
                    sp.teleportTo(server.overworld(), safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, sp.getYRot(), sp.getXRot());
                    sp.sendSystemMessage(Component.literal("§7§o*Мир моргает — и ты снова на поверхности...*"));
                }
            }
            server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, server);
        }
    }


    // ================= АКТ-ДИРИЖЁР =================
    // Акт кончается ДЕЛОМ (goal), дни — только фейлсейф. Игрок не успел — акт ждёт.

    /** Цель акта: true = дело сделано, можно идти дальше. */
    private static boolean actGoalDone(AniSaveData data) {
        return switch (data.currentAct) {
            case 2 -> data.acidRainDone;                 // дождь утешен (или фейлсейф дождя)
            case 3 -> data.act3SceneDone;                // сцена Бездны сыграна до выброса
            case 4 -> data.kosmosDone;                   // маяки направлены, бессонница спала
            case 6 -> data.chatCoreFixed;                // ядро починено кодом
            case 7 -> data.bsodConfirmed;                // сброс подтверждён
            case 9 -> data.bossDefeated;                 // босс повержен
            case 90 -> !InterludeX5.isActive();          // интерлюдия сыграна до конца
            default -> false;                            // 1, 5, 8, 10 — по фейлсейфу дней
        };
    }

    /** Фейлсейф: максимум дней в акте, если дело так и не сделано. */
    private static int actMaxDays(int act) {
        return switch (act) {
            case 1 -> 4;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 3;
            case 5 -> 4;
            case 6 -> 3;
            case 7 -> 2;
            case 8 -> 3;
            case 9 -> 8;
            default -> Integer.MAX_VALUE;
        };
    }

    /** ЕДИНАЯ точка смены акта: очистка старого → новый → состояния → снапшот. */
    public static void transitionTo(MinecraftServer server, AniSaveData data, int newAct) {
        if (newAct == data.currentAct) return;
        // интерлюдия X.5 вставляется на границе актов (канон)
        if (data.storyPendantGiven && !data.interludeX5Done && newAct > data.pendantAct && data.currentAct != 90) {
            data.x5NextAct = newAct;
            data.currentAct = 90;
            data.actStartDay = data.storyDay;
            data.setDirty();
            ServerPlayer sp = StoryPlayers.get(server);
            if (sp != null) {
                data.interludeX5Done = true;
                data.setDirty();
                InterludeX5.start(server, sp);
            }
            return; // дальше переведёт actGoalDone(90)
        }
        data.currentAct = newAct;
        data.actStartDay = data.storyDay;
        data.setDirty();
        devTransitionCleanup(server, data, newAct);
        applyActState(server, data);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendStorySync(p, data);
    }

    /** Состояние Java (NORMAL/SCARED/...) и HP выводятся ИЗ АКТА, не из дня. */
    private static void applyActState(MinecraftServer server, AniSaveData data) {
        StoryState oldState = data.getStoryState();
        StoryState newState = switch (data.currentAct) {
            case 1 -> StoryState.NORMAL;
            case 2 -> StoryState.ANYA_IS_SCARED;
            case 3 -> StoryState.ANYA_IS_CRYING;
            case 4 -> StoryState.ANYA_IS_WATCHING;
            case 5, 6, 7 -> StoryState.ANYA_IS_ANGRY;
            case 8 -> StoryState.ANYA_IS_NOT_HERE;
            case 9 -> StoryState.ANYA_BODY_EVENT;
            case 10 -> StoryState.STORY_COMPLETED;
            default -> oldState; // 90 = интерлюдия: состояние не меняем
        };
        data.anyaEventHp = switch (data.currentAct) {
            case 3, 4 -> 16.0;
            case 5, 6, 7, 8 -> 14.0;
            case 9 -> 10.0;
            default -> 20.0;
        };
        data.setStoryState(newState);
        data.setDirty();

        if (oldState != newState) {
            if (oldState == StoryState.ANYA_IS_SCARED && data.acidRainActive) {
                AcidRainManager.endAcidRain(server, data, false);
            }
            triggerStateTransition(server, data, oldState, newState);
        }

        // Проклятия: 20% один раз, в актах 5-8
        if (data.currentAct >= 5 && data.currentAct <= 8) {
            net.legoman.animod.economy.CurseManager.rollOnActEntry(server, data);
        }
        // АКТ 7: плашка аварийного сброса (перепоказывается, пока не подтверждена)
        if (data.currentAct == 7 && !data.bsodConfirmed) {
            ServerPlayer sp7 = StoryPlayers.get(server);
            if (sp7 != null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                buf.writeInt(990);
                buf.writeInt(600);
                ServerPlayNetworking.send(sp7, AniMod.STORY_CUTSCENE_PACKET, buf);
            }
        }
        // АКТ 6: следы на диске
        if (data.currentAct == 6 && !data.chatCoreFixed) {
            net.legoman.animod.story.ArgFilesManager.ensureDump(server);
        }
    }

    /** Проверка целей раз в секунду: дело сделано — акт сменяется СРАЗУ, без календаря. */
    private static void actDirectorTick(MinecraftServer server, AniSaveData data) {
        if (data.currentAct == 10) return;
        if (actGoalDone(data)) {
            int next = data.currentAct == 90 ? (data.x5NextAct > 0 ? data.x5NextAct : data.pendantAct + 1)
                                             : data.currentAct + 1;
            transitionTo(server, data, next);
        } else if (data.storyDay - data.actStartDay >= actMaxDays(data.currentAct)) {
            transitionTo(server, data, data.currentAct == 90
                    ? (data.x5NextAct > 0 ? data.x5NextAct : data.pendantAct + 1)
                    : data.currentAct + 1);
        }
    }

    public static void advanceStoryDay(MinecraftServer server, AniSaveData data, int daysToAdd) {
        data.storyDay += daysToAdd;
        data.setDirty();
        applyActState(server, data);
        actDirectorTick(server, data);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!StoryPlayers.isStoryPlayer(player)) continue;
            player.sendSystemMessage(Component.literal("§8§o*Наступил " + data.storyDay + "-й день вашего пути с Аней...*"));
        }
    }

    /** Дев-совместимость: /ani story setDay — прыжок по сетке дней (акт выводится из дня). */
    public static void applyDayLogic(MinecraftServer server, AniSaveData data, int day) {
        data.storyDay = Math.max(1, day);
        data.currentAct = Math.max(1, actIndexForDay(data.storyDay));
        data.actStartDay = data.storyDay;
        data.setDirty();
        devTransitionCleanup(server, data, data.currentAct);
        applyActState(server, data);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendStorySync(p, data);
    }

    /** Номер акта по дню сюжета (общая сетка для шанса Т3, интерлюдии и логики дня). */
    public static int actIndexForDay(int day) {
        if (day <= 4) return 1;
        if (day <= 7) return 2;
        if (day <= 8) return 3;
        if (day <= 11) return 4;
        if (day <= 15) return 5;
        if (day <= 18) return 6;
        if (day <= 19) return 7;
        if (day <= 22) return 8;
        return 9;
    }

    public static void triggerStateTransition(MinecraftServer server, AniSaveData data, StoryState oldState, StoryState newState) {
        ServerPlayer player = StoryPlayers.get(server);
        if (player == null) return;

        PlayerStoryHealthManager.applyHealthCap(player, data.anyaEventHp);

            if (newState == StoryState.ANYA_IS_WATCHING && oldState != StoryState.ANYA_IS_WATCHING
                    && data.insomniaStartDay == 0) {
                // АКТ 4: медная бессонница — 3 дня вечной ночи
                InsomniaManager.start(server, data);
            } else if (newState == StoryState.ANYA_IS_SCARED) {
                // Подарок Ани: 2 золотых сердца (Absorption I)
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 24000, 0, false, true, true));
                player.sendSystemMessage(Component.literal("§d[Аня] Я хочу, чтобы ты был в безопасности... §o*протягивает что-то светящееся*§r §e(+2 золотых сердца)"));

                // Первый эмоциональный сбой: кислотный дождь + токсичная луна
                AcidRainManager.tryStartAcidRain(server, data);
            } else if (newState == StoryState.ANYA_IS_CRYING) {
                cutsceneTimer = TOTAL_CUTSCENE_TICKS;
                victimPlayerUuid = player.getUUID();
                burialInProgress = false;
                inPocketDimension = false;
                pocketDimensionTicks = 0;
                chestTriggered = false;

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

            // АКТ 8: Безлуние — Ани нет, небо пустое, тишина
            if (newState == StoryState.ANYA_IS_NOT_HERE) {
                data.noneMoonActive = true;
                data.insomniaNightActive = false;
                data.toxicMoonActive = false;
                data.setDirty();
                StoryManager.syncNoneSky(player, true);
                StoryManager.syncInsomniaSky(player, false);
                StoryManager.syncToxicSky(player, false);
                player.sendSystemMessage(Component.literal("§8§o*Ты перезапустил мир. Небо пустое. Ни луны, ни солнца, ни звёзд. Ни единого звука...*"));
                StoryAtmosphereEvents.giveFarewellNote(player);
                // «Ани нигде нет»: убираем все её сущности из мира
                for (ServerLevel sl : server.getAllLevels()) {
                    for (Entity e : sl.getAllEntities()) {
                        if (e instanceof AnyaEntity anya) anya.discard();
                    }
                }
            }

            // АКТ 10: финал — небо возвращается + развязка по карме
            if (newState == StoryState.STORY_COMPLETED) {
                data.noneMoonActive = false;
                data.setDirty();
                StoryManager.syncNoneSky(player, false);
                BossManager.playEnding(server, data, player);
            }

            // АКТ 9: Воплощение Сбоя — боссфайт
            if (newState == StoryState.ANYA_BODY_EVENT) {
                BossManager.start(server, data);
            }
    }

    private static void tickCurrentState(MinecraftServer server, AniSaveData data) {
        StoryState state = data.getStoryState();
        ServerPlayer player = StoryPlayers.get(server);
        if (player == null) return;

            // АКТ 9: HP рулит рулетка боссфайта (BossManager), фиксированный кап её затирал бы
            if (state != StoryState.ANYA_BODY_EVENT) {
                PlayerStoryHealthManager.applyHealthCap(player, data.anyaEventHp);
            }

            // 1. АКТИВНАЯ ФАЗА КОШМАРА (в Бездне замедления нет — игрок идёт к сундуку сам)
            if (cutsceneTimer > 0) {
                cutsceneTimer--;
                int elapsed = TOTAL_CUTSCENE_TICKS - cutsceneTimer;

                player.setInvulnerable(true);
                if (!inPocketDimension) {
                    player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 255, false, false, false));
                }

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

                // Бурение шахты в бездну сквозь бедрок
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

            // 2. ОТСЛЕЖИВАНИЕ ПАДЕНИЯ В БЕЗДНУ
            if (burialInProgress && !inPocketDimension) {
                player.setInvulnerable(true);

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

                if (player.getY() <= -65.0) {
                    burialInProgress = false;
                    inPocketDimension = true;
                    pocketDimensionTicks = 0;
                    chestTriggered = false;
                    pocketScreamerNext = 240 + POCKET_RND.nextInt(300);

                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(999);
                    buf.writeInt(40);
                    ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);

                    teleportToBrokenScriptSector(player);
                }
            }

            // 3. ПРЕБЫВАНИЕ В ИЗМЕРЕНИИ THE BROKEN SCRIPT
            if (inPocketDimension) {
                pocketDimensionTicks++;
                player.setInvulnerable(true);
                player.fallDistance = 0.0F;

                // Ступор после взятия часов: только стоять и слушать, до выброса
                if (pocketStunned) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false, false));
                    player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
                }

                // Запуск эмбиента через 20 тиков ПОСЛЕ телепорта (чтобы смена мира не глушила звук!)
                if (pocketDimensionTicks == 20) {
                    startPocketAudio(player);
                }

                // Тьма и туннельное зрение
                if (pocketDimensionTicks % 60 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, false, false, true));
                }

                // Естественный обрыв музыки по таймеру (если игрок так и не открыл сундук)
                if (pocketDimensionTicks == POCKET_AMBIENT_END_TICKS && !chestTriggered) {
                    chestTriggered = true;
                    stopPocketAudio(player);
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 1.0F, 0.5F);
                }

                // Редкие скримеры пока звучит музыка
                if (pocketDimensionTicks < POCKET_AMBIENT_END_TICKS && pocketDimensionTicks >= pocketScreamerNext) {
                    pocketScreamerNext = pocketDimensionTicks + 280 + POCKET_RND.nextInt(340);
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(995);
                    buf.writeInt(35);
                    ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
                }

                // ГОЛОСОВЫЕ ШЁПОТЫ В ТИШИНЕ (пакеты 991, 992, 993, 994)
                if (pocketDimensionTicks == POCKET_AMBIENT_END_TICKS + 40) {
                    sendVoiceWhisper(player, 991);
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.65F);
                } else if (pocketDimensionTicks == POCKET_AMBIENT_END_TICKS + 100) {
                    sendVoiceWhisper(player, 992);
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.75F);
                } else if (pocketDimensionTicks == POCKET_AMBIENT_END_TICKS + 160) {
                    sendVoiceWhisper(player, 993);
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.1F, 0.85F);
                } else if (pocketDimensionTicks == POCKET_AMBIENT_END_TICKS + 220) {
                    sendVoiceWhisper(player, 994);
                    player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 1.0F);
                }

                // Финальный скример
                if (pocketDimensionTicks == POCKET_TOTAL_STAY_TICKS - 40) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeInt(999);
                    buf.writeInt(40);
                    ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
                }

                // Выброс на поверхность под медную Луну
                if (pocketDimensionTicks >= POCKET_TOTAL_STAY_TICKS) {
                    inPocketDimension = false;
                    pocketStunned = false;
                    chestTriggered = false;
                    data.act3SceneDone = true; // цель Акта 3 выполнена
                    data.setDirty();
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

            // Слёзы Ани: только в первой фазе срыва (до сирены), реже — чтобы не наслаивались
            if (state == StoryState.ANYA_IS_CRYING && cutsceneTimer > 0
                    && (TOTAL_CUTSCENE_TICKS - cutsceneTimer) < 440 && server.getTickCount() % 50 == 0) {
                player.serverLevel().sendParticles(ParticleTypes.FALLING_WATER,
                        player.getX(), player.getY() + 1.8, player.getZ(), 2, 0.3, 0.2, 0.3, 0.01);
            }
    }

    // Генерация большого сектора: свободный вход, монолитный мост без дыр, 3 структуры и рабочий сундук
    private static void teleportToBrokenScriptSector(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        ServerLevel pocket = server.getLevel(POCKET_DIMENSION);

        boolean hasPocket = pocket != null;
        ServerLevel targetLevel = hasPocket ? pocket : server.overworld();

        int originX = 0;
        int originY = hasPocket ? 64 : 220;
        int originZ = 0;

        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -1; cz <= 6; cz++) {
                targetLevel.getChunk(cx, cz);
            }
        }

        for (int x = -18; x <= 18; x++) {
            for (int z = -10; z <= 104; z++) {
                for (int y = -8; y <= 24; y++) {
                    targetLevel.setBlock(new BlockPos(originX + x, originY + y, originZ + z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        Random rnd = new Random(404L);

        // 1. АЛТАРЬ (Вход на мост открыт при Z >= 3 и |X| <= 2)
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= 6.5) {
                    BlockPos p = new BlockPos(originX + x, originY, originZ + z);
                    BlockState b = (dist > 5.0) ? Blocks.CRYING_OBSIDIAN.defaultBlockState() :
                            (rnd.nextInt(4) == 0 ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BEDROCK.defaultBlockState());
                    targetLevel.setBlock(p, b, 3);
                    targetLevel.setBlock(p.below(), Blocks.BEDROCK.defaultBlockState(), 3);

                    if (dist > 5.5) {
                        boolean isBridgeEntrance = (z >= 3 && Math.abs(x) <= 2);
                        if (!isBridgeEntrance) {
                            for (int h = 1; h <= 3; h++) {
                                targetLevel.setBlock(p.above(h), Blocks.BARRIER.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }

        for (int py = 1; py <= 4; py++) {
            targetLevel.setBlock(new BlockPos(originX - 2, originY + py, originZ - 4), Blocks.OBSIDIAN.defaultBlockState(), 3);
            targetLevel.setBlock(new BlockPos(originX + 2, originY + py, originZ - 4), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
        }
        targetLevel.setBlock(new BlockPos(originX - 1, originY + 5, originZ - 4), Blocks.OBSIDIAN.defaultBlockState(), 3);
        targetLevel.setBlock(new BlockPos(originX + 1, originY + 5, originZ - 4), Blocks.OBSIDIAN.defaultBlockState(), 3);

        // 2. МОСТ В БЕЗДНУ (The Null Walkway, Z: 7 до 86 — канонные 80 блоков, ширина 5, НИКАКИХ ДЫР!)
        for (int z = 7; z <= 86; z++) {
            for (int x = -2; x <= 2; x++) {
                BlockPos bp = new BlockPos(originX + x, originY, originZ + z);

                BlockState bridgeBlock;
                if (Math.abs(x) == 2) {
                    bridgeBlock = Blocks.BEDROCK.defaultBlockState();
                } else if (z % 6 == 0) {
                    bridgeBlock = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
                } else if (z % 4 == 0) {
                    bridgeBlock = Blocks.SMOOTH_BASALT.defaultBlockState();
                } else {
                    bridgeBlock = Blocks.BEDROCK.defaultBlockState();
                }

                targetLevel.setBlock(bp, bridgeBlock, 3);
                targetLevel.setBlock(bp.below(), Blocks.BEDROCK.defaultBlockState(), 3);

                // Барьеры только по самым внешним краям
                if (x == -2 || x == 2) {
                    for (int h = 1; h <= 3; h++) {
                        targetLevel.setBlock(bp.above(h), Blocks.BARRIER.defaultBlockState(), 3);
                    }
                }
            }
        }

        // 3. АРХИВ / БИБЛИОТЕКА (Z: 22 до 26)
        for (int bx = -7; bx <= -4; bx++) {
            for (int bz = 22; bz <= 26; bz++) {
                BlockPos libFloor = new BlockPos(originX + bx, originY + 1, originZ + bz);
                targetLevel.setBlock(libFloor, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 3);
                if (rnd.nextBoolean()) {
                    targetLevel.setBlock(libFloor.above(), Blocks.BOOKSHELF.defaultBlockState(), 3);
                } else if (rnd.nextInt(3) == 0) {
                    targetLevel.setBlock(libFloor.above(), Blocks.COBWEB.defaultBlockState(), 3);
                }
            }
        }

        // 4. СКАЛК-АНОМАЛИЯ И ПЕРЕВЁРНУТЫЙ ДУБ (Z: 44 до 49)
        BlockPos sculkCenter = new BlockPos(originX + 6, originY, originZ + 46);
        for (int sx = -2; sx <= 2; sx++) {
            for (int sz = -2; sz <= 2; sz++) {
                targetLevel.setBlock(sculkCenter.offset(sx, 0, sz), Blocks.SCULK_CATALYST.defaultBlockState(), 3);
            }
        }
        targetLevel.setBlock(sculkCenter.above(), Blocks.REDSTONE_TORCH.defaultBlockState(), 3);
        // Дуб растёт ВНИЗ в пустоту: ствол свисает, крона на нижнем конце
        for (int ty = 1; ty <= 6; ty++) {
            targetLevel.setBlock(sculkCenter.below(ty), Blocks.DARK_OAK_LOG.defaultBlockState(), 3);
        }
        BlockPos crown = sculkCenter.below(6);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) <= 3) {
                        targetLevel.setBlock(crown.offset(dx, dy, dz), Blocks.DARK_OAK_LEAVES.defaultBlockState(), 3);
                    }
                }
            }
        }

        // 5. ОСКОЛОК ДОМА (Z: 87 до 95 — в самом конце моста)
        for (int x = -3; x <= 3; x++) {
            for (int z = 87; z <= 95; z++) {
                BlockPos hp = new BlockPos(originX + x, originY, originZ + z);
                targetLevel.setBlock(hp, Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
                targetLevel.setBlock(hp.below(), Blocks.BEDROCK.defaultBlockState(), 3);

                if (x == -3 || x == 3 || z == 95) {
                    for (int h = 1; h <= 3; h++) {
                        targetLevel.setBlock(hp.above(h), Blocks.BARRIER.defaultBlockState(), 3);
                    }
                }
            }
        }

        for (int y = 1; y <= 3; y++) {
            targetLevel.setBlock(new BlockPos(originX - 3, originY + y, originZ + 91), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            targetLevel.setBlock(new BlockPos(originX - 3, originY + y, originZ + 93), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        }
        targetLevel.setBlock(new BlockPos(originX - 3, originY + 1, originZ + 92), Blocks.OAK_DOOR.defaultBlockState(), 3);
        // За дверью — пустота: закрываем выход барьером, чтобы игрок не выпал
        targetLevel.setBlock(new BlockPos(originX - 4, originY + 1, originZ + 92), Blocks.BARRIER.defaultBlockState(), 3);
        targetLevel.setBlock(new BlockPos(originX - 4, originY + 2, originZ + 92), Blocks.BARRIER.defaultBlockState(), 3);

        // Сундук с часами
        BlockPos chestPos = new BlockPos(originX, originY + 1, originZ + 93);
        targetLevel.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity be = targetLevel.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            ItemStack clock = new ItemStack(Items.CLOCK);
            clock.setHoverName(Component.literal("§cОстановившиеся часы"));

            CompoundTag display = clock.getOrCreateTagElement("display");
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("§8Они больше не тикают. Время вышло."))));
            display.put("Lore", lore);

            chest.setItem(13, clock);
        }

        // Спавн игрока лицом к мосту
        player.teleportTo(targetLevel, originX + 0.5, originY + 1.2, originZ - 2.0, 0.0F, 0.0F);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0.0F;
        player.setHealth(20.0F);
    }

    /** Часы с именем «Остановившиеся часы» исчезают из инвентаря (сюжетная роль сыграна). */
    private static void removeStoppedClock(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.CLOCK) && stack.getHoverName().getString().contains("Остановившиеся")) {
                inv.setItem(i, ItemStack.EMPTY);
                player.sendSystemMessage(Component.literal("§7§o*Остановившиеся часы рассыпаются мёртвой пылью у тебя в руках...*"));
            }
        }
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