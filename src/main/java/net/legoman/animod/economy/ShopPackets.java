package net.legoman.animod.economy;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AniSaveData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Синхронизация магазина и проклятий клиент ← сервер. */
public class ShopPackets {

    /** Полный синок: монеты, уровни прокачек, проклятие. open = открыть экран магазина. */
    public static void sendSync(ServerPlayer player, boolean open) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel().getServer().overworld());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(open);
        buf.writeInt(data.anyaCoins);
        buf.writeInt(Upgrades.level(data, 0));
        buf.writeInt(Upgrades.level(data, 1));
        buf.writeInt(Upgrades.level(data, 2));
        buf.writeInt(Upgrades.level(data, 3));
        buf.writeInt(data.activeCurse);
        buf.writeInt(net.legoman.animod.KarmaManager.get()); // для темы магазина под текущую модель Ани
        buf.writeBoolean(data.discountActive); // распродажа-приманка (Акт 9)
        ServerPlayNetworking.send(player, AniMod.SHOP_SYNC_PACKET, buf);
    }

    /** Лёгкий синк только проклятия (для визуала: пелена, аритмия). */
    public static void sendCurseSync(ServerPlayer player) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel().getServer().overworld());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(data.activeCurse);
        ServerPlayNetworking.send(player, AniMod.CURSE_SYNC_PACKET, buf);
    }
}
