package net.legoman.animod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Persisted, automatically offered collection quests with day-based scheduling.
 *
 * Timing uses whole-day numbers (overworld.getDayTime()/24000), so the counters
 * advance on every day tick, including /time add. Rating (karma) then reacts to
 * the quest being completed (+reward) or expiring after one quest-day (−penalty),
 * followed by a fixed 2 day pause before Anya offers the next quest.
 *
 * Tiers follow player-specified difficulty with 65% / 30% / 7% odds:
 *   Tier 1: early blocks (dirt, gravel, rare sand), coal, copper ore.
 *   Tier 2: tools/misc items (bucket, shield, shears, furnace, chest, torches),
 *           stone tools, rare iron items.
 *   Tier 3: iron armor/tools, crossbow, rare diamond gear (~10% combined).
 *
 * Each quest carries a weight; items marked "rare" have a lower weight so they
 * appear less often within their tier. When offering, a weighted random pick is
 * done among that tier's items.
 *
 * On completion the player also receives a random material reward:
 *   emeralds (3–5), bottles o' enchanting (8–10) or gold ingots (4–8).
 * A future story quest item is reserved as a second reward (not yet in the game).
 */
public final class QuestManager {
    private static final long DAY = 24_000L;
    private static final long FIRST_QUEST_GAP = 2L; // pause before the first quest on a fresh world
    private static final Random RANDOM = new Random();

    // Reserved for a future story quest item. It does not exist in the game yet,
    // so it is left null. When it is added, give the id here and the second
    // reward below will start granting it automatically.
    private static final String STORY_ITEM_ID = null;

    // Share of the diamond Tier-3 offer boosted to on the very first time a diamond T3 is offered in this world.
    private static final double BOOST_DIAMOND_PROBABILITY = 0.30;

    private record Quest(String item, int minTarget, int maxTarget, int tier, int weight) {}

    private record MaterialReward(String item, int min, int max) {}

    private static final List<Quest> QUESTS = List.of(
            // ---- Tier 1: early blocks, coal, copper ore. Sand is rare. ----
            new Quest("minecraft:dirt", 20, 20, 1, 5),
            new Quest("minecraft:gravel", 10, 10, 1, 5),
            new Quest("minecraft:sand", 24, 32, 1, 1),               // rare
            new Quest("minecraft:coal", 12, 24, 1, 5),
            new Quest("minecraft:copper_ore", 4, 8, 1, 5),

            // ---- Tier 2: misc/tools, stone tools; iron rare. ----
            new Quest("minecraft:bucket", 1, 1, 2, 4),
            new Quest("minecraft:shield", 1, 1, 2, 4),
            new Quest("minecraft:shears", 1, 1, 2, 4),
            new Quest("minecraft:furnace", 1, 1, 2, 4),
            new Quest("minecraft:chest", 1, 1, 2, 4),
            new Quest("minecraft:torch", 8, 16, 2, 4),
            new Quest("minecraft:stone_pickaxe", 1, 1, 2, 4),
            new Quest("minecraft:stone_shovel", 1, 1, 2, 4),
            new Quest("minecraft:stone_hoe", 1, 1, 2, 4),
            new Quest("minecraft:stone_axe", 1, 1, 2, 4),
            new Quest("minecraft:iron_ingot", 3, 6, 2, 1),            // rare

            // ---- Tier 3: iron gear & crossbow; diamonds rare (~10%). ----
            new Quest("minecraft:iron_chestplate", 1, 1, 3, 5),
            new Quest("minecraft:iron_leggings", 1, 1, 3, 5),
            new Quest("minecraft:crossbow", 1, 1, 3, 4),
            new Quest("minecraft:iron_pickaxe", 1, 1, 3, 5),
            new Quest("minecraft:iron_sword", 1, 1, 3, 4),
            new Quest("minecraft:diamond_sword", 1, 1, 3, 1),         // rare
            new Quest("minecraft:diamond_pickaxe", 1, 1, 3, 1),       // rare
            new Quest("minecraft:diamond_chestplate", 1, 1, 3, 1)     // rare
    );

