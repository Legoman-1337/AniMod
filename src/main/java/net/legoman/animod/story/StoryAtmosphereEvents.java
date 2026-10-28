package net.legoman.animod.story;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class StoryAtmosphereEvents {

    // 1. Записка с реальным временем компьютера игрока в инвентарь
    public static void giveRealTimeNote(ServerPlayer player) {
        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String dateStr = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = new CompoundTag();
        tag.putString("title", "...");
        tag.putString("author", "Аня");

        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf(
                "§0" + dateStr + "  " + timeStr + "\n\n" +
                        "§4Я вижу твои часы.§0\n\n" +
                        "Осталось играть совсем недолго...\n\n" +
                        "§8Не закрывай игру. Я всё равно тебя найду."
        ));
        tag.put("pages", pages);
        book.setTag(tag);

        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }

        player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 1.0f, 0.5f);
        player.sendSystemMessage(Component.literal("§8§o*В твоем инвентаре что-то появилось...*"));
    }

    // 2. Тушение факелов вокруг игрока (радиус 12 блоков)
    public static void extinguishTorches(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int extinguished = 0;

        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -4; dy <= 6; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.2f);
                        extinguished++;
                    }
                }
            }
        }
        if (extinguished > 0) {
            player.sendSystemMessage(Component.literal("§8*Сквозняк затушил свет вокруг...*"));
        }
    }

    // 3. Мгновенная грозовая ночь
    public static void suddenNight(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setDayTime(14000L); // ночь
        player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.AMBIENT, 1.0f, 0.8f);
        player.sendSystemMessage(Component.literal("§8§o*Мир внезапно погрузился во тьму...*"));
    }

    // 4. Фейковый выход и вход Ани в игру
    public static void fakePlayerLeave(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§eАня покинула игру"));
        player.serverLevel().getServer().execute(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(4000L);
                    player.sendSystemMessage(Component.literal("§eАня присоединилась к игре"));
                } catch (InterruptedException ignored) {}
            }).start();
        });
    }
}