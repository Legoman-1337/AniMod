package net.legoman.animod.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Дев-наблюдение: скриншоты прямо из рендера игры (агент видит картинку без логов).
 *
 * Включить:  /cshot            — снимок сейчас (с сообщением в чат)
 *            /cshot auto 5     — авто-съёмка каждые 5 секунд
 *            /cshot stop       — выключить авто-съёмку
 * Файл-флаг: run/config/animod_autoshot.txt, первая строка = интервал в секундах
 * (агент создаёт файл сам, без команд в игре; файла нет — съёмки нет).
 *
 * Файлы: <gamedir>/screenshots/animod_shot_*.png, хранятся последние 20.
 */
public final class DevShot {

    private static final int KEEP_FILES = 20;

    private static int autoIntervalSec = 0;   // включено командой
    private static int flagIntervalSec = -1;  // включено файлом-флагом (-1 = нет файла)
    private static int cooldown = 0;
    private static long lastFlagCheck = 0L;

    private DevShot() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("cshot")
                        .executes(ctx -> {
                            take(ctx.getSource().getClient(), true);
                            ctx.getSource().sendFeedback(Component.literal("§7[Dev] Скриншот сохранён в screenshots/."));
                            return 1;
                        })
                        .then(literal("auto").then(argument("sec", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> {
                                    autoIntervalSec = IntegerArgumentType.getInteger(ctx, "sec");
                                    ctx.getSource().sendFeedback(Component.literal("§7[Dev] Авто-съёмка каждые " + autoIntervalSec + " сек."));
                                    return 1;
                                })))
                        .then(literal("stop").executes(ctx -> {
                            autoIntervalSec = 0;
                            ctx.getSource().sendFeedback(Component.literal("§7[Dev] Авто-съёмка выключена."));
                            return 1;
                        }))));

        ClientTickEvents.END_CLIENT_TICK.register(DevShot::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        // Файл-флаг проверяем раз в 2 секунды: агент включает/выключает съёмку снаружи
        long now = System.currentTimeMillis();
        if (now - lastFlagCheck > 2000) {
            lastFlagCheck = now;
            flagIntervalSec = readFlagInterval(mc);
        }

        int interval = autoIntervalSec > 0 ? autoIntervalSec : flagIntervalSec;
        if (interval <= 0) return;

        if (++cooldown < interval * 20) return;
        cooldown = 0;
        take(mc, false);
    }

    /** Интервал из файла-флага; -1 — файла нет (съёмка выключена). */
    private static int readFlagInterval(Minecraft mc) {
        try {
            Path flag = mc.gameDirectory.toPath().resolve("config").resolve("animod_autoshot.txt");
            if (!Files.exists(flag)) return -1;
            String first = Files.readString(flag).trim();
            if (first.isEmpty()) return -1;
            int sec = Integer.parseInt(first.split("\\s+")[0]);
            return sec > 0 ? sec : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static void take(Minecraft mc, boolean announce) {
        try {
            File gameDir = mc.gameDirectory;
            Path dir = gameDir.toPath().resolve("screenshots");
            Files.createDirectories(dir);
            // своё имя: авто-кадры отличаются от ванильных F2-скриншотов и чистятся прайнером
            String name = "animod_shot_"
                    + new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new java.util.Date())
                    + ".png";
            if (announce) {
                Screenshot.grab(gameDir, name, mc.getMainRenderTarget(),
                        c -> mc.execute(() -> mc.gui.getChat().addMessage(c)));
            } else {
                Screenshot.grab(gameDir, name, mc.getMainRenderTarget(), c -> {});
            }
            pruneOld(dir);
        } catch (Exception ignored) {
            // наблюдение не должно ронять игру
        }
    }

    private static void pruneOld(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> shots = files
                    .filter(p -> p.getFileName().toString().startsWith("animod_shot_"))
                    .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .toList();
            for (int i = 0; i < shots.size() - KEEP_FILES; i++) {
                Files.deleteIfExists(shots.get(i));
            }
        } catch (Exception ignored) {}
    }
}