    /** Random material reward granted on quest completion, alongside karma. */
    private static final List<MaterialReward> MATERIAL_REWARDS = List.of(
            new MaterialReward("minecraft:emerald", 3, 5),
            new MaterialReward("minecraft:experience_bottle", 8, 10),
            new MaterialReward("minecraft:gold_ingot", 4, 8)
    );

    private static final String[] REQUESTS = {
            "собери мне %d %s, не спрашивай зачем",
            "ты должен собрать для меня %d %s",
            "собери-ка мне %d %s",
            "мне нужно, чтобы ты добыл %d %s, и не задавай вопросов",
            "принеси мне %d %s, не спрашивай почему"
    };

    private static final Map<String, String> RU_NAMES = Map.ofEntries(
            Map.entry("minecraft:dirt", "дёрна"),
            Map.entry("minecraft:gravel", "гравия"),
            Map.entry("minecraft:sand", "песка"),
            Map.entry("minecraft:coal", "угля"),
            Map.entry("minecraft:copper_ore", "медной руды"),
            Map.entry("minecraft:bucket", "ведро"),
            Map.entry("minecraft:shield", "щит"),
            Map.entry("minecraft:shears", "ножницы"),
            Map.entry("minecraft:furnace", "печь"),
            Map.entry("minecraft:chest", "сундук"),
            Map.entry("minecraft:torch", "факелов"),
            Map.entry("minecraft:stone_pickaxe", "каменную кирку"),
            Map.entry("minecraft:stone_shovel", "каменную лопату"),
            Map.entry("minecraft:stone_hoe", "каменную мотыгу"),
            Map.entry("minecraft:stone_axe", "каменный топор"),
            Map.entry("minecraft:iron_ingot", "железных слитков"),
            Map.entry("minecraft:iron_chestplate", "железный нагрудник"),
            Map.entry("minecraft:iron_leggings", "железные поножи"),
            Map.entry("minecraft:crossbow", "арбалет"),
            Map.entry("minecraft:iron_pickaxe", "железную кирку"),
            Map.entry("minecraft:iron_sword", "железный меч"),
            Map.entry("minecraft:diamond_sword", "алмазный меч"),
            Map.entry("minecraft:diamond_pickaxe", "алмазную кирку"),
            Map.entry("minecraft:diamond_chestplate", "алмазный нагрудник"));

    private QuestManager() {}

    /** The current whole-day number of the overworld (grows on day tick, incl. /time add). */
    private static long dayNow(MinecraftServer server) {
        return server.overworld().getDayTime() / DAY;
    }

    private static long currentDay(ServerPlayer player) {
        return player.serverLevel().getDayTime() / DAY;
    }

    /**
     * Called on every server tick (AniMod throttles it to 1 Hz). Advances timers on day
     * changes, updates collection progress, settles completed/failed quests and offers the
     * next quest after the 2 day pause. Self-healing: works from older saves too.
     */
    public static void tick(MinecraftServer server) {
        AniSaveData data = AniSaveData.getServerState(server.overworld());
        ServerPlayer player = findQuestPlayer(server);
        long day = dayNow(server);
        if (data.lastDayNumber == 0L) { // bootstrap / cold start
            data.lastDayNumber = day;
            // Fresh worlds start with the same pause before the first quest.
            if (data.questItem.isEmpty() && data.gapDaysLeft <= 0) data.gapDaysLeft = (int) FIRST_QUEST_GAP;
            data.setDirty();
        }
        long delta = day - data.lastDayNumber;
        data.lastDayNumber = day;
        // Live collection progress; completion resolves the quest immediately (reward + gap).
        if (player != null && !data.questItem.isEmpty() && data.questDaysLeft > 0) {
            updateCollection(player, data);
        }

        // Advance day-counters independently of each other, so that a large jump
        // (e.g. /time add 3d) fails the quest AND consumes the pause in the same
        // tick, letting a fresh quest be offered immediately afterwards.
        if (delta > 0) {
            if (!data.questItem.isEmpty() && data.questDaysLeft > 0) {
                long used = Math.min(delta, data.questDaysLeft);
                data.questDaysLeft -= used;
                if (data.questDaysLeft <= 0) {
                    applyPenalty(player, data); // clears quest and reschedules the 2 day gap
                }
            }
            if (data.gapDaysLeft > 0) {
                data.gapDaysLeft -= (int) Math.min(delta, data.gapDaysLeft);
            }
            data.setDirty();
        }

        // Offer a new quest when nothing is active and the pause has elapsed.
        if (player != null && data.questItem.isEmpty() && data.questDaysLeft <= 0 && data.gapDaysLeft <= 0) {
            offerRandom(player, data, day);
        }
    }

