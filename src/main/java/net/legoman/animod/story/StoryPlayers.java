package net.legoman.animod.story;

import net.legoman.animod.AniSaveData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Сюжетный игрок — БЕЗ привязки к нику (мод рассчитан на любых игроков).
 * Первый вошедший не-наблюдатель фиксируется в AniSaveData (storyPlayerUuid) и дальше
 * сюжет крутится вокруг него. Если он оффлайн — сюжетные события ждут его возврата
 * (никогда не перепрыгивают на другого игрока).
 */
public final class StoryPlayers {

    private StoryPlayers() {}

    /** Сюжетный игрок (или null, если он ещё не зафиксирован / оффлайн). */
    public static ServerPlayer get(MinecraftServer server) {
        AniSaveData data = AniSaveData.getServerState(server.overworld());

        if (data.storyPlayerUuid == null || data.storyPlayerUuid.isEmpty()) {
            // Фиксация: первый не-наблюдатель, вошедший в мир
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isSpectator()) continue;
                data.storyPlayerUuid = p.getUUID().toString();
                data.setDirty();
                return p;
            }
            return null;
        }

        try {
            return server.getPlayerList().getPlayer(UUID.fromString(data.storyPlayerUuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Проверка без фиксации: является ли игрок сюжетным (принимает и Player из событий). */
    public static boolean isStoryPlayer(Player player) {
        if (player == null || player.level().isClientSide() || !(player instanceof ServerPlayer sp)) return false;
        AniSaveData data = AniSaveData.getServerState((ServerLevel) sp.level());
        return sp.getUUID().toString().equals(data.storyPlayerUuid);
    }
}
