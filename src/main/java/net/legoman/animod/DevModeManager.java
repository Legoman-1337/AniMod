package net.legoman.animod;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.legoman.animod.economy.CoinManager;
import net.legoman.animod.economy.CurseManager;
import net.legoman.animod.story.AcidRainManager;
import net.legoman.animod.story.BossManager;
import net.legoman.animod.story.InsomniaManager;
import net.legoman.animod.story.InterludeX5;
import net.legoman.animod.story.KosmosManager;
import net.legoman.animod.story.PlayerStoryHealthManager;
import net.legoman.animod.story.StoryManager;
import net.legoman.animod.story.StoryState;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Режим разработчика: /ani test [on|off] — включает/выключает доступ к команде /test.
 * Внутри режима доступна обычная команда /test со всеми быстрыми тестами актов.
 * Brigadier не умеет удалять команды после регистрации, поэтому /test после первого
 * включения остаётся зарегистрированной, но без режима — полностью заблокирована.
 */
public class DevModeManager {

    private static final Set<UUID> devPlayers = ConcurrentHashMap.newKeySet();
    private static boolean testCommandRegistered = false;

    public static boolean isEnabled(ServerPlayer player) {
        return player != null && devPlayers.contains(player.getUUID());
    }

    public static void onServerStopped() {
        devPlayers.clear();
        testCommandRegistered = false;
    }