    /** Counts how much of the target item the player collected since the offer started from zero. */
    private static void updateCollection(ServerPlayer player, AniSaveData data) {
        Item item = itemFor(data.questItem);
        int count = countInventory(player, item);
        int collected = Math.max(0, count - data.questBaseline);
        int progress = Math.min(data.questTarget, collected);
        if (progress != data.questProgress) {
            data.questProgress = progress;
            data.setDirty();
        }
        if (progress >= data.questTarget) {
            completeQuest(player, data);
        }
    }

    /** Quest fulfilled: grant karma reward + material reward, clear fields, start pause. */
    private static void completeQuest(ServerPlayer player, AniSaveData data) {
        int reward = rewardFor(data.questTier);
        KarmaManager.set(Math.min(100, KarmaManager.get() + reward));
        String itemName = itemLocalName(data.questItem);
        player.sendSystemMessage(Component.literal("§a[Аня] Спасибо, что принёс " + itemName + "! +" + reward + " кармы."));
        AnyaAI.addMemoryFact("Игрок выполнил квест Ани (тир " + data.questTier + "): " + data.questTarget + " " + itemName + ". Карма +" + reward + ".", null);

        // Additional material reward (gold, emeralds, exp bottles) is granted only for a
        // successfully completed diamond Tier-3 quest. It is not granted for iron T3, T2 or T1.
        if (data.questTier == 3 && isDiamond(data.questItem)) {
            grantMaterialReward(player);
        }
        // Reserved (future) story item, granted regardless of tier.
        grantStoryItem(player, data, itemName);

        // Remember the first-ever diamond Tier-3 completion: this world's odds drop back to ~10%.
        if (data.questTier == 3 && isDiamond(data.questItem)) {
            data.diamondT3Completed = true;
        }

        data.questItem = "";
        data.questTarget = 0; data.questProgress = 0; data.questTier = 0; data.questBaseline = 0;
        data.questDaysLeft = 0;
        scheduleGap(data);
        data.setDirty();
    }

    /** Gives a random emerald/exp-bottle/gold-ingot reward and returns its Russian name for the chat line. */
    private static String grantMaterialReward(ServerPlayer player) {
        MaterialReward r = MATERIAL_REWARDS.get(RANDOM.nextInt(MATERIAL_REWARDS.size()));
        int amount = r.min + RANDOM.nextInt(r.max - r.min + 1);
        giveOrDrop(player, itemFor(r.item), amount);
        String name = materialLocalName(r.item, amount);
        player.sendSystemMessage(Component.literal("§a[Аня] Держи награду: " + name + " ×" + amount + "."));
        return name;
    }

    /**
     * Second reward slot. Reserved for a future story quest item.
     * No-op while it does not exist (STORY_ITEM_ID == null); grant it automatically
     * as soon as the id is set. The line is intentionally kept per-reward.
     */
    private static void grantStoryItem(ServerPlayer player, AniSaveData data, String questItemName) {
        if (STORY_ITEM_ID != null && !STORY_ITEM_ID.isEmpty()) {
            Item item = itemFor(STORY_ITEM_ID);
            if (!itemFor("minecraft:air").equals(item)) {
                giveOrDrop(player, item, 1);
                player.sendSystemMessage(Component.literal("§b[Аня] И это тебе за " + questItemName + ": сюжетный предмет."));
            }
        }
    }

    /** Adds the stack to the player's inventory, or drops it at their feet if the inventory is full. */
    private static void giveOrDrop(ServerPlayer player, Item item, int amount) {
        if (!player.getInventory().add(new ItemStack(item, amount))) {
            player.drop(new ItemStack(item, amount), false);
        }
    }

