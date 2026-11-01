package net.legoman.animod.economy;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AniSaveData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * ЭКОНОМИКА (Часть 2 диздока): Anya Coins.
 * Источники: моб +1, алмазная руда +3, древние обломки +10, подачка Ани.
 * Торговля с жителем (+1 с капом) требует отдельного миксина — будет позже.
 * При получении монет клиенту уходит coin_popup — всплывающий UI на 5 секунд.
 */
public class CoinManager {

    public static void add(ServerPlayer player, int amount, String reason) {
        if (amount <= 0) return;
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        data.anyaCoins += amount;
        data.setDirty();
        String suffix = (reason == null || reason.isEmpty()) ? "" : " §7(" + reason + ")";
        player.displayClientMessage(Component.literal(
                "§e+" + amount + " монет" + suffix + " §6[" + data.anyaCoins + "]"), true);

        // Всплывающий UI монет
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(amount);
        buf.writeInt(data.anyaCoins);
        ServerPlayNetworking.send(player, AniMod.COIN_POPUP_PACKET, buf);
    }

    /** Списать монеты (без сообщений). Возвращает false, если не хватает. */
    public static boolean trySpend(AniSaveData data, int amount) {
        if (data.anyaCoins < amount) return false;
        data.anyaCoins -= amount;
        data.setDirty();
        return true;
    }

    // Торговля с жителем: +1 за сделку, кап 10 монет в день (защита от абуза, канон).
    // Счётчик в AniSaveData: переживает перезапуск, кап не сбрасывается релогом.
    private static final int TRADE_DAILY_CAP = 10;

    public static void addTradeCoin(ServerPlayer player) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        int day = (int) (player.serverLevel().getDayTime() / 24000L);
        if (data.tradeCoinDay != day) {
            data.tradeCoinDay = day;
            data.tradeCoinCount = 0;
        }
        if (data.tradeCoinCount >= TRADE_DAILY_CAP) return;
        data.tradeCoinCount++;
        data.setDirty();
        add(player, 1, "торговля");
    }
}
