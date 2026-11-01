package net.legoman.animod.economy;

import net.legoman.animod.AniSaveData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;

/**
 * Пул проклятий (Часть 2 диздока): 20% шанс словить одно проклятие, один раз, до босса.
 * 1 «Пелена Бездны» — зрение срезано на -50% (тёмная виньетка, клиент).
 * 2 «Оковы Сбоя» — постоянное замедление -20% (атрибут скорости).
 * 3 «Цифровая Аритмия» — сбивчивый пульс (клиент), на боссфайте — хардкор-режим.
 */
public class CurseManager {

    private static final Random RANDOM = new Random();
    public static final String[] CURSE_NAMES = {"", "Пелена Бездны", "Оковы Сбоя", "Цифровая Аритмия"};

    /** Розыгрыш при входе в акты 5-8 (вызывается из сетки дней). Один раз за мир. */
    public static void rollOnActEntry(MinecraftServer server, AniSaveData data) {
        if (data.curseRolled || data.activeCurse != 0) return;
        boolean hit = RANDOM.nextInt(5) == 0; // 20%
        ServerPlayer player = findPlayer(server);
        // игрок оффлайн — бросок НЕ сгорает: прокатится на следующий день/вход
        if (player == null) return;
        data.curseRolled = true; // шанс выпадает один раз: не повезло — проклятия нет
        data.setDirty();

        if (!hit) return;
        apply(server, data, 1 + RANDOM.nextInt(3), player);
    }

    public static void apply(MinecraftServer server, AniSaveData data, int curse, ServerPlayer player) {
        data.activeCurse = curse;
        data.setDirty();
        Upgrades.applyAttributes(player, data);
        ShopPackets.sendCurseSync(player);
        String msg = switch (curse) {
            case 1 -> "§8§o*Мир перед глазами сужается и темнеет. Пелена Бездны срезала половину твоего зрения...*";
            case 2 -> "§8§o*Тяжёлые оковы вплетаются в каждый шаг. Оковы Сбоя: -20% к скорости...*";
            default -> "§4§o*Тук... тук-тук... ТУК! Твоё сердце бьётся не в такт этому миру. Цифровая Аритмия.*";
        };
        player.sendSystemMessage(Component.literal(msg));
    }

    public static void remove(MinecraftServer server, AniSaveData data, ServerPlayer player) {
        data.activeCurse = 0;
        data.setDirty();
        Upgrades.applyAttributes(player, data);
        ShopPackets.sendCurseSync(player);
    }

    /** Для /test curse — повесить проклятие принудительно. */
    public static void force(ServerPlayer player, int curse) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        data.curseRolled = true;
        apply(player.serverLevel().getServer(), data, curse, player);
        player.sendSystemMessage(Component.literal("§7[Dev] Проклятие «" + CURSE_NAMES[curse] + "» повешено принудительно."));
    }

    private static ServerPlayer findPlayer(MinecraftServer server) {
        return net.legoman.animod.story.StoryPlayers.get(server);
    }
}
