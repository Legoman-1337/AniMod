package net.legoman.animod.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legoman.animod.AniMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.WorldDataConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Автотест (для агента): если существует файл-флаг run/config/animod_autotest.txt,
 * клиент на титульном экране САМ создаёт свежий мир и заходит в него.
 * Свежий мир = чистые сюжетные флаги (канон: полный прогон в новом мире).
 * Используется после фейк-BSOD (игра закрывается) и для старта прогона без человека.
 */
public final class AutoTest {

    private static boolean attempted = false;

    private AutoTest() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void tick(Minecraft mc) {
        if (attempted || mc.screen == null || mc.level != null) return;
        if (!(mc.screen instanceof TitleScreen)) return;

        try {
            Path flag = mc.gameDirectory.toPath().resolve("config").resolve("animod_autotest.txt");
            if (!Files.exists(flag)) return;
        } catch (Exception e) {
            return;
        }
        attempted = true;

        try {
            String name = "AutoTest_" + (System.currentTimeMillis() / 1000 % 1000000);
            LevelSettings settings = new LevelSettings(
                    name, GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(
                    name,
                    settings,
                    new WorldOptions(new Random().nextLong(), true, false),
                    registryAccess -> WorldPresets.createNormalWorldDimensions(registryAccess));
            AniMod.LOGGER.info("[AutoTest] Создаю свежий мир {} для агентского прогона", name);
        } catch (Exception e) {
            AniMod.LOGGER.error("[AutoTest] Не удалось создать мир: ", e);
        }
    }
}
