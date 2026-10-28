package net.legoman.animod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.Difficulty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
public class AnyaGifts {

    private final AnyaEntity anya;

    public final SimpleContainer mainInventory = new SimpleContainer(27);
    public final SimpleContainer giftInventory = new SimpleContainer(27);

    private boolean isProcessingGifts = false;

    private int flowerViolations = 0;
    private int mediumViolations = 0;
    private int manipViolations = 0;

    private long lastGiftDayTracker = -1;

    private int mediumGiftsToday = 0;
    private int expensiveGiftsToday = 0;

    public AnyaGifts(AnyaEntity anya) {
        this.anya = anya;
        this.giftInventory.addListener(container -> onGiftInventoryChanged());
    }

    public void resetCooldowns() {
        this.mediumGiftsToday = 0;
        this.expensiveGiftsToday = 0;
    }

    public double calculateEncumbrancePenalty() {
        double totalPenaltyPercent = 0.0;
        for (int i = 0; i < mainInventory.getContainerSize(); i++) {
            ItemStack stack = mainInventory.getItem(i);
            if (!stack.isEmpty()) {
                int count = stack.getCount();
                int maxStack = stack.getMaxStackSize();
                if (maxStack >= 64) totalPenaltyPercent += count * 0.35;
                else if (maxStack >= 32) totalPenaltyPercent += count * 0.50;
                else totalPenaltyPercent += count * 1.00;
            }
        }
        return totalPenaltyPercent;
    }

