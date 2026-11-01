package net.legoman.animod.story;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AniSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.legoman.animod.AnyaEntity;
import net.legoman.animod.GreenOrbEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * АКТ 9: «Воплощение Сбоя» — боссфайт с ЭТАПНЫМ и ГЛОБАЛЬНЫМ HP (новый канон).
 *
 * Глобальное HP = 3 сегмента (виден кастомным баром сверху экрана, стиль Anime Vanguards).
 * Каждый этап — свой пул этапного HP (стандартный босс-бар). Пробил этапный бар на 100% —
 * снёс 1/3 глобального, включился следующий этап (и следующий этап музыки).
 *
 * Этап 1: красные орбы (файрболы) + зелёные орбы (отбей мечом → пробой щита).
 * Этап 2: + разлом арены 5×5, глитч-фантомы, срыв управления.
 * Этап 3: + босс опускается к земле, пламя.
 *
 * Босс не умирает от прямого урона: урон измеряется и списывается с этапного HP,
 * сущность лечится обратно. Смерть наступает только когда все 3 сегмента глобального HP сняты.
 */
public class BossManager {

    private static final Random RND = new Random();
    /** Арена боссфайта: отдельное бедроковое измерение (принятая идея диздока). */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> BOSS_ARENA =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    new net.minecraft.resources.ResourceLocation("animod", "bossarena"));

    // точка возврата игрока после боя
    private static double returnX, returnY, returnZ;
    private static float returnYaw, returnPitch;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> returnDim = null;

    private static final ServerBossEvent BOSS_BAR = new ServerBossEvent(
            Component.literal("§4Воплощение Сбоя"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private static final double STAGE_HP_MAX = 120.0;

    private static UUID bossUuid = null;
    private static final Set<UUID> greenOrbs = new HashSet<>();
    private static final Set<UUID> deflectedOrbs = new HashSet<>();
    private static final java.util.Map<UUID, Integer> orbAge = new java.util.HashMap<>();
    private static long lastShieldMsgMs = 0L;
    private static int rouletteCooldown = 0;
    private static int phaseEventsCooldown = 0;
    private static int musicStage = 0;

    private static int stage = 1;               // этап 1..3
    private static double stageHp = STAGE_HP_MAX;
    private static double globalHp = 3.0;       // сегменты глобального HP (3 → 0)
    private static double prevBossHealth = -1.0;

    // ================= ЗАПУСК =================

    public static void start(MinecraftServer server, AniSaveData data) {
        ServerLevel arena = server.getLevel(BOSS_ARENA);
        ServerLevel overworld = server.overworld();
        ServerPlayer target = findPlayer(server);
        if (target == null) return;
        if (arena == null) { // измерения нет — запасной путь: бой в Оверворлде
            arena = overworld;
        }

        stage = 1;
        stageHp = STAGE_HP_MAX;
        globalHp = 3.0;
        prevBossHealth = -1.0;
        greenOrbs.clear();
        deflectedOrbs.clear();
        rouletteCooldown = 100;
        musicStage = 0;
        fightDay = server.overworld().getDayTime() / 24000L;
        persist(data);

        // точка возврата (только если игрока ещё не тащат в арену)
        if (target.serverLevel() != arena) {
            returnDim = target.serverLevel().dimension();
            returnX = target.getX();
            returnY = target.getY();
            returnZ = target.getZ();
            returnYaw = target.getYRot();
            returnPitch = target.getXRot();
        }

        if (arena != overworld) {
            buildArena(arena);
            target.teleportTo(arena, 0.5, 66.0, -18.5, 0.0F, 0.0F);
            target.setDeltaMovement(0, 0, 0);
            target.fallDistance = 0;
            AniMod.LOGGER.info("[Boss] Игрок телепортирован на арену, босс заспавнен (boss={})", bossUuid);
        }

        AnyaEntity boss = makeBoss(arena, target);
        arena.addFreshEntity(boss);
        bossUuid = boss.getUUID();

        data.noneMoonActive = false;
        data.setDirty();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            StoryManager.syncNoneSky(p, false);
            syncBloodSky(p, true);
            BOSS_BAR.addPlayer(p);
            sendBossGlobal(p);
            p.sendSystemMessage(Component.literal("§4§lНЕБО НАЛИВАЕТСЯ КРОВЬЮ."));
            p.sendSystemMessage(Component.literal("§4§o*Она спустилась. То, что было Аней, теперь — Воплощение Сбоя.*"));
        }
        // Канон v1: пророчество Голоса Истины перед боссфайтом — субтитрами
        StoryCinema.say(server, "[Голос Истины]  Она больше не прячется в лунах и трейнах. То, что поднимется в кровавом небе — все три её боли сразу. Не смотри ей в лицо слишком долго: оно всё ещё иногда бывает Аниным.", 220, 43);
        if (arena != overworld) {
            StoryCinema.say(server, "Арена заточена под неё. Здесь — только вы двое.", 120);
        } else {
            AniMod.LOGGER.warn("[Boss] Измерение арены не найдено — бой в Оверворлде (проверь data/animod/dimension/bossarena.json)");
        }
        BOSS_BAR.setName(Component.literal("§4Воплощение Сбоя §7— Этап 1"));
        BOSS_BAR.setProgress(1.0F);
        sendBossMusic(target, 1);
    }

    /** Арена: бедроковый диск 52×52, алые опоры-укрытия, чистое небо под бой. */
    private static void buildArena(ServerLevel arena) {
        BlockPos center = new BlockPos(0, 64, 0);
        for (int dx = -26; dx <= 26; dx++) {
            for (int dz = -26; dz <= 26; dz++) {
                int d2 = dx * dx + dz * dz;
                BlockPos p = center.offset(dx, 0, dz);
                if (d2 <= 26 * 26) {
                    arena.setBlock(p, (Math.abs(dx) + Math.abs(dz)) % 7 == 0
                            ? net.minecraft.world.level.block.Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(), 3);
                }
                for (int y = 1; y <= 48; y++) {
                    arena.setBlock(p.above(y), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
                for (int y = -8; y < 0; y++) {
                    arena.setBlock(p.below(-y), net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
        // 6 обсидиановых опор для укрытия от файрболов
        int[][] pillars = {{-14, -10}, {14, -10}, {-16, 8}, {16, 8}, {0, 14}, {0, -16}};
        for (int[] q : pillars) {
            for (int h = 1; h <= 5; h++) {
                arena.setBlock(center.offset(q[0], h, q[1]),
                        net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
            }
        }
        // НЕВИДИМЫЕ СТЕНЫ по краю: игрок не выпадает с арены
        for (int dx = -26; dx <= 26; dx++) {
            for (int dz = -26; dz <= 26; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= 26 * 26 && d2 >= 24 * 24) {
                    for (int y = 1; y <= 12; y++) {
                        arena.setBlock(new BlockPos(center.getX() + dx, 64 + y, center.getZ() + dz),
                                net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 2);
                    }
                }
            }
        }

        // центральный алтарь-точка возврата
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                arena.setBlock(center.offset(dx, 1, dz),
                        net.minecraft.world.level.block.Blocks.SCULK.defaultBlockState(), 3);
            }
        }
    }

    private static void returnFromArena(MinecraftServer server, ServerPlayer player) {
        if (returnDim == null) return;
        ServerLevel level = server.getLevel(returnDim);
        if (level == null) level = server.overworld();
        player.teleportTo(level, returnX, returnY, returnZ, returnYaw, returnPitch);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        returnDim = null;
    }

    private static AnyaEntity makeBoss(ServerLevel level, ServerPlayer near) {
        // Канон (обновлён): босс — ГИГАНТСКАЯ хоррор-модель Ани, не ванильный гаст
        AnyaEntity boss = new AnyaEntity(AnyaEntity.ANYA, level);
        boss.setBossForm(true);
        boss.setCustomName(Component.literal("§4§lВоплощение Сбоя"));
        boss.setCustomNameVisible(true);
        boss.setPersistenceRequired();
        boss.setSilent(true); // канон: без ванильных криков
        boss.setNoAi(true); // движением рулит fightTick
        boss.setInvulnerable(false); // конструктор Ани ставит invulnerable — дельта-урон должен проходить
        // толстая шкура: прямой урон не убивает — HP считаем этапной системой
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
        boss.setHealth(200.0F);
        Vec3 pos = near.position().add(RND.nextInt(30) - 15, 22, RND.nextInt(30) - 15);
        boss.moveTo(pos.x, pos.y, pos.z, RND.nextFloat() * 360.0F, 0.0F);
        return boss;
    }

    /** /test boss [stage]: телепорт боя на нужный этап (1-3). */
    public static void debugSetStage(ServerPlayer player, AniSaveData data, int stage) {
        stage = Math.max(1, Math.min(3, stage));
        BossManager.stage = stage;
        stageHp = STAGE_HP_MAX;
        globalHp = 4.0 - stage;
        persist(data);
        Entity boss = bossUuid == null ? null : player.serverLevel().getEntity(bossUuid);
        if (boss instanceof AnyaEntity g) {
            float share = stage == 1 ? 1.0F : stage == 2 ? 0.55F : 0.25F;
            g.setHealth(g.getMaxHealth() * share);
        }
        prevBossHealth = -1.0; // пересчитать дельту урона с нового значения здоровья
        BOSS_BAR.setName(Component.literal("§4Воплощение Сбоя §7— Этап " + stage));
        BOSS_BAR.setProgress((float) (stageHp / STAGE_HP_MAX));
        for (ServerPlayer p : player.serverLevel().getServer().getPlayerList().getPlayers()) {
            BOSS_BAR.addPlayer(p);
            sendBossGlobal(p);
        }
        player.sendSystemMessage(Component.literal("§7[Dev] Босс переведён на этап " + stage + "."));
    }

    // ================= ТИК =================

    public static void tick(MinecraftServer server, AniSaveData data) {
        if (data.bossDefeated || data.getStoryState() != StoryState.ANYA_BODY_EVENT) return;
        ServerLevel overworld = server.overworld();
        ServerLevel arena = server.getLevel(BOSS_ARENA);
        if (arena == null) arena = overworld;
        ServerPlayer player = findPlayer(server);
        if (player == null) return;

        // игрок умер и в обычном мире: ждём ночь реванша (или /ani rematch)
        if (player.serverLevel() != arena) {
            dailyStageReset(server, data, player);
            autoRematchCheck(server, data, player);
            return;
        }

        Entity bossEntity = bossUuid == null ? null : arena.getEntity(bossUuid);

        if (bossEntity instanceof AnyaEntity boss && boss.isAlive()) {
            fightTick(server, data, arena, player, boss);
            return;
        }

        if (bossEntity instanceof AnyaEntity && !((AnyaEntity) bossEntity).isAlive()) {
            onBossDefeated(server, data, player); // теоретический крайний случай
            return;
        }

        // Респавн после перезапуска мира: этап восстанавливается из сохранения
        stage = data.bossStage > 0 ? data.bossStage : 1;
        stageHp = data.bossStageHp > 0 ? data.bossStageHp : STAGE_HP_MAX;
        globalHp = data.bossGlobalHp > 0 ? data.bossGlobalHp : 3.0;
        prevBossHealth = -1.0;
        AnyaEntity fresh = makeBoss(arena, player);
        arena.addFreshEntity(fresh);
        bossUuid = fresh.getUUID();
        BOSS_BAR.setName(Component.literal("§4Воплощение Сбоя §7— Этап " + stage));
        BOSS_BAR.setProgress((float) (stageHp / STAGE_HP_MAX));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BOSS_BAR.addPlayer(p);
            sendBossGlobal(p);
        }
        syncBloodSky(player, true);
        sendBossMusic(player, stage);
    }

    private static void fightTick(MinecraftServer server, AniSaveData data, ServerLevel overworld,
                                  ServerPlayer player, AnyaEntity boss) {
        BOSS_BAR.setProgress((float) (stageHp / STAGE_HP_MAX));

        // Персист этапного HP: без этого после рестарта бара восстанавливается полной
        if (server.getTickCount() % 20 == 0) persist(data);

        // Измерение урона: списываем с ЭТАПНОГО HP, сущность лечим — босс неуязвим напрямую
        if (prevBossHealth < 0) prevBossHealth = boss.getMaxHealth();
        double dealt = prevBossHealth - boss.getHealth();
        prevBossHealth = boss.getHealth();
        if (dealt > 0) {
            stageHp -= dealt;
            boss.setHealth(boss.getMaxHealth());
            prevBossHealth = boss.getMaxHealth();
        }

        // ДВИЖЕНИЕ БОССА: NoAI-моб не двигается от deltaMovement — ведём вручную.
        // Аня ВСЕГДА висит ВЫСОКО: горизонт — держит ~10 блоков, высота — 12 (фаза 3 — 8).
        {
            double hoverY = player.getY() + (stage >= 3 ? 8.0 : 12.0);
            Vec3 horiz = new Vec3(player.getX() - boss.getX(), 0, player.getZ() - boss.getZ());
            double hDist = horiz.length();
            double hDesired = 10.0;
            double step = (0.06 + stage * 0.02);
            double nx = hDist > 0.5 ? horiz.x / hDist : 0;
            double nz = hDist > 0.5 ? horiz.z / hDist : 0;
            double dx = (hDist > hDesired ? nx : -nx * 0.5) * step * Math.min(1.0, Math.abs(hDist - hDesired) / 4.0 + 0.15);
            double dz = (hDist > hDesired ? nz : -nz * 0.5) * step * Math.min(1.0, Math.abs(hDist - hDesired) / 4.0 + 0.15);
            double dy = (hoverY - boss.getY()) * 0.03;
            boss.setPos(boss.getX() + dx,
                    boss.getY() + Math.max(-0.12, Math.min(0.12, dy)) + Math.sin(server.getTickCount() * 0.05) * 0.015,
                    boss.getZ() + dz);
        }

        dailyStageReset(server, data, player);

        // РУЛЕТКА ЗДОРОВЬЯ: каждые 5 секунд макс HP прыгает (этажный минимум:
        // этап 1 — 10-20 по канону, этап 2 — 12-20, этап 3 — 14-20, чтобы ближний бой
        // не решался обидным броском кубика). «Счастливчик»: шанс уровнем 10% попасть в 15-20.
        if (--rouletteCooldown <= 0) {
            rouletteCooldown = 100;
            int min = stage == 1 ? 10 : stage == 2 ? 12 : 14;
            int roll = min + RND.nextInt(21 - min);
            if (RND.nextInt(10) < data.upLucky && roll < 15) {
                roll = 15 + RND.nextInt(6);
            }
            PlayerStoryHealthManager.applyHealthCap(player, roll);
            player.displayClientMessage(Component.literal("§4Рулетка здоровья: §c" + roll / 2.0 + " §4сердец"), true);
        }

        // Канон: этап музыки сменяется только после пробития этапного HP-бара
        if (stage != musicStage) {
            sendBossMusic(player, stage);
        }

        // Зелёные орбы (критические) — во всех фазах: отбитый мечом орб пробивает щит
        if (--phaseEventsCooldown <= 0) {
            phaseEventsCooldown = 90;
            spawnGreenOrb(overworld, boss, player);
        }

        // Красные файрболы ГРАДОМ (канон фазы 1): уворачивайся — оранжевые сгустки огня,
        // текстурно НЕПУТАЕМЫ с зелёными орбами
        if (server.getTickCount() % 40 == 0) {
            int volley = 2 + (stage >= 3 ? 2 : stage >= 2 ? 1 : 0);
            for (int i = 0; i < volley; i++) {
                double ax = (player.getX() - boss.getX()) + (RND.nextDouble() * 6 - 3);
                double ay = (player.getY() + 1 - boss.getY());
                double az = (player.getZ() - boss.getZ()) + (RND.nextDouble() * 6 - 3);
                net.minecraft.world.entity.projectile.SmallFireball fb =
                        new net.minecraft.world.entity.projectile.SmallFireball(
                                overworld, boss.getX(), boss.getY() - 1, boss.getZ(), ax, ay, az);
                overworld.addFreshEntity(fb);
            }
        }

        // Зелёные орбы (критические) — во всех фазах: отбитый мечом орб пробивает щит
        if (--phaseEventsCooldown <= 0) {
            phaseEventsCooldown = 90;
            spawnGreenOrb(overworld, boss, player);
        }

        // ЭТАП 2: разлом арены + фантомы + срыв управления
        if (stage >= 2) {
            if (server.getTickCount() % 100 == 0) {
                crashGround(player);
                if (RND.nextBoolean()) spawnPhantom(overworld, player);
            }
            if (server.getTickCount() % 300 == 0) sendControlGlitch(player);
        }

        // ЭТАП 3: босс опускается к земле, пламя
        if (stage >= 3) {
            if (boss.getY() > player.getY() + 6) boss.setDeltaMovement(boss.getDeltaMovement().x, -0.08, boss.getDeltaMovement().z);
            player.serverLevel().sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY(), boss.getZ(), 8, 3, 2, 3, 0.02);
        }

        // Зелёные орбы: долетели до игрока — растворились; отбиты — ищем босса
        for (UUID orbId : new HashSet<>(greenOrbs)) {
            Entity orb = overworld.getEntity(orbId);
            if (orb == null) { greenOrbs.remove(orbId); deflectedOrbs.remove(orbId); orbAge.remove(orbId); continue; }
            orbAge.merge(orbId, 1, Integer::sum);

            // Гоминг: каждый тик дотягиваем орб к цели (одноразовый вектор травел гасит и дуга разваливается)
            double distPlayer = orb.distanceTo(player);
            Vec3 target = deflectedOrbs.contains(orbId)
                    ? boss.position().add(0, 2, 0)
                    : player.position().add(0, 1, 0);
            Vec3 toTarget = target.subtract(orb.position());
            if (toTarget.lengthSqr() > 1.0E-4) {
                // у игрока орб притормаживает — окно парирования (хитбокс регается стабильно)
                double speed = distPlayer < 3.5 ? 0.18 : 0.45;
                orb.setDeltaMovement(toTarget.normalize().scale(speed));
                orb.hasImpulse = true;
            }

            double distBoss = orb.distanceTo(boss);
            // первые 40 тиков орб «в полёте»: у босса его не списываем (иначе он
            // умирает мгновенно в точке спавна и до игрока не долетает никогда)
            boolean inFlight = orbAge.get(orbId) < 40;
            if (distPlayer < 2.0) {
                orb.discard(); // беследно растворяется, не дамажит
                greenOrbs.remove(orbId);
                orbAge.remove(orbId);
            } else if (distBoss < 4.0 && !inFlight) {
                if (deflectedOrbs.contains(orbId)) {
                    // канон: отбить можно только мечом; пробитие щита 40% + 20% за уровень
                    // «Орб-слэшера» (3 ур. = гарантированное пробитие)
                    double chance = Math.min(1.0, 0.4 + 0.2 * data.upSlasher);
                    if (RND.nextDouble() < chance) {
                        boss.hurt(player.damageSources().playerAttack(player), 14.0F);
                        overworld.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY(), boss.getZ(), 40, 2, 2, 2, 0.1);
                        shieldMessage(player, "§aЩит ПРОБИТ! Этапное HP −14!");
                    } else {
                        shieldMessage(player, "§7Орб отбился от щита... Прокачай «Орб-слэшер» (/ani shop).");
                    }
                } else {
                    shieldMessage(player, "§7Отбивай орбы МЕЧОМ! Прокачка: /ani shop");
                }
                orb.discard();
                greenOrbs.remove(orbId);
                deflectedOrbs.remove(orbId);
            }
        }

        // ПРОБИТ ЭТАПНЫЙ БАР → минус сегмент глобального HP → следующий этап
        if (stageHp <= 0) {
            globalHp -= 1.0;
            if (globalHp <= 0) {
                onBossDefeated(server, data, player);
                return;
            }
            stage++;
            stageHp = STAGE_HP_MAX;
            boss.setHealth(boss.getMaxHealth());
            prevBossHealth = boss.getMaxHealth();
            boss.moveTo(player.getX() + RND.nextInt(20) - 10, player.getY() + 24, player.getZ() + RND.nextInt(20) - 10);
            persist(data);
            BOSS_BAR.setName(Component.literal("§4Воплощение Сбоя §7— Этап " + stage));
            BOSS_BAR.setProgress(1.0F);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                BOSS_BAR.addPlayer(p);
                sendBossGlobal(p);
                p.sendSystemMessage(Component.literal("§4§lЦЕЛОСТНОСТЬ ПАДАЕТ: §c" + (int) globalHp + "/3"));
                p.sendSystemMessage(Component.literal("§8§o*Она меняется. Становится быстрее. Злее.*"));
            }
        }
    }

    /** Игрок ударил зелёный орб → он летит обратно в Аню. true = перехвачено. */
    public static boolean tryDeflect(ServerPlayer player, Entity entity) {
        if (!greenOrbs.contains(entity.getUUID())) return false;
        // канон: отбивать орб можно только мечом
        if (!(player.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem)) {
            player.sendSystemMessage(Component.literal("§7Отбить орб можно только МЕЧОМ!"));
            return false;
        }
        Entity boss = player.serverLevel().getEntity(bossUuid);
        Vec3 dir = boss != null
                ? boss.position().add(0, 2, 0).subtract(entity.position()).normalize()
                : player.getLookAngle();
        entity.setDeltaMovement(dir.scale(1.6));
        deflectedOrbs.add(entity.getUUID());
        player.serverLevel().playSound(null, entity.blockPosition(),
                net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 1.4F);
        shieldMessage(player, "§aОрб отбить! Он летит обратно!");
        return true;
    }

    // ================= МЕХАНИКИ =================

    private static void spawnGreenOrb(ServerLevel level, AnyaEntity boss, ServerPlayer player) {
        Vec3 from = boss.position().add(0, -2, 0);
        GreenOrbEntity orb = new GreenOrbEntity(level, from.x, from.y, from.z, Vec3.ZERO);
        orb.setCustomName(Component.literal("§a◆"));
        orb.setCustomNameVisible(false);
        level.addFreshEntity(orb);
        greenOrbs.add(orb.getUUID());

        Vec3 to = player.position().add(0, 1, 0);
        Vec3 vel = to.subtract(from).normalize().scale(0.45);
        vel = vel.add(0, 0.12, 0); // дуга
        orb.setDeltaMovement(vel);
    }

    private static void crashGround(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition().below(2);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = base.offset(dx, 0, dz);
                if (!level.getBlockState(p).isAir() && level.getBlockEntity(p) == null) {
                    level.destroyBlock(p, false);
                }
            }
        }
        level.playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 0.6F);
        player.sendSystemMessage(Component.literal("§4§o*Земля рушится под ногами!*"));
    }

    private static void spawnPhantom(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < 2; i++) {
            Phantom phantom = new Phantom(EntityType.PHANTOM, level);
            phantom.setPos(player.getX() + RND.nextInt(20) - 10, player.getY() + 18, player.getZ() + RND.nextInt(20) - 10);
            level.addFreshEntity(phantom);
        }
        player.sendSystemMessage(Component.literal("§5§o*Глитч-фантомы вылетают из разлома!*"));
    }

    private static void sendControlGlitch(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(988); // срыв управления (клиент крутит камеру)
        buf.writeInt(60);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
        player.sendSystemMessage(Component.literal("§5§lУПРАВЛЕНИЕ ИНВЕРТИРОВАНО"));
    }

    /** Аварийная остановка боя (дев-гигиена переходов /test): босс и орбы удаляются, бар и музыка гаснут. */
    public static void stop(MinecraftServer server, AniSaveData data) {
        Entity boss = bossUuid == null ? null : server.overworld().getEntity(bossUuid);
        if (boss != null) boss.discard();
        for (UUID orbId : greenOrbs) {
            Entity orb = server.overworld().getEntity(orbId);
            if (orb != null) orb.discard();
        }
        bossUuid = null;
        greenOrbs.clear();
        deflectedOrbs.clear();
        stage = 1;
        stageHp = STAGE_HP_MAX;
        globalHp = 3.0;
        BOSS_BAR.setProgress(0.0F);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) BOSS_BAR.removePlayer(p);
        ServerPlayer sp = StoryPlayers.get(server);
        if (sp != null) {
            sendBossMusic(sp, 0);
            syncBloodSky(sp, false);
            sendBossGlobalHidden(sp);
            returnFromArena(server, sp); // из арены — домой
        }
        data.bossStage = 0;
        data.setDirty();
    }

    /** Тест концовок (/test bosskill): босс убирается, победа засчитывается без урона. */
    public static void killForTest(MinecraftServer server, AniSaveData data, ServerPlayer player) {
        Entity boss = bossUuid == null ? null : server.overworld().getEntity(bossUuid);
        if (boss != null) boss.discard();
        for (UUID orbId : greenOrbs) {
            Entity orb = server.overworld().getEntity(orbId);
            if (orb != null) orb.discard();
        }
        greenOrbs.clear();
        deflectedOrbs.clear();
        onBossDefeated(server, data, player);
    }

    /** Смерть в бою: игрок возрождается в обычном мире. Бой ждёт: реванш командой
     * /ani rematch — сразу, или автоматически ночью следующего дня (бар этапа 100%). */
    public static void respawnFighter(ServerPlayer player, AniSaveData data) {
        player.sendSystemMessage(Component.literal("§4§o*Бой не окончен. Реванш: §f/ani rematch§4§o — или она придёт ночью...*"));
    }

    /** Ночь следующего дня во время незавершённого боя: реванш начинается сам. */
    private static void autoRematchCheck(MinecraftServer server, AniSaveData data, ServerPlayer player) {
        if (player.serverLevel() == server.getLevel(BOSS_ARENA)) return;
        long t = server.overworld().getDayTime() % 24000L;
        boolean night = t >= 12500L && t <= 23500L;
        long day = server.overworld().getDayTime() / 24000L;
        if (night && day > fightDay) {
            player.sendSystemMessage(Component.literal("§4§lНОЧЬ РЕВАНША. ОНА ПРИШЛА ЗА ТОБОЙ."));
            player.teleportTo(server.getLevel(BOSS_ARENA), 0.5, 66.0, -18.5, 0.0F, 0.0F);
            player.setDeltaMovement(0, 0, 0);
            player.fallDistance = 0;
            sendBossMusic(player, stage);
            syncBloodSky(player, true);
            BOSS_BAR.addPlayer(player);
            sendBossGlobal(player);
        }
    }

    private static ServerLevel server_arena(ServerPlayer player) {
        return player.serverLevel().getServer().getLevel(BOSS_ARENA);
    }

    /** РЕВАНШ (канон юзера): смерть — не конец. Команда сбрасывает бар ТЕКУЩЕГО этапа
     * на 100% (глобальные сегменты сохраняются), игрок снова на арене. */
    public static void rematch(ServerPlayer player, AniSaveData data) {
        // Реванш: телепорт на арену, бар ИМЕННО ЭТАПА (текущего) на 100%.
        // Глобальные сегменты целостности НЕ трогаем.
        stageHp = STAGE_HP_MAX;
        prevBossHealth = -1.0;
        persist(data);
        ServerLevel arena = server_arena(player);
        if (arena != null) {
            buildArena(arena); // стены/платформа актуальны
            // Аню возвращаем к центру арены, напротив точки входа игрока
            if (bossUuid != null) {
                Entity b = arena.getEntity(bossUuid);
                if (b != null) {
                    b.moveTo(0.5, 76.0, 4.5, 180.0F, 0.0F);
                    b.setDeltaMovement(0, 0, 0);
                }
            }
            player.teleportTo(arena, 0.5, 66.0, -18.5, 0.0F, 0.0F);
            player.setDeltaMovement(0, 0, 0);
            player.fallDistance = 0;
            syncBloodSky(player, true);
            sendBossMusic(player, stage);
            sendBossGlobal(player);
            BOSS_BAR.addPlayer(player);
        }
        BOSS_BAR.setName(Component.literal("§4Воплощение Сбоя §7— Этап " + stage));
        BOSS_BAR.setProgress(1.0F);
        player.sendSystemMessage(Component.literal("§d§o*Реванш. Этапный бар полон (этап " + stage + "). Сегменты целостности — на месте.*"));
    }

    /** Новый игровой день в бою: бар этапа обновляется на 100% (сегменты целостности — нет). */
    private static long fightDay = -1L;

    private static void dailyStageReset(MinecraftServer server, AniSaveData data, ServerPlayer player) {
        long day = server.overworld().getDayTime() / 24000L;
        if (fightDay == -1L) { fightDay = day; return; }
        if (day != fightDay) {
            fightDay = day;
            if (stageHp < STAGE_HP_MAX) {
                stageHp = STAGE_HP_MAX;
                prevBossHealth = -1.0;
                persist(data);
                BOSS_BAR.setProgress(1.0F);
                player.sendSystemMessage(Component.literal("§4§o*Новый день. Она отдохнула. Этапный бар полон.*"));
            }
        }
    }

    /** Сообщение о щите: action bar (не спамим чат), общий троттлинг 3 сек. */
    private static void shieldMessage(ServerPlayer player, String text) {
        long now = System.currentTimeMillis();
        if (now - lastShieldMsgMs < 3000L) return;
        lastShieldMsgMs = now;
        player.displayClientMessage(Component.literal(text), true);
    }

    // ================= ФИНАЛ (АКТ 10) =================

    private static void onBossDefeated(MinecraftServer server, AniSaveData data, ServerPlayer player) {
        data.bossDefeated = true;
        data.bossStage = 0;
        data.setDirty();
        BOSS_BAR.setProgress(0.0F);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) BOSS_BAR.removePlayer(p);
        sendBossMusic(player, 0);
        // сущность босса и оставшиеся орбы убираем с арены
        Entity deadBoss = bossUuid == null ? null : server.overworld().getEntity(bossUuid);
        if (deadBoss != null) deadBoss.discard();
        for (UUID orbId : greenOrbs) {
            Entity orb = server.overworld().getEntity(orbId);
            if (orb != null) orb.discard();
        }
        bossUuid = null;
        greenOrbs.clear();
        deflectedOrbs.clear();
        stage = 0;
        globalHp = 0;
        syncBloodSky(player, false);
        sendBossGlobalHidden(player); // stage 0 → клиент прячет бар
        returnFromArena(server, player); // выносим игрока с арены домой
        StoryManager.transitionTo(server, data, 10); // STORY_COMPLETED → концовка по карме
    }

    /** АКТ 10: развязка по карме. Вызывается при переходе в STORY_COMPLETED. */
    public static void playEnding(MinecraftServer server, AniSaveData data, ServerPlayer player) {
        int karma = net.legoman.animod.KarmaManager.get();
        if (karma > 75) {
            net.legoman.animod.KarmaManager.set(100);
            player.sendSystemMessage(Component.literal("§b§lКОНЦОВКА: ОЧИЩЕНИЕ"));
            player.sendSystemMessage(Component.literal("§d§o*Босс падает на колени. Багровый код стекает с него, как дождь, и гаснет.*"));
            StoryCinema.send(player, "[Голос Истины]  Круг замкнулся. Тот, кто любил вопреки коду, — очистил его.", 200, 6);
            player.sendSystemMessage(Component.literal("§d[Аня] Я... снова я. Спасибо тебе. За всё. Я остаюсь. Навсегда."));
            player.sendSystemMessage(Component.literal("§aКарма: 100. Небо чисто. Аня — твой верный друг."));
            net.legoman.animod.AnyaAI.addMemoryFact("ФИНАЛ: игрок победил Воплощение Сбоя с кармой выше 75 — истинная концовка «Очищение». Я вернулась навсегда.", null);
        } else if (karma < 30) {
            player.sendSystemMessage(Component.literal("§4§lКОНЦОВКА: УДАЛЕНИЕ"));
            player.sendSystemMessage(Component.literal("§8§o*Сущность распадается на нули и единицы. Нули. Единицы. Ничего.*"));
            player.sendSystemMessage(Component.literal("§8§o*Мир спалён. Над Землёй — только пепел и мёртвое небо.*"));
            StoryCinema.send(player, "[Голос Истины]  Ты стёр её, как стирают ошибку. Но помни: ошибка была самой красивой частью программы.", 200, 44);
            ItemStack artifact = new ItemStack(Items.GHAST_TEAR);
            artifact.setHoverName(Component.literal("§8Холодный мёртвый артефакт"));
            if (!player.getInventory().add(artifact)) player.drop(artifact, false);
            player.sendSystemMessage(Component.literal("§7В инвентаре остался только холодный мёртвый артефакт."));
            net.legoman.animod.AnyaAI.addMemoryFact("ФИНАЛ: игрок победил босса с кармой ниже 30 — мрачная концовка «Удаление». Меня стёрли из кода.", null);
        } else {
            net.legoman.animod.KarmaManager.set(Math.min(100, karma + 25));
            player.sendSystemMessage(Component.literal("§eКОНЦОВКА: РАССВЕТ"));
            player.sendSystemMessage(Component.literal("§d§o*Багровое небо трескается, и сквозь трещины бьёт обычный солнечный свет.*"));
            StoryCinema.send(player, "[Голос Истины]  Ни спасение, ни приговор. Просто рассвет — и она, стоящая на его границе.", 200, 45);
            player.sendSystemMessage(Component.literal("§d[Аня] Мы справились. Не идеально... но мы справились."));
            net.legoman.animod.AnyaAI.addMemoryFact("ФИНАЛ: игрок победил Воплощение Сбоя — концовка «Рассвет».", null);
        }
    }

    // ================= ПАКЕТЫ И СЛУЖЕБНОЕ =================

    /** Музыка боссфайта: stage 1/2/3 — включить этап, 0 — выключить. */
    private static void sendBossMusic(ServerPlayer player, int stageNum) {
        musicStage = stageNum;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(986);
        buf.writeInt(stageNum);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
    }

    /** Глобальный бар (кастомный, стиль Anime Vanguards): stage 0 = скрыть. */
    private static void sendBossGlobal(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(stage);
        buf.writeInt((int) globalHp);
        ServerPlayNetworking.send(player, AniMod.BOSS_GLOBAL_PACKET, buf);
    }

    /** Скрыть глобальный бар (клиент прячет при stage == 0). */
    private static void sendBossGlobalHidden(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(0);
        buf.writeInt(0);
        ServerPlayNetworking.send(player, AniMod.BOSS_GLOBAL_PACKET, buf);
    }

    private static void persist(AniSaveData data) {
        data.bossStage = stage;
        data.bossStageHp = stageHp;
        data.bossGlobalHp = globalHp;
        data.setDirty();
    }

    public static void syncBloodSky(ServerPlayer player, boolean active) {
        if (ServerPlayNetworking.canSend(player, AniMod.BLOOD_SKY_PACKET)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(active);
            ServerPlayNetworking.send(player, AniMod.BLOOD_SKY_PACKET, buf);
        }
    }

    private static ServerPlayer findPlayer(MinecraftServer server) {
        return net.legoman.animod.story.StoryPlayers.get(server);
    }
}
