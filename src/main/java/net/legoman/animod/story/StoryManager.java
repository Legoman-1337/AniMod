package net.legoman.animod.story;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.legoman.animod.AniMod;
import net.legoman.animod.AniSaveData;
import net.legoman.animod.KarmaManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class StoryManager {
    private static long lastWorldTime = -1L;

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        AniSaveData data = AniSaveData.getServerState(overworld);

        long currentTime = overworld.getDayTime();

        if (lastWorldTime == -1L) {
            lastWorldTime = currentTime;
            return;
        }

        long dayDiff = (currentTime / 24000L) - (lastWorldTime / 24000L);
        if (dayDiff > 0) {
            lastWorldTime = currentTime;
            advanceStoryDay(server, data, (int) dayDiff);
        } else if (currentTime < lastWorldTime) {
            lastWorldTime = currentTime;
        }

        tickCurrentState(server, data);
    }

    public static void advanceStoryDay(MinecraftServer server, AniSaveData data, int daysToAdd) {
        data.storyDay += daysToAdd;
        data.setDirty();
        applyDayLogic(server, data, data.storyDay);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase("legoman")) {
                player.sendSystemMessage(Component.literal("§8§o*Наступил " + data.storyDay + "-й день вашего пути с Аней...*"));
            }
        }
    }

    public static void applyDayLogic(MinecraftServer server, AniSaveData data, int day) {
        int karma = KarmaManager.get();
        StoryState oldState = data.getStoryState();
        StoryState newState;

        if (day <= 4) {
            newState = StoryState.NORMAL;
            data.anyaEventHp = 20.0;
        } else if (day <= 6) {
            newState = StoryState.ANYA_IS_SCARED;
            data.anyaEventHp = 20.0;
        } else if (day <= 8) {
            newState = StoryState.ANYA_IS_CRYING;
            data.anyaEventHp = 16.0;
        } else if (day <= 13) {
            if (karma >= 50) {
                newState = StoryState.ANYA_IS_WATCHING;
                data.anyaEventHp = 18.0;
            } else {
                newState = StoryState.ANYA_IS_ANGRY;
                data.anyaEventHp = 14.0;
            }
        } else if (day <= 17) {
            newState = StoryState.ANYA_IS_NOT_HERE;
            data.anyaEventHp = 14.0;
        } else if (!data.bossDefeated) {
            newState = StoryState.ANYA_BODY_EVENT;
            data.anyaEventHp = 10.0;
        } else {
            newState = StoryState.STORY_COMPLETED;
            data.anyaEventHp = 20.0;
        }

        data.setStoryState(newState);
        data.setDirty();

        if (oldState != newState) {
            triggerStateTransition(server, data, oldState, newState);
        }
    }

    public static void triggerStateTransition(MinecraftServer server, AniSaveData data, StoryState oldState, StoryState newState) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getName().getString().equalsIgnoreCase("legoman")) continue;

            if (newState == StoryState.ANYA_IS_CRYING) {
                // Накладываем 3D-тьму Вардена на 460 тиков (23 сек)
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 460, 0, false, false, false));

                // Пакет кат-сцены на клиент: 460 тиков (~23 секунды)
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeInt(newState.getId());
                buf.writeInt(460);
                ServerPlayNetworking.send(player, AniMod.STORY_CUTSCENE_PACKET, buf);

                player.sendSystemMessage(Component.literal("§8§o*Воздух внезапно стал леденящим... Совсем рядом раздался тихий, безысходный всхлип.*"));
            } else if (newState == StoryState.ANYA_IS_ANGRY) {
                player.sendSystemMessage(Component.literal("§4§o*Пространство вокруг содрогнулось. Что-то в её взгляде изменилось навсегда...*"));
            }
        }
    }

    private static void tickCurrentState(MinecraftServer server, AniSaveData data) {
        StoryState state = data.getStoryState();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getName().getString().equalsIgnoreCase("legoman")) continue;

            PlayerStoryHealthManager.applyHealthCap(player, data.anyaEventHp);

            switch (state) {
                case ANYA_IS_CRYING -> {
                    if (server.getTickCount() % 30 == 0) {
                        player.serverLevel().sendParticles(ParticleTypes.FALLING_WATER,
                                player.getX(), player.getY() + 1.8, player.getZ(), 2, 0.3, 0.2, 0.3, 0.01);
                    }
                }
                case ANYA_IS_ANGRY -> {
                    if (server.getTickCount() % 35 == 0) {
                        player.serverLevel().sendParticles(ParticleTypes.CRIMSON_SPORE,
                                player.getX(), player.getY() + 1.2, player.getZ(), 4, 0.5, 0.5, 0.5, 0.02);
                    }
                }
                default -> {}
            }
        }
    }
}