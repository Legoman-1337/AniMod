package net.legoman.animod.story;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AniSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Random;

/**
 * АКТ 4: Космическое измерение. Кровать на 3-й день бессонницы затягивает игрока сюда.
 * Чёрная дыра на небе (клиент), невесомость (медленное падение + прыжки ~5 блоков),
 * мини-квест: 3 гравитационных маяка — ПКМ по маяку направляет его луч в дыру.
 * После третьего — возврат на рассвете (InsomniaManager.endInsomnia).
 * Чёрная дыра — упрощённый визуал (чёрно-багровое небо); шейдер аккреционного диска
 * как в Интерстелларе можно добавить позже отдельным пост-шейдером.
 */
public class KosmosManager {

    private static final Random RND = new Random();
    private static int beaconsHit = 0;
    private static int kosmosTicks = 0;
    private static int returnX = 0;
    private static int returnZ = 0;
    private static int prevInsomniaStartDay = 0;
    private static boolean prevKosmosDone = false;
    private static boolean devEntry = false; // вход через /test kosmos — не портит реальный Акт 4

    private static ServerLevel level(MinecraftServer server) {
        return server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation("animod", "kosmos")));
    }

    public static void enter(ServerPlayer player, AniSaveData data) {
        enter(player, data, false);
    }

    public static void enter(ServerPlayer player, AniSaveData data, boolean devTest) {
        MinecraftServer server = player.serverLevel().getServer();
        ServerLevel kosmos = level(server);
        if (kosmos == null) {
            player.sendSystemMessage(Component.literal("§c[Аня] Космос не отвечает... (измерение не найдено)"));
            return;
        }
        returnX = player.getBlockX();
        returnZ = player.getBlockZ();
        // dev-тест не должен ломать реальный Акт 4: запоминаем и возвращаем состояние
        devEntry = devTest;
        prevInsomniaStartDay = data.insomniaStartDay;
        prevKosmosDone = data.kosmosDone;
        data.kosmosPrevInsomniaDay = data.insomniaStartDay;
        data.kosmosPrevDone = data.kosmosDone;

        buildSector(kosmos);

        data.kosmosActive = true;
        data.setDirty();
        beaconsHit = 0;
        kosmosTicks = 0;
        data.kosmosBeaconsHit = 0;
        data.kosmosTicks = 0;
        data.kosmosReturnX = returnX;
        data.kosmosReturnZ = returnZ;

        player.teleportTo(kosmos, 0.5, 66, -6.0, 0.0F, 0.0F);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        ServerPlayNetworking.send(player, AniMod.KOSMOS_PACKET, buf);

        player.sendSystemMessage(Component.literal("§5§o*Кровать проваливается в бесконечность. Ты падаешь вверх — в чёрное небо с горящей дырой.*"));
        player.sendSystemMessage(Component.literal("§b[Задача] Найди 3 гравитационных маяка на астероидах и направь их лучи в чёрную дыру (ПКМ по маяку)."));
        // Канон v1: откровение Голоса Истины о Чёрной Дыре — субтитрами
        StoryCinema.send(player, "[Голос Истины]  То, что ты видишь — не дыра. Это место, где код забыл, как быть светом. Три маяка вернут тебе небо... если ты помнишь, зачем возвращаешься.", 220, 42);

        // Спокойная тема Космоса (пакет 987: авто-повтор после конца, стоп при выходе)
        sendKosmosMusic(player, true);
    }

    /** Тема Космоса: on=true включить, false — выключить. */
    private static void sendKosmosMusic(ServerPlayer player, boolean on) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(987);
        buf.writeInt(on ? 1 : 0);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);
    }

    /** Астероиды и маяки (генерируется один раз при входе). */
    private static void buildSector(ServerLevel kosmos) {
        // Стартовая платформа
        platform(kosmos, 0, 64, -8, 5, Blocks.OBSIDIAN);
        // Астероиды с маяками
        placeBeaconAsteroid(kosmos, 18, 70, 14);
        placeBeaconAsteroid(kosmos, -24, 76, 30);
        placeBeaconAsteroid(kosmos, 6, 84, 52);
        // Декоративные астероиды
        for (int i = 0; i < 8; i++) {
            platform(kosmos, RND.nextInt(70) - 35, 60 + RND.nextInt(35), RND.nextInt(70), 2 + RND.nextInt(3),
                    RND.nextBoolean() ? Blocks.END_STONE : Blocks.OBSIDIAN);
        }
    }

    private static void platform(ServerLevel level, int cx, int cy, int cz, int r, net.minecraft.world.level.block.Block block) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > r + 1) continue;
                BlockPos p = new BlockPos(cx + dx, cy, cz + dz);
                level.setBlock(p, block.defaultBlockState(), 3);
                for (int y = cy + 1; y <= cy + 12; y++) level.setBlock(new BlockPos(p.getX(), y, p.getZ()), Blocks.AIR.defaultBlockState(), 2);
                for (int y = cy - 4; y < cy; y++) level.setBlock(new BlockPos(p.getX(), y, p.getZ()), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static void placeBeaconAsteroid(ServerLevel level, int x, int y, int z) {
        platform(level, x, y, z, 4, Blocks.END_STONE);
        level.setBlock(new BlockPos(x, y + 1, z), Blocks.BEACON.defaultBlockState(), 3);
    }

    /** ПКМ по маяку в Космосе. Возвращает true, если клик обработан. */
    public static boolean onBeaconClick(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        if (player.serverLevel() != level(server)) return false;
        if (beaconsHit >= 3) return true;

        beaconsHit++;
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        player.sendSystemMessage(Component.literal("§bЛуч маяка №" + beaconsHit + " уходит в чёрную дыру... §7(" + (3 - beaconsHit) + " осталось)"));

        if (beaconsHit >= 3) {
            complete(server, player);
        }
        return true;
    }

    private static void complete(MinecraftServer server, ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§5§o*Три луча сходятся в точке абсолютной тьмы. Дыра сжимается... и гаснет.*"));
        server.execute(() -> {
            // Возврат в Оверворлд
            ServerLevel overworld = server.overworld();
            AniSaveData data = AniSaveData.getServerState(overworld);
            BlockPos safe = InsomniaManager.safeSurface(overworld, returnX, returnZ);
            player.teleportTo(overworld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.setDeltaMovement(0, 0, 0);
            player.fallDistance = 0;

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(false);
            ServerPlayNetworking.send(player, AniMod.KOSMOS_PACKET, buf);

            sendKosmosMusic(player, false); // тема глохнет — дальше только рассвет и колокол

            InsomniaManager.endInsomnia(server, data);
            // откат состояния — ТОЛЬКО для dev-входа (/test kosmos). Реальное прохождение
            // обязано оставить kosmosDone = true, иначе Акт 4 нельзя завершить.
            if (devEntry) {
                data.insomniaStartDay = prevInsomniaStartDay;
                data.kosmosDone = prevKosmosDone;
            }
            data.kosmosBeaconsHit = 0;
            data.kosmosTicks = 0;
            data.setDirty();
        });
    }

    public static void tick(MinecraftServer server, AniSaveData data) {
        if (!data.kosmosActive) return;
        ServerLevel kosmos = level(server);
        if (kosmos == null) return;

        // Перезапуск мира посреди Космоса: восстанавливаем счётчики из сохранения
        if (kosmosTicks == 0 && data.kosmosTicks > 0) {
            kosmosTicks = data.kosmosTicks;
            beaconsHit = data.kosmosBeaconsHit;
            returnX = data.kosmosReturnX;
            returnZ = data.kosmosReturnZ;
            prevInsomniaStartDay = data.kosmosPrevInsomniaDay;
            prevKosmosDone = data.kosmosPrevDone;
            devEntry = false; // после рестарта считаем вход настоящим
        }
        kosmosTicks++;
        data.kosmosTicks = kosmosTicks;
        data.kosmosBeaconsHit = beaconsHit;
        if (kosmosTicks % 200 == 0) data.setDirty();

        for (ServerPlayer p : kosmos.players()) {
            // Невесомость: падение почти не тянет вниз
            if (p.getDeltaMovement().y < -0.04) {
                p.setDeltaMovement(p.getDeltaMovement().x, -0.04, p.getDeltaMovement().z);
            }
            p.fallDistance = 0;
            // Прыжки ~5 блоков
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 4, false, false, false));
        }

        // Канон: полноценные 20 минут геймплея (1 игровой день) — потом всё равно вытаскивает
        if (kosmosTicks > 24000L) {
            ServerPlayer p = kosmos.players().isEmpty() ? null : kosmos.players().iterator().next();
            if (p != null) {
                p.sendSystemMessage(Component.literal("§7§o*Космос отпускает тебя... без маяков, в полусне.*"));
                complete(server, p);
            }
        }
    }
}
