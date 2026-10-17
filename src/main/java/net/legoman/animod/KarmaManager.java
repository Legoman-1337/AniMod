package net.legoman.animod;

import org.slf4j.Logger;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class KarmaManager {

    private static final File KARMA_FILE = new File("anya_karma.txt");

    private static int karma = 50;
    private static Logger logger;

    public static void init(Logger modLogger) {
        logger = modLogger;
    }

    public static int get() {
        return karma;
    }

    // Установка кармы из сохранения мира без повторной записи.
    public static void setFromSave(int value) {
        karma = Math.max(0, Math.min(100, value));
    }

    public static void set(int value) {
        karma = Math.max(0, Math.min(100, value));
        save();
    }

    public static void add(int amount) {
        karma = Math.max(0, Math.min(100, karma + amount));
        save();
    }

    public static void remove(int amount) {
        karma = Math.max(0, Math.min(100, karma - amount));
        save();
    }

    // Используется только для миграции старого TXT-файла.
    public static int loadLegacyKarma() {
        if (!KARMA_FILE.exists()) {
            return 50;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(KARMA_FILE),
                        StandardCharsets.UTF_8))) {

            String line = reader.readLine();

            if (line != null) {
                return Math.max(0, Math.min(100, Integer.parseInt(line.trim())));
            }

        } catch (Exception e) {
            if (logger != null) {
                logger.error("Не удалось загрузить старую карму: {}", e.getMessage());
            }
        }

        return 50;
    }

    // Оставлен для совместимости, но теперь загружает в память безопасно
    public static void load() {
        setFromSave(loadLegacyKarma());
    }

    public static void save() {
        // Сохраняем карму исключительно в NBT-сохранение текущего мира.
        if (AniMod.currentServer != null) {
            AniSaveData data = AniSaveData.getServerState(
                    AniMod.currentServer.overworld()
            );

            data.karma = karma;
            data.setDirty();
        }

        // Запись в anya_karma.txt полностью удалена.
        // Файл теперь строго Read-Only архив, чтобы игра в одном мире
        // не перезаписывала глобальную легаси-карму для других старых миров.
    }
}