    public void openMainMenu(ServerPlayer player) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Основной инвентарь Ани");
            }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player p) {
                return ChestMenu.threeRows(id, playerInv, mainInventory);
            }
        });
    }

    public void openGiftMenu(ServerPlayer player) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Подарки для Ани");
            }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player p) {
                return ChestMenu.threeRows(id, playerInv, giftInventory);
            }
        });
    }

    private void onGiftInventoryChanged() {
        if (isProcessingGifts || anya.level().isClientSide) return;

        isProcessingGifts = true;
        try {
            Level level = anya.level();
            long currentDay = level.getDayTime() / 24000L;

            if (currentDay != lastGiftDayTracker) {
                lastGiftDayTracker = currentDay;
                mediumGiftsToday = 0;
                expensiveGiftsToday = 0;

                flowerViolations = 0;
                mediumViolations = 0;
                manipViolations = 0;
            }

            int currentFlowerCount = 0;
            int newFlowers = 0;
            int totalKarmaBonus = 0;

            Set<Item> currentFlowerTypes = new HashSet<>();

            boolean hasFlowerViolation = false;
            boolean hasMediumViolation = false;
            boolean hasManipViolation = false;
            boolean cooldownTriggered = false;

            int expensiveGivenNow = 0;
            int mediumGivenNow = 0;

            String successfulCategory = "";
            ItemStack visualItemToHold = ItemStack.EMPTY;

            List<Integer> offendingFlowerSlots = new ArrayList<>();
            List<Integer> validFlowerSlots = new ArrayList<>();
            List<ItemStack> itemsToReturn = new ArrayList<>();

            Player nearestPlayer = anya.level().getNearestPlayer(anya, 16.0);
            ServerPlayer serverPlayer = nearestPlayer instanceof ServerPlayer ? (ServerPlayer) nearestPlayer : null;

            for (int i = 0; i < giftInventory.getContainerSize(); i++) {
                ItemStack stack = giftInventory.getItem(i);
                if (stack.isEmpty()) continue;

                // --- ЗАГОТОВКА ДЛЯ БУДУЩИХ СЮЖЕТНЫХ ПРЕДМЕТОВ ---
                if (stack.hasCustomHoverName()) {
                    String customName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                    if (customName.contains("кулон") || customName.contains("письмо")) {
                        visualItemToHold = stack.copy();
                        giftInventory.setItem(i, ItemStack.EMPTY);

                        AnyaAI.addMemoryFact("Игрок передал мне особую сюжетную вещь: " + stack.getHoverName().getString() + ". Это важно для нас.", null);

                        sendMsgToOwner(serverPlayer, "§d[Аня] " + getAnyaResponse(KarmaManager.get(), "STORY_ITEM"));
                        if (!anya.isReacting()) anya.triggerReaction(2);

                        continue;
                    }
                }
                // ------------------------------------------------

                Item item = stack.getItem();

                if (isTrashGift(item)) {
                    hasManipViolation = true;
                    itemsToReturn.add(stack.copy());
                    giftInventory.setItem(i, ItemStack.EMPTY);
                    continue;
                }

                int specificExpensiveValue = getSpecificExpensiveValue(item);

                if (specificExpensiveValue > 0) {
                    if (expensiveGiftsToday + expensiveGivenNow < 1) {
                        totalKarmaBonus += getExpensiveBonus(specificExpensiveValue, level);
                        expensiveGivenNow++;
                        successfulCategory = getExpensiveCategoryName(item);
                        visualItemToHold = stack.copy();

                        if (stack.getCount() > 1) {
                            ItemStack excess = stack.copy();
                            excess.setCount(stack.getCount() - 1);
                            itemsToReturn.add(excess);
                        }
                        giftInventory.setItem(i, ItemStack.EMPTY);
                    } else {
                        itemsToReturn.add(stack.copy());
                        giftInventory.setItem(i, ItemStack.EMPTY);
                        cooldownTriggered = true;
                    }

                } else if (isMediumGift(item)) {
                    boolean isBroken = false;
                    if (stack.isDamageableItem()) {
                        float remainingDurabilityPercent = (float)(stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
                        if (remainingDurabilityPercent < 0.5f) {
                            isBroken = true;
                        }
                    }

                    if (isBroken) {
                        hasMediumViolation = true;
                        itemsToReturn.add(stack.copy());
                        giftInventory.setItem(i, ItemStack.EMPTY);
                    } else if (mediumGiftsToday + mediumGivenNow < 4) {
                        totalKarmaBonus += getMediumBonus(level);
                        mediumGivenNow++;
                        successfulCategory = getMediumCategoryName(item);
                        visualItemToHold = stack.copy();

                        if (stack.getCount() > 1) {
                            ItemStack excess = stack.copy();
                            excess.setCount(stack.getCount() - 1);
                            itemsToReturn.add(excess);
                        }
                        giftInventory.setItem(i, ItemStack.EMPTY);
                    } else {
                        itemsToReturn.add(stack.copy());
                        giftInventory.setItem(i, ItemStack.EMPTY);
                        cooldownTriggered = true;
                    }

                } else if (isFlower(item)) {
                    currentFlowerCount += stack.getCount();
                    if (!currentFlowerTypes.add(item) || stack.getCount() > 1) {
                        hasFlowerViolation = true;
                        offendingFlowerSlots.add(i);
                    } else {
                        validFlowerSlots.add(i);
                        if (!stack.hasTag() || !stack.getTag().getBoolean("anya_gifted")) {
                            newFlowers++;
                            stack.getOrCreateTag().putBoolean("anya_gifted", true);
                            successfulCategory = "FLOWER";
                            visualItemToHold = stack.copy();
                        }
                    }

                } else {
                    hasManipViolation = true;
                    itemsToReturn.add(stack.copy());
                    giftInventory.setItem(i, ItemStack.EMPTY);
                }
            }

            expensiveGiftsToday += expensiveGivenNow;
            mediumGiftsToday += mediumGivenNow;

            if (currentFlowerCount > 4) {
                hasFlowerViolation = true;
            }

            boolean gotPositive = false;
            int currentKarma = KarmaManager.get();

            if (totalKarmaBonus > 0) {
                KarmaManager.add(totalKarmaBonus);
                gotPositive = true;
                String reply = getAnyaResponse(currentKarma, successfulCategory);
                sendMsgToOwner(serverPlayer, "§d[Аня] " + reply + " (+" + totalKarmaBonus + " кармы)");
                AnyaAI.addMemoryFact("Игрок сделал мне хороший подарок (категория: " + successfulCategory + "). Я с радостью его приняла.", null);
            }

            if (newFlowers > 0 && !hasFlowerViolation) {
                int flowerBonus = getFlowerBonus(level) * newFlowers;
                KarmaManager.add(flowerBonus);
                gotPositive = true;
                String reply = getAnyaResponse(currentKarma, "FLOWER");
                sendMsgToOwner(serverPlayer, "§d[Аня] " + reply + " (+" + flowerBonus + " кармы)");
                AnyaAI.addMemoryFact("Игрок подарил мне красивые цветы. Я была очень тронута этим вниманием.", null);
            }

            if (gotPositive) {
                if (!anya.isReacting()) anya.triggerReaction(2);
                if (!visualItemToHold.isEmpty()) {
                    anya.showVisualGift(visualItemToHold);
                }
            }

            if (cooldownTriggered && !hasFlowerViolation && !hasMediumViolation && !hasManipViolation && !gotPositive) {
                sendMsgToOwner(serverPlayer, "§7[Аня] " + getAnyaResponse(currentKarma, "COOLDOWN"));
            }

            boolean gotNegative = false;

            if (hasFlowerViolation) {
                int penalty = getFlowerPenalty(level);
                KarmaManager.remove(penalty);
                sendMsgToOwner(serverPlayer, "§c[Аня] Я просила не дарить мне одинаковые цветы или больше 4-х штук! (-" + penalty + " кармы)");
                gotNegative = true;
                AnyaAI.addMemoryFact("Игрок нарушил мою просьбу и попытался подарить слишком много одинаковых цветов. Меня это огорчило.", null);

                for (int slot : offendingFlowerSlots) {
                    giftInventory.setItem(slot, ItemStack.EMPTY);
                }
                int validCount = 0;
                for (int slot : validFlowerSlots) {
                    validCount++;
                    if (validCount > 4) {
                        giftInventory.setItem(slot, ItemStack.EMPTY);
                    }
                }
            }

            if (hasMediumViolation) {
                int penalty = getMediumPenalty(level);
                KarmaManager.remove(penalty);
                sendMsgToOwner(serverPlayer, "§c[Аня] " + getAnyaResponse(currentKarma, "BROKEN") + " (-" + penalty + " кармы)");
                gotNegative = true;
                AnyaAI.addMemoryFact("Игрок попытался подарить мне сломанный хлам. Я отчитала его и вернула вещь обратно.", null);
            }

            if (hasManipViolation) {
                int penalty = getManipPenalty(level);
                KarmaManager.remove(penalty);
                sendMsgToOwner(serverPlayer, "§c[Аня] " + getAnyaResponse(currentKarma, "TRASH") + " (-" + penalty + " кармы)");
                gotNegative = true;
                AnyaAI.addMemoryFact("Игрок попытался подсунуть мне бесполезный мусор в качестве подарка. Я разозлилась и вернула его.", null);
            }

            if (gotNegative && !anya.isReacting()) {
                anya.triggerReaction(1);
            }

            if (!itemsToReturn.isEmpty()) {
                for (ItemStack returnedItem : itemsToReturn) {
                    if (serverPlayer != null) {
                        if (!serverPlayer.getInventory().add(returnedItem)) {
                            serverPlayer.drop(returnedItem, false);
                        }
                    } else {
                        anya.spawnAtLocation(returnedItem);
                    }
                }
            }

        } finally {
            isProcessingGifts = false;
        }
    }

    // --- МАТЕМАТИКА БАЛАНСА ПО СЛОЖНОСТЯМ ---
    private int getFlowerBonus(Level level) {
        if (level.getServer() != null && level.getServer().isHardcore()) return 1;
        if (level.getDifficulty() == Difficulty.HARD) return 1;
        return 2;
    }

    private int getMediumBonus(Level level) {
        if (level.getServer() != null && level.getServer().isHardcore()) return 1;
        if (level.getDifficulty() == Difficulty.HARD) return 1;
        return 2;
    }

    private int getExpensiveBonus(int base, Level level) {
        if (level.getServer() != null && level.getServer().isHardcore()) {
            return Math.max(1, Math.round(base * 0.7f)); // Срез 30% на хардкоре
        }
        if (level.getDifficulty() == Difficulty.HARD) {
            return Math.max(1, base - 1); // Небольшой срез на харде
        }
        return base;
    }

    private int getFlowerPenalty(Level level) {
        if (level.getServer() != null && level.getServer().isHardcore()) return 8;
        if (level.getDifficulty() == Difficulty.HARD) return 5;
        return 3;
    }

    private int getMediumPenalty(Level level) {
        if (level.getServer() != null && level.getServer().isHardcore()) return 6;
        if (level.getDifficulty() == Difficulty.HARD) return 3;
        return 2;
    }

    private int getManipPenalty(Level level) {
        if (level.getServer() != null && level.getServer().isHardcore()) return 7;
        if (level.getDifficulty() == Difficulty.HARD) return 4;
        return 2;
    }

    // --- ПРАВИЛЬНАЯ КЛАССИФИКАЦИЯ ПРЕДМЕТОВ ---
    private int getSpecificExpensiveValue(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);

        // Топ тир (8)
        if (rl != null && rl.getPath().contains("netherite")) return 8;
        if (item == Items.NETHER_STAR || item == Items.TOTEM_OF_UNDYING ||
                item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.DRAGON_EGG ||
                item == Items.ELYTRA || item == Items.DRAGON_HEAD) return 8;

        // Высокий тир (7)
        if (item instanceof RecordItem) return 7;
        if (item == Items.DIAMOND_BLOCK || item == Items.EMERALD_BLOCK ||
                item == Items.GOLD_BLOCK || item == Items.HEART_OF_THE_SEA ||
                item == Items.END_CRYSTAL) return 7;
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) return 7;

        // Хороший тир (5)
        if (item == Items.DIAMOND || item == Items.EMERALD ||
                item == Items.GOLDEN_APPLE || item == Items.ECHO_SHARD) return 5;

        return 0;
    }

    private String getExpensiveCategoryName(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        if (rl != null && rl.getPath().contains("netherite")) return "NETHERITE";
        if (item == Items.ELYTRA || item == Items.DRAGON_EGG || item == Items.DRAGON_HEAD ||
                item == Items.END_CRYSTAL || (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock)) return "END";
        return "RARE";
    }

    private boolean isMediumGift(Item item) {
        if (item instanceof TieredItem || item instanceof ArmorItem) return true;

        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        if (rl != null) {
            String name = rl.getPath();
            if (name.contains("ingot") || name.contains("ore") || name.contains("raw_")) return true;
        }

        return item == Items.LAPIS_LAZULI || item == Items.REDSTONE || item == Items.COAL ||
               item == Items.GLOWSTONE_DUST || item == Items.GHAST_TEAR || item == Items.BLAZE_POWDER || item == Items.BLAZE_ROD || item == Items.MAGMA_CREAM ||
                item == Items.NETHER_WART || item == Items.NETHER_BRICK ||
                item == Items.IRON_BLOCK || item == Items.IRON_NUGGET ||
                item == Items.AMETHYST_SHARD || item == Items.SLIME_BALL || item == Items.HONEYCOMB;
    }

    private String getMediumCategoryName(Item item) {
        if (item == Items.GLOWSTONE_DUST || item == Items.GHAST_TEAR ||
                item == Items.BLAZE_POWDER || item == Items.BLAZE_ROD || item == Items.MAGMA_CREAM ||
                item == Items.NETHER_WART || item == Items.NETHER_BRICK) return "NETHER";
        return "MEDIUM";
    }

    private boolean isFlower(Item item) {
        return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof FlowerBlock;
    }

    private boolean isTrashGift(Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        if (rl == null) return false;
        String path = rl.getPath();

        if (path.contains("seeds") || path.contains("leaves") || path.contains("dirt") ||
                path.contains("sand") || path.contains("gravel")) return true;

        return item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE || item == Items.POISONOUS_POTATO ||
                item == Items.BONE || item == Items.STRING || item == Items.COBWEB ||
                item == Items.GUNPOWDER || item == Items.COBBLESTONE;
    }

    private String getAnyaResponse(int karma, String category) {
        int band = AnyaAIPrompts.getKarmaBand(karma);
        switch (category) {
            case "STORY_ITEM":
                if (band < 3) return "Это... мне? С-спасибо. Я сохраню это.";
                return "Какая особенная вещь... Спасибо, я буду беречь её как зеницу ока.";
            case "FLOWER":
                if (band == 0) return "Завянет.";
                if (band == 1) return "И что мне с этим делать?";
                if (band == 2) return "Э-это мне? Ну ладно...";
                if (band == 3) return "Какой красивый... Спасибо.";
                if (band == 4) return "Спасибо! Он такой милый.";
                return "Цветок от тебя... Я засушу его и буду хранить вечно.";
            case "NETHERITE":
                if (band == 0) return "Твёрдый металл. Полезно.";
                if (band == 1) return "Где ты это достал? Ладно, заберу.";
                if (band == 2) return "П-подумаешь, незерит! Но... спасибо, пригодится.";
                if (band == 3) return "Ого, это же очень ценно... Ты уверен?";
                if (band == 4) return "Вау! Спасибо огромное за такой щедрый подарок!";
                return "Самое крепкое для наших чувств... Идеально.";
            case "END":
                if (band == 0) return "Эхо Пустоты.";
                if (band == 1) return "Вещи из Края? Давай сюда.";
                if (band == 2) return "Ты летал туда без меня?! Ладно, забираю.";
                if (band == 3) return "Там ведь очень опасно... Спасибо, что принёс.";
                if (band == 4) return "Даже дракон не остановит тебя! Спасибо!";
                return "Мы будем летать вместе, всегда...";
            case "RARE":
                if (band == 0) return "Редкий артефакт.";
                if (band == 1) return "Неплохо. Умеешь удивлять.";
                if (band == 2) return "Что?! Ты отдаёшь это мне?! Б-бака, я же потеряю!";
                if (band == 3) return "Невероятно... Я буду беречь это всем сердцем.";
                if (band == 4) return "Боже мой, это же легендарно! Ты лучший!";
                return "Ты отдал мне самую большую ценность... Я никогда тебя не предам.";
            case "NETHER":
                if (band == 0) return "Горячо.";
                if (band == 1) return "Снова из ада притащил? Сойдёт.";
                if (band == 2) return "Фу, пахнет серой! Но для крафта пойдёт.";
                if (band == 3) return "Из Незера? Надеюсь, ты там не обжёгся...";
                if (band == 4) return "Ресурсы из Незера! Ты такой смелый!";
                return "Адское пламя не сравнится с моей любовью...";
            case "MEDIUM":
                if (band == 0) return "Принято.";
                if (band == 1) return "Сойдёт.";
                if (band == 2) return "Ну, спасибо.";
                if (band == 3) return "Спасибо, пригодится.";
                if (band == 4) return "Спасибо! Очень полезно.";
                return "Всё, что от тебя — идеально.";
            case "TRASH":
                if (band == 0) return "Убери это.";
                if (band == 1) return "Не пытайся подкинуть мне этот мусор!";
                if (band == 2) return "Ты за кого меня держишь?! Сам таскай этот хлам!";
                if (band == 3) return "Эм... Извини, но мне это совсем не нужно.";
                if (band == 4) return "Давай не будем засорять инвентарь такими вещами.";
                return "Не отвлекай меня этим мусором от созерцания тебя!";
            case "BROKEN":
                if (band == 0) return "Сломано. Бесполезно.";
                if (band == 1) return "И зачем мне сломанный хлам?";
                if (band == 2) return "Эй, почини сначала, а потом дари!";
                if (band == 3) return "Оно же почти сломано... Извини, я верну.";
                if (band == 4) return "Ой, это же сломано. Оставь пока себе.";
                return "Даже если это сломано, я хочу... Нет, чини сам!";
            case "COOLDOWN":
                if (band < 3) return "Хватит мне это совать, лимит на сегодня исчерпан!";
                return "Спасибо, но на сегодня подарков достаточно. Давай завтра.";
            default:
                return "Спасибо.";
        }
    }

    private void sendMsgToOwner(ServerPlayer player, String msg) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(msg));
        }
    }

    public void save(CompoundTag tag) {
        ListTag mainList = new ListTag();
        for (int i = 0; i < mainInventory.getContainerSize(); i++) {
            ItemStack stack = mainInventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                stack.save(itemTag);
                mainList.add(itemTag);
            }
        }
        tag.put("MainInventory", mainList);

        ListTag giftList = new ListTag();
        for (int i = 0; i < giftInventory.getContainerSize(); i++) {
            ItemStack stack = giftInventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                stack.save(itemTag);
                giftList.add(itemTag);
            }
        }
        tag.put("GiftInventory", giftList);

        tag.putLong("LastGiftDayTracker", lastGiftDayTracker);
        tag.putInt("MediumGiftsToday", mediumGiftsToday);
        tag.putInt("ExpensiveGiftsToday", expensiveGiftsToday);

        tag.putInt("FlowerViolations", flowerViolations);
        tag.putInt("MediumViolations", mediumViolations);
        tag.putInt("ManipViolations", manipViolations);
    }

    public void load(CompoundTag tag) {
        isProcessingGifts = true;
        try {
            if (tag.contains("MainInventory")) {
                ListTag mainList = tag.getList("MainInventory", 10);
                for (int i = 0; i < mainList.size(); i++) {
                    CompoundTag itemTag = mainList.getCompound(i);
                    int slot = itemTag.getByte("Slot") & 255;
                    if (slot < mainInventory.getContainerSize()) {
                        mainInventory.setItem(slot, ItemStack.of(itemTag));
                    }
                }
            }

            if (tag.contains("GiftInventory")) {
                ListTag giftList = tag.getList("GiftInventory", 10);
                for (int i = 0; i < giftList.size(); i++) {
                    CompoundTag itemTag = giftList.getCompound(i);
                    int slot = itemTag.getByte("Slot") & 255;
                    if (slot < giftInventory.getContainerSize()) {
                        giftInventory.setItem(slot, ItemStack.of(itemTag));
                    }
                }
            }

            lastGiftDayTracker = tag.getLong("LastGiftDayTracker");
            mediumGiftsToday = tag.getInt("MediumGiftsToday");
            expensiveGiftsToday = tag.getInt("ExpensiveGiftsToday");

            flowerViolations = tag.getInt("FlowerViolations");
            mediumViolations = tag.getInt("MediumViolations");
            manipViolations = tag.getInt("ManipViolations");

        } finally {
            isProcessingGifts = false;
        }
    }
}