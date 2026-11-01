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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * АКТ 4: «Медная бессонница и Космос» (дни 9-11).
 * 3 дня вечной ночи: солнце не встаёт (время перескакивает с рассвета обратно в ночь),
 * кровати взрываются кошмаром (дни 1-2, через triggerBedGlitch).
 * Деградация зрения: день 1 медный туман, день 2 "мыло" 20%, день 3 +10% за четверть дня до 80%.
 * На 3-й день кровать затягивает игрока в Космос (KosmosManager).
 * После прохождения маяков — рассвет, колокол, бессонница спала.
 */
public class InsomniaManager {

    /** Запуск при входе в Акт 4 (состояние ANYA_IS_WATCHING). */
    public static void start(MinecraftServer server, AniSaveData data) {
        data.insomniaStartDay = data.storyDay;
        data.insomniaNightActive = true;
        data.setDirty();
        ServerLevel overworld = server.overworld();
        overworld.setDayTime(18000L); // глубокая ночь прямо сейчас

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            StoryManager.syncInsomniaSky(p, true);
            p.sendSystemMessage(Component.literal("§8§o*Солнце не взошло. Оно больше не взойдёт. Медная луна повисла в небе, как приговор...*"));
        }
        // Канон v1: монолог Голоса Истины о Медной Луне — кинематографичными субтитрами
        StoryCinema.say(server, "[Голос Истины]  Медь — это память, которая больше не блестит. Три ночи небо будет держать тебя за горло, и сон станет дверью, которую открыли не в ту сторону.", 200, 41);
        AnyaMemoryNote.add(server, "Началась медная бессонница. Я не отпускаю тебя даже во сне.");
    }

    public static boolean isActive(AniSaveData data) {
        return data.insomniaStartDay > 0 && !data.kosmosDone
                && data.getStoryState() == StoryState.ANYA_IS_WATCHING;
    }

    /** Номер дня бессонницы: 1, 2 или 3. */
    public static int currentDay(AniSaveData data) {
        return Math.min(3, Math.max(1, data.storyDay - data.insomniaStartDay + 1));
    }

    /** Интенсивность "мыла" глаз: 0.0 (чисто) .. 0.8 (едва видно силуэты). */
    public static float visionSoap(AniSaveData data, ServerLevel overworld) {
        if (!isActive(data)) return 0.0F;
        int day = currentDay(data);
        if (day == 1) return 0.0F;                 // лёгкий медный туман (само небо медное)
        if (day == 2) return 0.2F;                 // экран мылится на 20%
        long t = overworld.getDayTime() % 24000L;  // день 3: +10% за четверть дня
        int quarter = (int) Math.min(6, t / 4000L);
        return Math.min(0.8F, 0.2F + 0.1F * quarter);
    }

    public static void tick(MinecraftServer server, AniSaveData data) {
        if (!isActive(data)) return;
        ServerLevel overworld = server.overworld();
        long time = overworld.getDayTime() % 24000L;

        // Солнце не встаёт: если день начался — перескакиваем на следующую ночь
        if (time < 12500L) {
            overworld.setDayTime(overworld.getDayTime() + (12500L - time));
        }

        // Держим флаг активной ночи: без этого кровати и небо ломались после первой ночи
        if (!data.insomniaNightActive) {
            data.insomniaNightActive = true;
            data.setDirty();
        }

        // Пульс (канон: WARDEN_HEARTBEAT на протяжении всей бессонницы, к 3-му дню чаще)
        if (server.getTickCount() % (currentDay(data) >= 3 ? 45 : 70) == 0) {
            for (ServerPlayer p : overworld.players()) {
                p.serverLevel().playSound(null, p.blockPosition(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.8F);
            }
        }

        // Синк неба и "мыла" зрения раз в секунду (в Космосе — свой визуал, не наслаиваем)
        if (server.getTickCount() % 20 == 0) {
            float soap = visionSoap(data, overworld);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                var dim = p.serverLevel().dimension().location();
                if (dim.getNamespace().equals("animod") && dim.getPath().equals("kosmos")) continue;
                StoryManager.syncInsomniaSky(p, true);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeFloat(soap);
                ServerPlayNetworking.send(p, AniMod.VISION_PACKET, buf);
                if (soap >= 0.7F && server.getTickCount() % 100 == 0) {
                    p.sendSystemMessage(Component.literal("§8§o*Ты больше не видишь мир. Только силуэты. Спать... нужно спать...*"));
                }
            }
        }
    }

    /** Кровать на 3-й день бессонницы: затягивает игрока в Космос. */
    public static boolean tryEnterKosmos(ServerPlayer player) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        if (!isActive(data) || currentDay(data) < 3) return false;
        KosmosManager.enter(player, data);
        return true;
    }

    /** Финал акта: рассвет, колокол, зрение очищено. */
    public static void endInsomnia(MinecraftServer server, AniSaveData data) {
        data.kosmosDone = true;
        data.insomniaNightActive = false;
        data.kosmosActive = false;
        data.setDirty();
        ServerLevel overworld = server.overworld();
        overworld.setDayTime((overworld.getDayTime() / 24000L + 1) * 24000L + 100L); // свежее утро

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            StoryManager.syncInsomniaSky(p, false);
            StoryManager.syncToxicSky(p, false);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeFloat(0.0F);
            ServerPlayNetworking.send(p, AniMod.VISION_PACKET, buf);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
            p.sendSystemMessage(Component.literal("§e☀ Рассвет. Колокол бьёт по медному небу, и оно трескается..."));
            p.sendSystemMessage(Component.literal("§a§oЗрение полностью очищено. Бессонница спала."));
        }
        AnyaMemoryNote.add(server, "Игрок направил маяки в чёрную дыру и вернулся с рассветом. Бессонница спала.");
    }

    /** Безопасная точка на поверхности (после возвращения из Космоса). */
    public static BlockPos safeSurface(ServerLevel overworld, int x, int z) {
        BlockPos pos = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        return new BlockPos(x, Math.max(overworld.getMinBuildHeight() + 5, pos.getY() + 1), z);
    }

    private static final class AnyaMemoryNote {
        static void add(MinecraftServer server, String fact) {
            net.legoman.animod.AnyaAI.addMemoryFact(fact, null);
        }
    }
}