    /** Quest not done within one quest-day: apply the failure penalty and start the pause. */
    private static void applyPenalty(ServerPlayer player, AniSaveData data) {
        if (data.questItem.isEmpty()) return;
        int penalty = penaltyFor(data);
        KarmaManager.set(Math.max(0, KarmaManager.get() - penalty));
        String itemName = itemLocalName(data.questItem);
        player.sendSystemMessage(Component.literal("§c[Аня] Я просила тебя, но ты так и не принёс " + itemName + ". −" + penalty + " кармы."));
        AnyaAI.addMemoryFact("Игрок не выполнил квест Ани (тир " + data.questTier + "): " + data.questTarget + " " + itemName + ". Карма −" + penalty + ".", null);
        data.questItem = "";
        data.questTarget = 0; data.questProgress = 0; data.questTier = 0; data.questBaseline = 0;
        data.questDaysLeft = 0;
        scheduleGap(data);
        data.setDirty();
    }

    private static void scheduleGap(AniSaveData data) {
        data.gapDaysLeft = 2; // fixed 2 day pause after completion/failure
    }

    /** Manual command: immediately offer a quest of the given tier, resetting the counters. */
    public static boolean offerTier(ServerPlayer player, int tier) {
        if (tier < 1 || tier > 3) return false;
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        if (data.questDaysLeft > 0 && !data.questItem.isEmpty()) return false; // quest already active
        List<Quest> choices = QUESTS.stream().filter(q -> q.tier == tier).toList();
        if (choices.isEmpty()) return false;
        data.questDaysLeft = 1;
        data.gapDaysLeft = 0;
        data.lastDayNumber = currentDay(player);
        setQuest(data, player, pickWeighted(choices, tier == 3 && !data.diamondT3Completed), tier);
        return true;
    }

    /** Discards the currently active quest (if any) and starts the 2 day pause. */
    public static boolean cleanQuest(ServerPlayer player) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        if (data.questDaysLeft <= 0) return false;
        data.questDaysLeft = 0;
        data.questItem = ""; data.questTarget = 0; data.questProgress = 0;
        data.questTier = 0; data.questBaseline = 0;
        scheduleGap(data);
        data.setDirty();
        return true;
    }

    public static void sendStatus(ServerPlayer player) {
        AniSaveData data = AniSaveData.getServerState(player.serverLevel());
        if (data.questDaysLeft <= 0 || data.questItem.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7[Аня] Активного задания сейчас нет; " +
                    (data.gapDaysLeft > 0
                            ? ("я предложу следующее через " + data.gapDaysLeft + " дн.")
                            : "я предложу его в ближайшее время.")));
            return;
        }
        player.sendSystemMessage(Component.literal("§d[Аня] " + itemLocalName(data.questItem) +
                ": " + data.questProgress + "/" + data.questTarget + " (собрано с нуля, ещё ~1 дн. на выполнение)."));
    }

    private static void offerRandom(ServerPlayer player, AniSaveData data, long day) {
        double roll = RANDOM.nextDouble();
        int tier = roll < 0.65 ? 1 : roll < 0.93 ? 2 : 3;
        List<Quest> choices = QUESTS.stream().filter(q -> q.tier == tier).toList();
        if (choices.isEmpty()) return;
        data.questDaysLeft = 1; // must be done within one quest-day
        data.gapDaysLeft = 0;
        data.lastDayNumber = day;
        setQuest(data, player, pickWeighted(choices, tier == 3 && !data.diamondT3Completed), tier);
    }

    /** Weighted random pick so "rare" items (low weight) appear less often in their tier.
        On the very first diamond Tier-3 offer in this world, diamond items are boosted to
        30%, with the rest shared among the other Tier-3 items proportionally. */
		private static Quest pickWeighted(List<Quest> choices, boolean boostDiamondT3) {
		if (!hasDiamond(choices)) {
			// T1/T2 contain no diamonds: plain weighted pick by item weight.
			int total = 0;
			for (Quest q2 : choices) total += q2.weight;
			int roll = RANDOM.nextInt(total);
			int acc = 0;
			for (Quest q : choices) { acc += q.weight; if (roll < acc) return q; }
			return choices.get(choices.size() - 1);
		}
		// T3 (contains diamonds): diamond vs iron split. On the very first diamond T3
		// offer in this world diamonds are boosted to 30% (iron 70%); after the player has
		// completed a diamond T3 they drop to 10% (iron 90%). Shares split by weights.
		double diamondTarget = boostDiamondT3 ? BOOST_DIAMOND_PROBABILITY : 0.10;
		int diamonds = 0;
		int others = 0;
		for (Quest q : choices) {
			if (isDiamond(q.item)) diamonds += q.weight;
			else others += q.weight;
		}
		double[] weights = new double[choices.size()];
		for (int i = 0; i < choices.size(); i++) {
			Quest q = choices.get(i);
			weights[i] = isDiamond(q.item)
				? (double) q.weight / diamonds * diamondTarget
				: (double) q.weight / others * (1.0 - diamondTarget);
		}
		double accW = 0;
		double rollD = RANDOM.nextDouble();
		for (int i = 0; i < choices.size(); i++) {
			accW += weights[i];
			if (rollD < accW) return choices.get(i);
		}
		return choices.get(choices.size() - 1);
	}

