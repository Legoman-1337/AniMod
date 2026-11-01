package net.legoman.animod.economy;

import net.legoman.animod.AniSaveData;
import net.legoman.animod.KarmaManager;
import net.legoman.animod.story.StoryManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ВЫПРАШИВАНИЕ У АНИ (Часть 3 диздока), работает строго через чат («Аня, дай...»):
 *  - Акты 1-2: ценности просить нельзя (кирка/алмаз/меч и т.п.) → -5 кармы и отказ.
 *  - Поздние акты: еда и факелы выдаются спокойнее при карме >= 50.
 *  - Карма < 40: полный отказ на всё («Сам себе добывай»), штрафы за наглость.
 *  - Монеты: вежливо 5-10 монет, до 2 раз в день. Наглость (просьба 50+ или спам)
 *    → Аня злится, снимает карму и КОНФИСКУЕТ монеты.
 */
public class BeggingManager {

    private static final Random RANDOM = new Random();
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    // Суточные лимиты (runtime, переживать перезапуск не обязаны)
    private static final Map<UUID, Long> lastBegMs = new HashMap<>();
    private static final Map<UUID, int[]> dailyCounters = new HashMap<>(); // [0]=день, [1]=монеты, [2]=еда

    /** Вызывается из обработки чата. true = фраза распознана как выпрашивание, ИИ не отвечает. */
    public static boolean handle(ServerPlayer player, String rawText) {
        String norm = rawText.toLowerCase().replace('ё', 'е');
        if (!norm.contains("аня")) return false;
        if (!(norm.contains("дай") || norm.contains("подари") || norm.contains("выдай"))) return false;

        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        int karma = KarmaManager.get();
        int act = StoryManager.actIndexForDay(data.storyDay);

        // Карма < 40: полный отказ на всё
        if (karma < 40) {
            nag(player, 3);
            player.sendSystemMessage(Component.literal("§c[Аня] Сам себе добывай. Я тебе ничего не должна."));
            return true;
        }

        // Просьба ценностей в ранних актах — железо-кирка-алмазы под запретом
        boolean wantsValuable = norm.contains("алмаз") || norm.contains("кирк") || norm.contains("меч")
                || norm.contains("незерит") || norm.contains("брон") || norm.contains("тотем");
        if (wantsValuable && act <= 2) {
            nag(player, 5);
            player.sendSystemMessage(Component.literal("§c[Аня] Рано тебе такое просить. И не вздумай больше."));
            return true;
        }

        // Просьба монет
        if (norm.contains("монет")) {
            Matcher m = NUMBER.matcher(norm);
            int asked = 0;
            while (m.find()) asked = Math.max(asked, Integer.parseInt(m.group()));

            int[] counter = dailyCounters.computeIfAbsent(player.getUUID(), k -> new int[3]);
            long today = player.serverLevel().getDayTime() / 24000L;
            if (counter[0] != today) { counter[0] = (int) today; counter[1] = 0; counter[2] = 0; }

            // Наглость: просит 50+ монет или уже спамит
            if (asked >= 50) {
                int take = Math.min(data.anyaCoins, 10);
                data.anyaCoins -= take;
                data.setDirty();
                nag(player, 10);
                player.sendSystemMessage(Component.literal("§4[Аня] Наглеть перестань! §7Штраф -10 кармы" +
                        (take > 0 ? ", конфисковано " + take + " монет." : ".")));
                return true;
            }
            if (counter[1] >= 2) {
                // спам после двух подачек — наглость: −10 кармы и конфискация (канон Части 3)
                int take = Math.min(data.anyaCoins, 10);
                data.anyaCoins -= take;
                data.setDirty();
                nag(player, 10);
                player.sendSystemMessage(Component.literal("§4[Аня] Я же сказала — на сегодня всё! Наглость наказывается."
                        + (take > 0 ? " §7Конфисковано " + take + " монет." : "")));
                return true;
            }
            counter[1]++;
            int coins = 5 + RANDOM.nextInt(6); // 5-10
            CoinManager.add(player, coins, "подачка от Ани");
            player.sendSystemMessage(Component.literal("§d[Аня] Ладно... только потому, что ты хороший. Но это на сегодня всё."));
            return true;
        }

        // Еда и факелы: канон Части 3 — только в поздних актах (3+) и при карме >= 50
        boolean wantsFood = norm.contains("ед") || norm.contains("голод") || norm.contains("хлеб") || norm.contains("поест");
        boolean wantsTorch = norm.contains("факел") || norm.contains("факелов");
        if (wantsFood || wantsTorch) {
            if (act <= 2) {
                player.sendSystemMessage(Component.literal("§c[Аня] Не сейчас. Может быть, позже — когда я пойму, что ты мне не угрожаешь."));
                return true;
            }
            if (karma < 50) {
                player.sendSystemMessage(Component.literal("§c[Аня] Сначала заслужи моё доверие (карма 50+), потом проси еды."));
                return true;
            }
            int[] counter = dailyCounters.computeIfAbsent(player.getUUID(), k -> new int[3]);
            long today = player.serverLevel().getDayTime() / 24000L;
            if (counter[0] != today) { counter[0] = (int) today; counter[1] = 0; counter[2] = 0; }
            if (counter[2] >= 1) {
                player.sendSystemMessage(Component.literal("§7[Аня] Я тебе уже помогала сегодня. Не злоупотребляй."));
                return true;
            }
            counter[2]++;
            ItemStack stack = wantsFood ? new ItemStack(Items.BREAD, 6) : new ItemStack(Items.TORCH, 12);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            player.sendSystemMessage(Component.literal("§d[Аня] Держи. Только не привыкай."));
            return true;
        }

        // «Аня, дай...» без конкретики — пусть отвечает обычный ИИ в характере
        return false;
    }

    private static void nag(ServerPlayer player, int penalty) {
        Long last = lastBegMs.get(player.getUUID());
        long now = System.currentTimeMillis();
        if (last != null && now - last < 30_000L) penalty += 3; // спам в течение 30 секунд — больнее
        lastBegMs.put(player.getUUID(), now);
        KarmaManager.set(Math.max(0, KarmaManager.get() - penalty));
        player.sendSystemMessage(Component.literal("§c[Карма] -" + penalty + " за наглость."));
    }
}
