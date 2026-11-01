package net.legoman.animod.story;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * КИНОМАТОГРАФ (критический апгрейд подачи): сюжетный текст идёт НЕ в чат, а
 * кинематографичными субтитрами — letterbox-полосы + текст снизу экрана с плавным
 * появлением (клиент: StoryScreenOverlay.showCinematic).
 * «Голос Истины», подписи сцен и ключевые реплики актов идут через say()/sayAll().
 */
public final class StoryCinema {

    private StoryCinema() {}

    /** Одна реплика сюжетному игроку. voiceId > 0 — реплика озвучивается (файл voice_N). */
    public static void say(MinecraftServer server, String text, int ticks) {
        say(server, text, ticks, 0);
    }

    public static void say(MinecraftServer server, String text, int ticks, int voiceId) {
        ServerPlayer p = StoryPlayers.get(server);
        if (p != null) send(p, text, ticks, voiceId);
    }

    /** Реплика конкретному игроку. */
    public static void send(ServerPlayer player, String text, int ticks) {
        send(player, text, ticks, 0);
    }

    public static void send(ServerPlayer player, String text, int ticks, int voiceId) {
        boolean can = ServerPlayNetworking.canSend(player, AniMod.CINEMATIC_TEXT_PACKET);
        AniMod.LOGGER.info("[Cinema] send={}, voice={}, text={}", can, voiceId, text.substring(0, Math.min(40, text.length())));
        if (can) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUtf(text, 512);
            buf.writeInt(ticks);
            buf.writeInt(voiceId);
            ServerPlayNetworking.send(player, AniMod.CINEMATIC_TEXT_PACKET, buf);
        }
    }
}
