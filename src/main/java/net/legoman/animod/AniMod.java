package net.legoman.animod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.GeckoLib;

import java.util.*;

import static net.legoman.animod.AnyaAIPrompts.normalize;

public class AniMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("animod");

    public static MinecraftServer currentServer;

    public static int getKarma() {
        return KarmaManager.get();
    }

    private static long lastComplimentMinecraftDay = Long.MIN_VALUE;
    private static long lastPeacefulHitKarmaTime = Long.MIN_VALUE;
    private static final long PEACEFUL_HIT_COOLDOWN_MS = 5L * 60L * 1000L;

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(AnyaEntity.ANYA, Mob.createMobAttributes());
        GeckoLib.initialize();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            AniSaveData data = AniSaveData.getServerState(server.overworld());

            if (!data.migrated) {
                if (server.overworld().getLevelData().getGameTime() == 0L) {
                    data.karma = 50;
                } else {
                    data.karma = KarmaManager.loadLegacyKarma();
                }
                data.migrated = true;
                data.setDirty();
            }

            KarmaManager.setFromSave(data.karma);

            AnyaAI.loadMemory(data.memory);
            LOGGER.info("AniMod: Состояние мира загружено (Карма: {}, Память: {} фактов)", data.karma, data.memory.size());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            currentServer = null;
            KarmaManager.setFromSave(50);
            AnyaAI.loadMemory(new ArrayList<>());
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String rawText = message.signedBody().content();
            String playerName = sender.getName().getString();

            if (playerName.equalsIgnoreCase("legoman")) {
                if (rawText.equalsIgnoreCase("!карма плюс")) {
                    KarmaManager.set(Math.min(100, KarmaManager.get() + 10));
                    sender.sendSystemMessage(Component.literal("§a[Система] Карма Ани повышена! Текущая: " + KarmaManager.get()));
                    drawKarmaActionBar(sender);
                    return;
                }
                if (rawText.equalsIgnoreCase("!карма минус")) {
                    KarmaManager.set(Math.max(0, KarmaManager.get() - 15));
                    sender.sendSystemMessage(Component.literal("§c[Система] Карма Ани упала! Текущая: " + KarmaManager.get()));
                    drawKarmaActionBar(sender);
                    return;
                }
            }

            String norm = normalize(rawText).replaceAll("[^а-яa-z0-9]", "");
            if (norm.isEmpty()) {
                return;
            }

            boolean blockedByJava = applyMessageKarma(sender, rawText);

            if (!blockedByJava) {
                AnyaAI.processAndSend(sender, rawText);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("ani")
                    .then(Commands.literal("traits").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        if (player != null) {
                            String trait = getTrait();
                            String color = getTraitColor();
                            player.sendSystemMessage(Component.literal("§7[Система] Состояние Ани: " + color + trait + " §7(Карма: " + KarmaManager.get() + ")"));
                        }
                        return 1;
                    }))
                    .then(Commands.literal("karma").then(Commands.literal("set").then(Commands.argument("number", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100)).executes(context -> {
                        int value = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "number");
                        ServerPlayer player = context.getSource().getPlayer();
                        KarmaManager.set(value);
                        if (player != null) {
                            drawKarmaActionBar(player);
                        }
                        context.getSource().sendSuccess(() -> Component.literal("§a[Система] Карма установлена на: " + KarmaManager.get()), false);
                        return 1;
                    }))))
                    .then(Commands.literal("cleanMessages").then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(context -> {
                        int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count");
                        int removed = AnyaAI.cleanMessages(count);
                        context.getSource().sendSuccess(() -> Component.literal("§e[Система] Удалено " + removed + " строк из истории диалога."), false);
                        return 1;
                    })))
                    .then(Commands.literal("cleanStory").then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(context -> {
                        int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count");
                        if (currentServer != null && context.getSource().getPlayer() != null) {
                            AniSaveData data = AniSaveData.getServerState(currentServer.overworld());
                            int removed = data.cleanStoryJournal(count);
                            context.getSource().sendSuccess(() -> Component.literal("§e[Система] Удалено " + removed + " записей из журнала сюжета."), false);
                        }
                        return 1;
                    })))
                    // КОМАНДЫ ТЕЛЕПОРТАЦИИ
                    .then(Commands.literal("tp")
                            .then(Commands.literal("call").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null && player.serverLevel() != null) {
                                    List<AnyaEntity> anyas = player.serverLevel().getEntitiesOfClass(AnyaEntity.class, player.getBoundingBox().inflate(500.0));
                                    if (!anyas.isEmpty()) {
                                        AnyaEntity anya = anyas.get(0);
                                        anya.teleportTo(player.getX(), player.getY(), player.getZ());
                                        context.getSource().sendSuccess(() -> Component.literal("§d[Система] Аня телепортирована к вам."), false);
                                    } else {
                                        context.getSource().sendSuccess(() -> Component.literal("§c[Система] Аня не найдена в радиусе 500 блоков."), false);
                                    }
                                }
                                return 1;
                            }))
                            .then(Commands.literal("to").executes(context -> {
                                ServerPlayer player = context.getSource().getPlayer();
                                if (player != null && player.serverLevel() != null) {
                                    List<AnyaEntity> anyas = player.serverLevel().getEntitiesOfClass(AnyaEntity.class, player.getBoundingBox().inflate(500.0));
                                    if (!anyas.isEmpty()) {
                                        AnyaEntity anya = anyas.get(0);
                                        player.teleportTo(anya.getX(), anya.getY(), anya.getZ());
                                        context.getSource().sendSuccess(() -> Component.literal("§d[Система] Вы телепортированы к Ане."), false);
                                    } else {
                                        context.getSource().sendSuccess(() -> Component.literal("§c[Система] Аня не найдена в радиусе 500 блоков."), false);
                                    }
                                }
                                return 1;
                            }))
                    )
            );
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && player.getName().getString().equalsIgnoreCase("legoman")) {
                if (entity instanceof Animal || entity instanceof Villager) {
                    long now = System.currentTimeMillis();
                    if (lastPeacefulHitKarmaTime == Long.MIN_VALUE || now - lastPeacefulHitKarmaTime >= PEACEFUL_HIT_COOLDOWN_MS) {
                        KarmaManager.set(Math.max(0, KarmaManager.get() - 3));
                        lastPeacefulHitKarmaTime = now;
                        player.sendSystemMessage(Component.literal("§c[Аня] Зачем ты бьешь мирных существ?! (-3 кармы)"));
                        AnyaAI.addMemoryFact("Игрок ударил мирное существо (" + entity.getType().getDescription().getString() + ")", null);
                    }
                }
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase("legoman")) {
                        drawKarmaActionBar(player);
                    }
                }
            }
            if (server.getTickCount() % 20 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equalsIgnoreCase("legoman")) {
                        AnyaAI.updateContextCache(player, true);
                    }
                }
            }
        });

        LOGGER.info("AniMod запущен");
    }

    private static String getTrait() {
        if (KarmaManager.get() < 20) return "Хоррор / Абсолютное безумие";
        if (KarmaManager.get() < 30) return "Обиженная";
        if (KarmaManager.get() < 50) return "Цундере";
        if (KarmaManager.get() < 75) return "Нейтральная";
        if (KarmaManager.get() < 90) return "Любящая";
        return "Яндере";
    }

    public static String getTraitColor() {
        if (KarmaManager.get() < 20) return "§4";
        if (KarmaManager.get() < 30) return "§d";
        if (KarmaManager.get() < 50) return "§6";
        if (KarmaManager.get() < 75) return "§f";
        if (KarmaManager.get() < 90) return "§a";
        return "§c";
    }

    private void drawKarmaActionBar(ServerPlayer player) {
        String face;
        if (KarmaManager.get() < 20) face = "\uE006";
        else if (KarmaManager.get() < 30) face = "\uE003";
        else if (KarmaManager.get() < 50) face = "\uE004";
        else if (KarmaManager.get() < 75) face = "\uE005";
        else if (KarmaManager.get() < 90) face = "\uE002";
        else face = "\uE001";
        int visual = Math.max(0, Math.min(100, KarmaManager.get()));
        int filled = visual / 10;
        StringBuilder bar = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "\uE011" : "\uE012");
        }
        player.sendSystemMessage(Component.literal("§f" + face + "  " + bar + "  §e" + KarmaManager.get()), true);
    }

    private boolean applyMessageKarma(ServerPlayer player, String text) {
        if (player == null || text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text).toLowerCase(Locale.ROOT).replace('ё', 'е');

        if (AnyaTextEmotion.isInsult(normalized)) {
            int old = KarmaManager.get();
            KarmaManager.set(Math.max(0, KarmaManager.get() - 3));
            if (KarmaManager.get() != old) {
                player.sendSystemMessage(Component.literal("§c[Аня] Это уже было лишним. (-3 кармы)"));
            }
            triggerAnyaReaction(player, 1);
            AnyaAI.addMemoryFact("Игрок разозлился и оскорбил меня.", text);
            return false;
        }

        if (AnyaTextEmotion.isCompliment(normalized)) {
            long minecraftDay = player.serverLevel().getDayTime() / 24000L;
            if (minecraftDay != lastComplimentMinecraftDay) {
                int old = KarmaManager.get();
                KarmaManager.set(Math.min(100, KarmaManager.get() + 3));
                lastComplimentMinecraftDay = minecraftDay;
                if (KarmaManager.get() != old) {
                    player.sendSystemMessage(Component.literal("§d[Аня] +3 кармы за тёплые слова 💗"));
                } else {
                    player.sendSystemMessage(Component.literal("§7[Аня] Я и так уже на максимальной карме."));
                }
                triggerAnyaReaction(player, 2);
            }
            AnyaAI.addMemoryFact("Игрок сделал мне приятный комплимент / признался в симпатии.", text);
            return false;
        }

        return false;
    }

    private void triggerAnyaReaction(ServerPlayer player, int type) {
        if (player == null) return;

        AABB area = player.getBoundingBox().inflate(32.0, 16.0, 32.0);
        List<AnyaEntity> anyas = player.serverLevel().getEntitiesOfClass(AnyaEntity.class, area);

        if (anyas.isEmpty()) return;

        AnyaEntity closest = null;
        double best = Double.MAX_VALUE;
        for (AnyaEntity anya : anyas) {
            double distance = anya.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                closest = anya;
            }
        }
        if (closest != null) {
            closest.triggerReaction(type);
        }
    }
}