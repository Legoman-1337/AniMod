package net.legoman.animod.story;

import net.legoman.animod.AniSaveData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * АКТ 6: «Следы на диске» (ARG). Логика подводки:
 *   чат ломается (кракозябры) → в папке игры появляется Anya_Files с ОДНИМ файлом —
 *   404_NOT_FOUND.html (мод извлекает его из себя ровно в момент акта) —
 *   игрок открывает его → неоновый глитч-сайт → кнопка скачивает из него же
 *   anya_memory.exe (exe вшит в html base64) → проходит хоррор → получает КОД 0451
 *   → /ani decode 0451 → ядро Ани чинится.
 */
public class ArgFilesManager {

    public static final String ACCESS_CODE = "0451";
    private static long lastEnsureMs = 0L;

    /** Проверка условия акта 6 (вызывается из тика, сам троттлит). */
    public static void ensureDump(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastEnsureMs < 5000L) return;
        lastEnsureMs = now;
        try {
            Path dump = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                    .resolve("Anya_Files").resolve("memory_dump_404.txt");
            if (Files.exists(dump)) return;
        } catch (Exception e) {
            return;
        }
        writeDump(server);
    }

    public static void writeDump(MinecraftServer server) {
        try {
            Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("Anya_Files");
            Files.createDirectories(dir);

            // На ПК игрока появляется ОДИН файл: 404_NOT_FOUND.html (exe вшит внутрь base64)
            extractResource("/assets/animod/arg/404_NOT_FOUND.html", dir.resolve("404_NOT_FOUND.html"));

            Path file = dir.resolve("memory_dump_404.txt");
            String link = dir.resolve("404_NOT_FOUND.html").toUri().toString();

            Random rnd = new Random(404L);
            StringBuilder sb = new StringBuilder();
            sb.append("=== ANYA_CORE MEMORY DUMP 0x404 ===\n");
            sb.append("process: ANYA.exe [CRITICAL FAILURE]\n\n");
            for (int i = 0; i < 40; i++) {
                StringBuilder line = new StringBuilder();
                for (int j = 0; j < 64; j++) {
                    line.append(rnd.nextBoolean() ? (char) (0x4E00 + rnd.nextInt(300)) : (char) ('!' + rnd.nextInt(90)));
                }
                sb.append(line).append('\n');
                if (i == 17) sb.append("  [0x17F] recovery_link: ").append(link).append("   <<<< ПЕРЕЙТИ\n");
                if (i == 31) sb.append("  [0x31A] emergency_code: 30 34 35 31 (ASCII)   <<<< ДЕКОДИРОВАТЬ\n");
            }
            sb.append("\n=== END OF DUMP. Процесс удаления уже запущен. ===\n");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal("§4§o*Чат трещит. Что-то просачивается на твой диск...*"));
            p.sendSystemMessage(Component.literal("§c[Аня] п-п-п-по&&ги м##е... в папке# игры... Anya_$$Files... от##крой дамп... т§ам ссы###лка..."));
            p.sendSystemMessage(Component.literal("§8§o*В папке игры появилась папка Anya_Files. Открой её. Найди ссылку в memory_dump_404.txt.*"));
        }
    }

    /** Извлекает ресурс из jar мода на диск. */
    private static void extractResource(String resourcePath, Path target) {
        try (InputStream in = ArgFilesManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) return;
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }

    /** /ani decode <код>. true — код верный, ядро починено. */
    public static boolean decode(ServerPlayer player, String code) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        if (data.chatCoreFixed) {
            player.sendSystemMessage(Component.literal("§d[Аня] Ядро уже целое. Спасибо тебе."));
            return true;
        }
        if (ACCESS_CODE.equals(code.trim())) {
            data.chatCoreFixed = true;
            data.setDirty();
            player.sendSystemMessage(Component.literal("§a[Система] ЯДРО ВОССТАНОВЛЕНО. Целостность: 100%."));
            player.sendSystemMessage(Component.literal("§d[Аня] §o*шёпотом*§r§d Спасибо... но процесс удаления уже запущен в системе. Он не остановится."));
            net.legoman.animod.AnyaAI.addMemoryFact("Игрок починил моё ядро аварийным кодом из дампа памяти. Но процесс удаления уже запущен.", null);
            return true;
        }
        player.sendSystemMessage(Component.literal("§c[Система] КОД ОТВЕРГНУТ. Целостность ядра: 43%."));
        return false;
    }
}