    /** Вызывается из /ani test [on|off]. null = переключить. */
    public static void handleAniTest(ServerPlayer player, Boolean enable) {
        if (player == null) return;
        boolean current = devPlayers.contains(player.getUUID());
        boolean target = enable != null ? enable : !current;

        if (target && current) {
            player.sendSystemMessage(Component.literal("§e[Dev] Режим разработчика уже включён. Введи §f/test§e для списка."));
            ensureTestCommand(player.serverLevel().getServer());
            return;
        }
        if (!target && !current) {
            player.sendSystemMessage(Component.literal("§7[Dev] Режим разработчика уже выключен."));
            return;
        }

        if (target) {
            devPlayers.add(player.getUUID());
            player.sendSystemMessage(Component.literal(
                    "§a[Dev] Режим разработчика ВКЛЮЧЁН. Доступна команда §f/test§a (без аргументов — список всех тестов)."));
            ensureTestCommand(player.serverLevel().getServer());
        } else {
            devPlayers.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(
                    "§7[Dev] Режим разработчика ВЫКЛЮЧЕН. Команда /test заблокирована."));
        }
    }

    private static void ensureTestCommand(MinecraftServer server) {
        if (testCommandRegistered) return;
        testCommandRegistered = true;
        try {
            server.getCommands().getDispatcher().register(buildTestCommand());
            // Обновляем дерево команд у всех игроков, чтобы /test появилась в подсказках
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(p);
            }
        } catch (IllegalArgumentException ignored) {
            // Команда уже зарегистрирована — не страшно
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTestCommand() {
        return Commands.literal("test")
                .requires(src -> {
                    // консоль/агентский мост (права OP) допускаются без dev-режима
                    ServerPlayer p = src.getPlayer();
                    return p != null ? devPlayers.contains(p.getUUID()) : src.hasPermission(4);
                })
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) sendHelp(p);
                    return 1;
                })

                // === ПОГОДА ===
                .then(Commands.literal("rain").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        MinecraftServer server = p.serverLevel().getServer();
                        AniSaveData data = AniSaveData.getServerState(p.serverLevel());
                        if (data.acidRainActive) {
                            AcidRainManager.endAcidRain(server, data, false);
                            p.sendSystemMessage(Component.literal("§7[Dev] Кислотный дождь ВЫКЛЮЧЕН."));
                        } else {
                            AcidRainManager.startAcidRain(server, data);
                            p.sendSystemMessage(Component.literal("§7[Dev] Кислотный дождь ВКЛЮЧЁН. Встань под небо — 0.5 HP каждые 4 сек."));
                        }
                    }
                    return 1;
                }))

                // === ЭФФЕКТЫ ===
                .then(Commands.literal("hearts").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 24000, 0, false, true, true));
                        p.sendSystemMessage(Component.literal("§7[Dev] Выданы 2 золотых сердца (подарок Ани, Акт 2)."));
                    }
                    return 1;
                }))

                // === ЛУНЫ / НЕБО ===
                .then(Commands.literal("moon")
                        .then(Commands.literal("harvest").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            if (p != null) applyMoon(p, "harvest");
                            return 1;
                        }))
                        .then(Commands.literal("toxic").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            if (p != null) applyMoon(p, "toxic");
                            return 1;
                        }))
                        .then(Commands.literal("none").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            if (p != null) {
                                MinecraftServer server = p.serverLevel().getServer();
                                AniSaveData data = AniSaveData.getServerState(server.overworld());
                                data.noneMoonActive = true;
                                data.setDirty();
                                StoryManager.syncNoneSky(p, true);
                                p.sendSystemMessage(Component.literal("§7[Dev] Безлуние включено (Акт 8). Снимется на финале."));
                            }
                            return 1;
                        }))
                        .then(Commands.literal("blood").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            if (p != null) {
                                BossManager.syncBloodSky(p, true);
                                p.sendSystemMessage(Component.literal("§7[Dev] Кровавая луна включена (Акт 9)."));
                            }
                            return 1;
                        })))

                // === ИЗМЕРЕНИЯ / КАТ-СЦЕНЫ ===
                .then(Commands.literal("broken_script").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        StoryManager.debugEnterBrokenScript(p);
                        p.sendSystemMessage(Component.literal("§7[Dev] Телепорт в The Broken Script. Пройди мост к сундуку (Z 93) — сработают шёпоты."));
                    }
                    return 1;
                }))
                .then(Commands.literal("kosmos").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        AniSaveData data = AniSaveData.getServerState(p.serverLevel());
                        data.insomniaStartDay = data.storyDay - 2; // считать 3-й день бессонницы
                        data.setDirty();
                        KosmosManager.enter(p, data, true);
                    }
                    return 1;
                }))
                .then(Commands.literal("boss")
                        .then(Commands.literal("1").executes(ctx -> startBoss(ctx, 1)))
                        .then(Commands.literal("2").executes(ctx -> startBoss(ctx, 2)))
                        .then(Commands.literal("3").executes(ctx -> startBoss(ctx, 3)))
                        .executes(ctx -> startBoss(ctx, 1)))
                .then(Commands.literal("bosskill").executes(ctx -> {
                    // Тест концовок: мгновенное «убийство» босса без нанесения урона
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        AniSaveData data = AniSaveData.getServerState(p.serverLevel());
                        BossManager.killForTest(p.serverLevel().getServer(), data, p);
                        p.sendSystemMessage(Component.literal("§7[Dev] Босс «повержен» (тест). Смотри концовку по карме."));
                    }
                    return 1;
                }))
                .then(Commands.literal("rematch").executes(ctx -> {
                    // Реванш: бар текущего этапа на 100%, игрок снова на арене
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        AniSaveData data = AniSaveData.getServerState(p.serverLevel());
                        if (data.getStoryState() == StoryState.ANYA_BODY_EVENT && !data.bossDefeated) {
                            BossManager.rematch(p, data);
                        } else {
                            p.sendSystemMessage(Component.literal("§c[Dev] Боссфайта сейчас нет."));
                        }
                    }
                    return 1;
                }))
                .then(Commands.literal("clean").executes(ctx -> {
                    // Очистка стадий: гасит всё, что не относится к текущему акту
                    // (дождь, бессонница/Космос, безлуние, босс, сцена Бездны)
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        MinecraftServer server = p.serverLevel().getServer();
                        AniSaveData data = AniSaveData.getServerState(server.overworld());
                        StoryManager.cleanStages(server, data);
                        p.sendSystemMessage(Component.literal("§7[Dev] Стадии очищены: активен только контент текущего акта (день " + data.storyDay + ")."));
                    }
                    return 1;
                }))
                .then(Commands.literal("bsod").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                        buf.writeInt(990);
                        buf.writeInt(600);
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, AniMod.STORY_CUTSCENE_PACKET, buf);
                    }
                    return 1;
                }))

                // === ЭКОНОМИКА И ПРОКЛЯТИЯ ===
                .then(Commands.literal("coins").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        CoinManager.add(p, 500, "dev");
                        p.sendSystemMessage(Component.literal("§7[Dev] Выдано 500 монет. Магазин: /ani shop"));
                    }
                    return 1;
                }))
                .then(Commands.literal("curse")
                        .then(Commands.literal("slow").executes(ctx -> stubCurse(ctx.getSource(), 2)))
                        .then(Commands.literal("blind").executes(ctx -> stubCurse(ctx.getSource(), 1)))
                        .then(Commands.literal("arrhythmia").executes(ctx -> stubCurse(ctx.getSource(), 3))))

                // === КВЕСТЫ ===
                .then(Commands.literal("t3").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p != null) {
                        if (QuestManager.offerTier(p, 3)) {
                            p.sendSystemMessage(Component.literal("§7[Dev] Т3 квест выдан принудительно. Сдай его — запустится интерлюдия X.5 «Осколок памяти»."));
                        } else {
                            p.sendSystemMessage(Component.literal("§c[Dev] Не удалось: уже есть активное задание (/ani cleanQuest)."));
                        }
                    }
                    return 1;
                }));
    }

    private static int startBoss(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int stage) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p != null) {
            AniSaveData data = AniSaveData.getServerState(p.serverLevel());
            data.setStoryState(StoryState.ANYA_BODY_EVENT);
            data.anyaEventHp = 10.0;
            data.setDirty();
            PlayerStoryHealthManager.applyHealthCap(p, 10.0);
            BossManager.start(p.serverLevel().getServer(), data);
            BossManager.debugSetStage(p, data, stage);
        }
        return 1;
    }

    private static int stubCurse(CommandSourceStack source, int curseId) {
        ServerPlayer p = source.getPlayer();
        if (p != null) CurseManager.force(p, curseId);
        return 1;
    }

    /** Включает одну луну и гасит остальные (одновременно активно не больше одной). */
    private static void applyMoon(ServerPlayer player, String moon) {
        MinecraftServer server = player.serverLevel().getServer();
        AniSaveData data = AniSaveData.getServerState(server.overworld());
        server.overworld().setDayTime(18000L); // глубокая ночь для визуала

        // смена луны гасит кислотный дождь (иначе дождь портит тест лун)
        if (data.acidRainActive) {
            AcidRainManager.endAcidRain(server, data, false);
        }

        data.insomniaNightActive = false;
        data.toxicMoonActive = false;

        String text;
        switch (moon) {
            case "harvest" -> {
                data.insomniaNightActive = true;
                text = "§7[Dev] Медная луна (Harvest Moon) включена. Снимется сама на рассвете.";
            }
            case "toxic" -> {
                data.toxicMoonActive = true;
                text = "§7[Dev] Токсичная зелёная луна включена.";
            }
            default -> text = "§c[Dev] Неизвестная луна.";
        }
        data.setDirty();

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            StoryManager.syncInsomniaSky(sp, data.insomniaNightActive);
            StoryManager.syncToxicSky(sp, data.toxicMoonActive);
        }
        player.sendSystemMessage(Component.literal(text));
    }

    private static void sendHelp(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("§d=== ТЕСТЫ РАЗРАБОТЧИКА (/test) ==="));
        p.sendSystemMessage(Component.literal("§e/test rain §7— вкл/выкл кислотный дождь"));
        p.sendSystemMessage(Component.literal("§e/test hearts §7— 2 золотых сердца"));
        p.sendSystemMessage(Component.literal("§e/test moon harvest|toxic|none|blood §7— луны (none/blood — заготовки Актов 8/9)"));
        p.sendSystemMessage(Component.literal("§e/test broken_script §7— телепорт в Бездну к сундуку с шёпотами"));
        p.sendSystemMessage(Component.literal("§e/test kosmos §7— прыжок в Космос, 3 маяка (Акт 4; флаги Акта 4 восстановятся)"));
        p.sendSystemMessage(Component.literal("§e/test coins §7— +500 монет (магазин: /ani shop)"));
        p.sendSystemMessage(Component.literal("§e/test curse slow|blind|arrhythmia §7— повесить проклятие"));
        p.sendSystemMessage(Component.literal("§e/test t3 §7— принудительный Алмазный Т3 квест"));
        p.sendSystemMessage(Component.literal("§e/test boss §7— вызвать Воплощение Сбоя (Акт 9)"));
        p.sendSystemMessage(Component.literal("§e/test bsod §7— плашка аварийного сброса (Акт 7)"));
        p.sendSystemMessage(Component.literal("§7Служебные: §e/ani story setDay <n>§7, §e/ani story setState <СТАТУС>§7, §e/ani test off§7"));
    }
}