private static boolean hasDiamond(List<Quest> choices) {
		for (Quest q : choices) if (isDiamond(q.item)) return true;
		return false;
	}

	private static boolean isDiamond(String id) {
		return id.startsWith("minecraft:diamond");
	}
    /** Sets up the quest fields, records the baseline, and prints Anya's offer with a tier label. */
    private static void setQuest(AniSaveData data, ServerPlayer player, Quest quest, int tier) {
        int target = quest.minTarget + RANDOM.nextInt(quest.maxTarget - quest.minTarget + 1);
        data.questItem = quest.item;
        data.questTarget = target;
        data.questTier = tier;
        data.questBaseline = countInventory(player, itemFor(quest.item)); // collection starts from zero
        data.questProgress = 0;
        data.setDirty();
        String request = REQUESTS[RANDOM.nextInt(REQUESTS.length)];
        player.sendSystemMessage(Component.literal("§d[Аня] Legoman, " +
                String.format(request, target, itemLocalName(quest.item)) + "."));
        // New line: just the tier label (Т1 / Т2 / Т3), no extra words.
        player.sendSystemMessage(Component.literal("§e[Т" + tier + "]"));
    }

    private static int rewardFor(int tier) {
        switch (tier) {
            case 3: return 20;
            case 2: return 10;
            default: return 5;
        }
    }

    private static int penaltyFor(AniSaveData data) {
        boolean low = KarmaManager.get() < 80;
        switch (data.questTier) {
            case 3: return low ? 20 : 25;
            case 2: return low ? 15 : 20;
            default: return low ? 10 : 15;
        }
    }

    private static Item itemFor(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        return item == null ? BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:air")) : item;
    }

    private static String itemLocalName(String id) {
        String ru = RU_NAMES.get(id);
        if (ru != null) return ru;
        Item item = itemFor(id);
        String desc = item.getDescriptionId(); // e.g. block.minecraft.cobblestone
        int dot = desc.lastIndexOf('.');
        return dot >= 0 ? desc.substring(dot + 1) : desc;
    }

    private static String materialLocalName(String id, int amount) {
        switch (id) {
            case "minecraft:emerald": return "изумруд" + (amount % 10 == 1 && amount % 100 != 11 ? "" : "ов");
            case "minecraft:experience_bottle": return "пузырёк опыта" + (amount % 10 == 1 && amount % 100 != 11 ? "" : "ов");
            case "minecraft:gold_ingot": return "золотой слиток" + (amount % 10 == 1 && amount % 100 != 11 ? "" : "а");
            default: return itemLocalName(id);
        }
    }

    private static int countInventory(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(item)) count += stack.getCount();
        for (ItemStack stack : player.getInventory().armor) if (stack.is(item)) count += stack.getCount();
        return count;
    }

    private static ServerPlayer findQuestPlayer(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (player.getName().getString().equalsIgnoreCase("legoman")) return player;
        return null;
    }
}
