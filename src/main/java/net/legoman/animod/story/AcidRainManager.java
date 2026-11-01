package net.legoman.animod.story;

import net.legoman.animod.AniSaveData;
import net.legoman.animod.KarmaManager;
import net.legoman.animod.AnyaAI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * АКТ 2: «Зелёная хворь и слёзы».
 * Первый эмоциональный сбой Ани: весь день идёт токсичный зелёный дождь,
 * наносящий урон всем под открытым небом. Дождь останавливается, только если
 * игрок сделает комплимент в чат и задарит подарками минимум +20 кармы.
 * Одновременно с событием активно Toxic Sky (ядовитая луна/небо).
 */
public class AcidRainManager {

    /** Запуск события (одноразовый). Вызывается при переходе в ANYA_IS_SCARED. */
    public static void tryStartAcidRain(MinecraftServer server, AniSaveData data) {
        if (data.acidRainDone || data.acidRainActive) return;
        startAcidRain(server, data);
    }

    public static void startAcidRain(MinecraftServer server, AniSaveData data) {
        data.acidRainActive = true;
        data.toxicMoonActive = true;
        // состояние события живёт в сохранении мира: релог не должен обнулять фейлсейф
        data.acidRainKarmaAtStart = KarmaManager.get();
        data.acidRainCompliment = false;
        data.acidRainStartDayTime = server.overworld().getDayTime();
        data.setDirty();

        ServerLevel overworld = server.overworld();
        overworld.setWeatherParameters(1200, 0, true, false);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            StoryManager.syncToxicSky(p, true);
            p.sendSystemMessage(Component.literal("§8§o*Небо наливается ядовитой зеленью. Капли шипят, касаясь земли...*"));
            p.sendSystemMessage(Component.literal("§a[Аня] Я... я не понимаю, что происходит. Кажется, это я! Прости... прости меня!"));
        }

        AnyaAI.addMemoryFact("Я испугалась и мой сбой вызвал ядовитый кислотный дождь — мои эмоции сломали физику мира.", null);
    }

    public static void tick(MinecraftServer server, AniSaveData data) {
        checkNightTorches(server, data);
        if (!data.acidRainActive) return;

        ServerLevel overworld = server.overworld();

        // Держим дождь включённым, пока событие не завершено
        if (server.getTickCount() % 200 == 0 && !overworld.isRaining()) {
            overworld.setWeatherParameters(1200, 0, true, false);
        }

        // Урон под открытым небом: 0.5 HP (четверть сердца) каждые 4 секунды (магия — броня почти не режет)
        if (server.getTickCount() % 80 == 0) {
            if (!overworld.isRaining()) return; // дождь кончился — урона нет, даже если флаг ещё висит
            for (ServerPlayer p : overworld.players()) {
                if (p.level().dimension() != Level.OVERWORLD) continue;
                if (p.isCreative() || p.isSpectator()) continue;
                if (!p.level().canSeeSky(p.blockPosition())) continue;
                // канон юзера: 0.5 СЕРДЕЧКА (1.0 HP) каждые 4 секунды, ПОЛНОСТЬЮ незаметно
                // (без шипения и частиц) — «это просто дождь»
                p.hurt(p.damageSources().magic(), 1.0F);
            }
        }

        // Остановка: комплимент + +20 кармы (подарками) с момента начала события
        if (data.acidRainCompliment && KarmaManager.get() - data.acidRainKarmaAtStart >= 20) {
            endAcidRain(server, data, true);
            return;
        }

        // Фейлсейф: 2 игровых дня без утешения — Аня успокаивается сама
        if (overworld.getDayTime() - data.acidRainStartDayTime > 48000L) {
            endAcidRain(server, data, false);
        }
    }

    /** Игрок сделал комплимент (вызывается из обработки чата). */
    public static void onCompliment(ServerPlayer player) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        if (!data.acidRainActive || data.acidRainCompliment) return;
        data.acidRainCompliment = true;
        // карма от самого комплимента НЕ засчитывается в «+20 подарками» (канон):
        // точка отсчёта переносится на момент комплимента
        data.acidRainKarmaAtStart = KarmaManager.get();
        data.setDirty();
        player.sendSystemMessage(Component.literal(
                "§d[Аня] Ты... правда так думаешь? §o*всхлип*§r§d Дождь стал слабее... §7(Осталось задарить меня подарками: +20 кармы)"));
    }

    public static void endAcidRain(MinecraftServer server, AniSaveData data, boolean comforted) {
        data.acidRainActive = false;
        data.acidRainDone = true;
        data.toxicMoonActive = false;
        data.setDirty();

        ServerLevel overworld = server.overworld();
        overworld.setWeatherParameters(24000, 0, false, false);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            StoryManager.syncToxicSky(p, false);
            if (comforted) {
                p.sendSystemMessage(Component.literal("§d[Аня] Спасибо... Мне стало легче. Смотри — дождь остановился! Я смогла!"));
                p.sendSystemMessage(Component.literal("§e☀ Тучи расходятся, и солнце возвращается в мир."));
            } else {
                p.sendSystemMessage(Component.literal("§7§o*Дождь постепенно сошёл на нет. Аня замкнулась в себе и больше не говорит об этом...*"));
            }
        }

        if (comforted) {
            AnyaAI.addMemoryFact("Я испугалась и вызвала кислотный дождь, но игрок утешил меня комплиментом и подарками. Дождь прекратился.", null);
        }
    }

    /** Хоррор Акта 2: первую же ночь акта факелы сами гаснут вокруг игрока. */
    private static void checkNightTorches(MinecraftServer server, AniSaveData data) {
        if (data.acidTorchesDone) return;
        if (data.getStoryState() != StoryState.ANYA_IS_SCARED) return;

        ServerLevel overworld = server.overworld();
        long time = overworld.getDayTime() % 24000L;
        if (time < 13500L || time > 23000L) return;
        if (server.getTickCount() % 100 != 0) return;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!StoryPlayers.isStoryPlayer(p)) continue;
            data.acidTorchesDone = true;
            data.setDirty();
            StoryAtmosphereEvents.extinguishTorches(p);
            break;
        }
    }
}
