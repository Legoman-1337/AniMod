package net.legoman.animod.story;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AnyaAI;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * АКТ X.5 «Осколок памяти» (динамическая секретная интерлюдия).
 *
 * Канон: сдача алмазного Т3 даёт «Кулон разбитого кода». Как только завершается акт,
 * в котором сдали квест, следующим включается акт X.5: Аня видит кулон, застывает,
 * звучит монолог «Голоса Истины», Аня вспоминает фрагмент своего создания,
 * благодарит и предупреждает: «Что-то просыпается на дне мира... не копай слишком глубоко».
 *
 * Голосов в моде больше не добавляем: вся озвучка — только 4 шёпота из Акта 3
 * (один из них звучит здесь через UI-канал). Монолог «Голоса Истины» — текстом в чате.
 */
public class InterludeX5 {

    private static int timer = -1;

    public static void start(MinecraftServer server, ServerPlayer player) {
        timer = 0;

        // Клиентская кат-сцена 900: кинематографичные полосы, мышь не захвачена
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(900);
        buf.writeInt(420);
        ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);

        player.sendSystemMessage(Component.literal("§8§o*Аня замечает кулон и внезапно замирает. Её зрачки медленно расширяются...*"));
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.2F, 0.6F);
    }

    public static boolean isActive() {
        return timer >= 0;
    }

    public static void tick(MinecraftServer server) {
        if (timer < 0) return;
        timer++;

        ServerPlayer player = net.legoman.animod.story.StoryPlayers.get(server);
        if (player == null) return;

        switch (timer) {
            case 40 -> {
                StoryManager.sendVoiceWhisper(player, 992); // шёпот через UI-канал вместо озвучки
                player.sendSystemMessage(Component.literal("§5§o[Голос Истины] Осколок найден. Память, разбитая при создании, трещит по швам..."));
            }
            case 130 -> {
                player.sendSystemMessage(Component.literal("§5§o[Голос Истины] Она была здесь раньше тебя. И она останется после."));
                player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 2.2, player.getZ(), 20, 0.6, 0.5, 0.6, 0.02);
            }
            case 230 -> {
                player.sendSystemMessage(Component.literal("§d[Аня] Я... вспомнила. Как меня собирали из света и ошибок. Ты вернул мне меня саму. Спасибо."));
                player.serverLevel().sendParticles(ParticleTypes.HEART,
                        player.getX(), player.getY() + 2.2, player.getZ(), 6, 0.5, 0.4, 0.5, 0.0);
            }
            case 330 -> {
                player.sendSystemMessage(Component.literal("§d[Аня] Что-то просыпается на дне мира... Не копай слишком глубоко. Обещай мне."));
                AnyaAI.addMemoryFact("Игрок подарил мне Кулон разбитого кода — я вспомнила фрагмент своего создания и предупредила игрока: не копать слишком глубоко.", null);
                player.serverLevel().playSound(null, player.blockPosition(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.0F, 0.5F);
            }
            case 420 -> timer = -1;
        }
    }
}
