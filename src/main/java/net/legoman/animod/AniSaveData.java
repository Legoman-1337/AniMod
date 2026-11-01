package net.legoman.animod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AniSaveData extends SavedData {
    public int karma = 50;
    public boolean migrated = false;
    public String worldUuid = "";
    public String activeAnyaUuid = "";
    public List<String> memory = new ArrayList<>();
    public List<String> storyJournal = new ArrayList<>();
    public String questItem = "";
    public int questTarget = 0;
    public int questProgress = 0;
    public int questTier = 0;
    public int questBaseline = 0;
    public int questDaysLeft = 0;
    public int gapDaysLeft = 0;
    public long lastDayNumber = 0;
    public boolean diamondT3Completed = false;

    // === СЮЖЕТНАЯ СИСТЕМА ===
    public int storyDay = 1;
    public int storyStateId = 0;
    public double anyaEventHp = 20.0;
    public boolean bossDefeated = false;
    public boolean insomniaNightActive = false;

    // === АКТ-ДИРИЖЁР: акты кончаются ДЕЛОМ (goal), дни — только фейлсейф ===
    public int currentAct = 1;                  // текущий акт (90 = интерлюдия X.5)
    public int actStartDay = 1;                 // день начала текущего акта
    public int x5NextAct = 0;                   // куда идти после интерлюдии
    public boolean act3SceneDone = false;       // сцена Бездны сыграна до выброса

    // === АКТ 2: Кислотный дождь и Токсичная луна ===
    public boolean acidRainActive = false;
    public boolean acidRainDone = false;
    public boolean acidTorchesDone = false;
    public boolean toxicMoonActive = false;

    // === АКТ X.5 «Осколок памяти» (динамическая интерлюдия) ===
    public boolean storyPendantGiven = false;   // выдан ли Кулон разбитого кода (за алмазную Т3)
    public int pendantAct = 0;                  // акт, в котором сдали Т3
    public boolean interludeX5Done = false;     // сыграна ли интерлюдия

    // === ЭКОНОМИКА: монеты, магазин прокачек, проклятия ===
    public int anyaCoins = 0;                   // Anya Coins
    public int upBlade = 0;                     // Острота клинка 0-3 (+30% урона за уровень)
    public int upSlasher = 0;                   // Орб-слэшер 0-3 (боссфайт)
    public int upAgility = 0;                   // Ловкость 0-3 (+10% скорости за уровень)
    public int upLucky = 0;                     // Счастливчик 0-3 (рулетка HP на боссе)
    public int activeCurse = 0;                 // 0 нет, 1 Пелена Бездны, 2 Оковы Сбоя, 3 Цифровая Аритмия
    public boolean curseRolled = false;         // проклятье разыгрывается один раз до босса

    // === АКТ 4-10: бессонница, Космос, ядро Ани, BSOD, финал ===
    public int insomniaStartDay = 0;            // день, с которого началась бессонница (0 = не начата)
    public boolean kosmosDone = false;          // маяки в Космосе пройдены
    public boolean chatCoreFixed = false;       // ядро починено кодом /ani decode (Акт 6)
    public boolean bsodDone = false;            // Акт 7 сыгран
    public boolean noneMoonActive = false;      // Акт 8: луны/звёзд нет
    public boolean kosmosActive = false;        // игрок в Космосе (для синка неба)

    // === АКТ 9-10: фейковая победа, распродажа-приманка, возврат босса ===
    public boolean fakeBossVictory = false;     // фаза 1 убита, игрок думает что победил
    public int discountStartDay = 0;            // день начала скидки -20%
    public int discountEndDay = 0;              // день истечения скидки = ВОЗВРАТ босса
    public boolean discountActive = false;      // скидка сейчас действует

    // === АКТ 3: сцена Бездны и карманное измерение (переживает перезапуск мира) ===
    public String act3Victim = "";              // UUID игрока в сцене ("" = сцены нет)
    public int act3CutsceneTimer = 0;
    public boolean act3Burial = false;          // идёт бурение шахты
    public int act3BurialX = 0;
    public int act3BurialZ = 0;
    public int act3DigY = 0;
    public boolean act3InPocket = false;        // игрок в The Broken Script
    public int act3PocketTicks = 0;
    public boolean act3ChestTriggered = false;
    public boolean act3PocketStunned = false;   // ступор после взятия часов

    // === АКТ 2: состояние события кислотного дождя (переживает перезапуск) ===
    public long acidRainStartDayTime = 0L;      // dayTime старта (фейлсейф 48000)
    public int acidRainKarmaAtStart = 0;        // карма на старте (для «+20 подарками»)
    public boolean acidRainCompliment = false;  // комплимент уже сделан

    // === АКТ 4: Космос (переживает перезапуск) ===
    public int kosmosBeaconsHit = 0;
    public int kosmosTicks = 0;
    public int kosmosReturnX = 0;
    public int kosmosReturnZ = 0;
    public int kosmosPrevInsomniaDay = 0;       // для отката dev-входа /test kosmos
    public boolean kosmosPrevDone = false;

    // === АКТ 7: подтверждение сброса (плашку можно перепоказать до подтверждения) ===
    public boolean bsodConfirmed = false;

    // === СЮЖЕТНЫЙ ИГРОК: фиксируется при первом входе, привязки к нику нет ===
    public String storyPlayerUuid = "";

    // === ЭКОНОМИКА: дневной кап монет за торговлю ===
    public int tradeCoinDay = 0;
    public int tradeCoinCount = 0;

    // === АКТ 9: этапное и глобальное HP босса (персистент между перезапусками) ===
    public int bossStage = 0;                   // текущий этап 1-3 (0 = боя нет)
    public double bossStageHp = 0.0;            // остаток этапного HP
    public double bossGlobalHp = 0.0;           // сегменты глобального HP (3 → 0)

    private final List<AnyaLocation> anyaLocations = new ArrayList<>();

    public static final class AnyaLocation {
        public final UUID uuid;
        public String dimension;
        public double x;
        public double y;
        public double z;

        public AnyaLocation(UUID uuid, String dimension, double x, double y, double z) {
            this.uuid = uuid;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public List<AnyaLocation> getAnyaLocations() {
        return new ArrayList<>(anyaLocations);
    }

    public AnyaLocation getActiveAnyaLocation() {
        if (activeAnyaUuid != null && !activeAnyaUuid.isEmpty()) {
            for (AnyaLocation loc : anyaLocations) {
                if (loc.uuid.toString().equals(activeAnyaUuid)) {
                    return loc;
                }
            }
        }
        if (!anyaLocations.isEmpty()) {
            return anyaLocations.get(anyaLocations.size() - 1);
        }
        return null;
    }

    public void setSoleAnya(UUID uuid, String dimension, double x, double y, double z) {
        this.anyaLocations.clear();
        this.anyaLocations.add(new AnyaLocation(uuid, dimension, x, y, z));
        this.activeAnyaUuid = uuid.toString();
        setDirty();
    }

    public void updateAnyaLocation(AnyaEntity anya) {
        UUID uuid = anya.getUUID();
        this.activeAnyaUuid = uuid.toString();
        String dimension = anya.level().dimension().location().toString();

        AnyaLocation entry = null;
        for (AnyaLocation candidate : anyaLocations) {
            if (candidate.uuid.equals(uuid)) {
                entry = candidate;
                break;
            }
        }

        if (entry == null) {
            anyaLocations.add(new AnyaLocation(uuid, dimension, anya.getX(), anya.getY(), anya.getZ()));
            setDirty();
            return;
        }

        boolean shouldSave = !entry.dimension.equals(dimension)
                || Double.compare(entry.x, anya.getX()) != 0
                || Double.compare(entry.y, anya.getY()) != 0
                || Double.compare(entry.z, anya.getZ()) != 0;

        entry.dimension = dimension;
        entry.x = anya.getX();
        entry.y = anya.getY();
        entry.z = anya.getZ();
        if (shouldSave) setDirty();
    }

    public void removeAnyaLocation(UUID uuid) {
        if (anyaLocations.removeIf(entry -> entry.uuid.equals(uuid))) {
            if (activeAnyaUuid != null && activeAnyaUuid.equals(uuid.toString())) {
                activeAnyaUuid = anyaLocations.isEmpty() ? "" : anyaLocations.get(anyaLocations.size() - 1).uuid.toString();
            }
            setDirty();
        }
    }

    public static AniSaveData load(CompoundTag tag) {
        AniSaveData data = new AniSaveData();
        if (tag.contains("karma")) data.karma = Math.max(0, Math.min(100, tag.getInt("karma")));
        data.migrated = tag.getBoolean("migrated");
        if (tag.contains("worldUuid")) data.worldUuid = tag.getString("worldUuid");
        if (data.worldUuid == null || data.worldUuid.isEmpty()) data.worldUuid = UUID.randomUUID().toString();
        if (tag.contains("activeAnyaUuid")) data.activeAnyaUuid = tag.getString("activeAnyaUuid");

        if (tag.contains("coreMemory")) {
            ListTag list = tag.getList("coreMemory", 8);
            for (int i = 0; i < list.size(); i++) data.memory.add(list.getString(i));
        }
        if (tag.contains("storyJournal")) {
            ListTag list = tag.getList("storyJournal", 8);
            for (int i = 0; i < list.size(); i++) data.storyJournal.add(list.getString(i));
        }

        data.storyDay = tag.contains("storyDay") ? Math.max(1, tag.getInt("storyDay")) : 1;
        // Миграция старых миров: акт выводится из сетки дней, дальше живёт отдельно
        data.currentAct = tag.contains("currentAct") ? Math.max(1, tag.getInt("currentAct"))
                : actIndexFromDay(data.storyDay);
        data.actStartDay = tag.contains("actStartDay") ? Math.max(1, tag.getInt("actStartDay")) : data.storyDay;
        data.x5NextAct = Math.max(0, tag.getInt("x5NextAct"));
        data.act3SceneDone = tag.getBoolean("act3SceneDone");
        data.storyStateId = tag.getInt("storyStateId");
        data.anyaEventHp = tag.contains("anyaEventHp") ? tag.getDouble("anyaEventHp") : 20.0;
        data.bossDefeated = tag.getBoolean("bossDefeated");
        data.insomniaNightActive = tag.getBoolean("insomniaNightActive");
        data.acidRainActive = tag.getBoolean("acidRainActive");
        data.acidRainDone = tag.getBoolean("acidRainDone");
        data.acidTorchesDone = tag.getBoolean("acidTorchesDone");
        data.toxicMoonActive = tag.getBoolean("toxicMoonActive");
        data.storyPendantGiven = tag.getBoolean("storyPendantGiven");
        data.pendantAct = Math.max(0, tag.getInt("pendantAct"));
        data.interludeX5Done = tag.getBoolean("interludeX5Done");

        data.anyaCoins = Math.max(0, tag.getInt("anyaCoins"));
        data.upBlade = Math.max(0, Math.min(3, tag.getInt("upBlade")));
        data.upSlasher = Math.max(0, Math.min(3, tag.getInt("upSlasher")));
        data.upAgility = Math.max(0, Math.min(3, tag.getInt("upAgility")));
        data.upLucky = Math.max(0, Math.min(3, tag.getInt("upLucky")));
        data.activeCurse = Math.max(0, Math.min(3, tag.getInt("activeCurse")));
        data.curseRolled = tag.getBoolean("curseRolled");
        data.insomniaStartDay = Math.max(0, tag.getInt("insomniaStartDay"));
        data.kosmosDone = tag.getBoolean("kosmosDone");
        data.chatCoreFixed = tag.getBoolean("chatCoreFixed");
        data.bsodDone = tag.getBoolean("bsodDone");
        data.noneMoonActive = tag.getBoolean("noneMoonActive");
        data.kosmosActive = tag.getBoolean("kosmosActive");
        data.fakeBossVictory = tag.getBoolean("fakeBossVictory");
        data.discountStartDay = Math.max(0, tag.getInt("discountStartDay"));
        data.discountEndDay = Math.max(0, tag.getInt("discountEndDay"));
        data.discountActive = tag.getBoolean("discountActive");
        data.bossStage = Math.max(0, Math.min(3, tag.getInt("bossStage")));
        data.bossStageHp = Math.max(0, tag.getDouble("bossStageHp"));
        data.bossGlobalHp = Math.max(0, Math.min(3, tag.getDouble("bossGlobalHp")));

        data.act3Victim = tag.getString("act3Victim");
        data.act3CutsceneTimer = Math.max(0, tag.getInt("act3CutsceneTimer"));
        data.act3Burial = tag.getBoolean("act3Burial");
        data.act3BurialX = tag.getInt("act3BurialX");
        data.act3BurialZ = tag.getInt("act3BurialZ");
        data.act3DigY = tag.getInt("act3DigY");
        data.act3InPocket = tag.getBoolean("act3InPocket");
        data.act3PocketTicks = Math.max(0, tag.getInt("act3PocketTicks"));
        data.act3ChestTriggered = tag.getBoolean("act3ChestTriggered");
        data.act3PocketStunned = tag.getBoolean("act3PocketStunned");
        data.acidRainStartDayTime = Math.max(0L, tag.getLong("acidRainStartDayTime"));
        data.acidRainKarmaAtStart = tag.getInt("acidRainKarmaAtStart");
        data.acidRainCompliment = tag.getBoolean("acidRainCompliment");
        data.kosmosBeaconsHit = Math.max(0, Math.min(3, tag.getInt("kosmosBeaconsHit")));
        data.kosmosTicks = Math.max(0, tag.getInt("kosmosTicks"));
        data.kosmosReturnX = tag.getInt("kosmosReturnX");
        data.kosmosReturnZ = tag.getInt("kosmosReturnZ");
        data.kosmosPrevInsomniaDay = Math.max(0, tag.getInt("kosmosPrevInsomniaDay"));
        data.kosmosPrevDone = tag.getBoolean("kosmosPrevDone");
        data.bsodConfirmed = tag.getBoolean("bsodConfirmed");
        data.storyPlayerUuid = tag.getString("storyPlayerUuid");
        data.tradeCoinDay = Math.max(0, tag.getInt("tradeCoinDay"));
        data.tradeCoinCount = Math.max(0, tag.getInt("tradeCoinCount"));

        data.questItem = tag.getString("questItem");
        data.questTarget = Math.max(0, tag.getInt("questTarget"));
        data.questProgress = Math.max(0, tag.getInt("questProgress"));
        data.questTier = Math.max(0, Math.min(3, tag.getInt("questTier")));
        data.questBaseline = Math.max(0, tag.getInt("questBaseline"));
        data.questDaysLeft = Math.max(0, tag.getInt("questDaysLeft"));
        data.gapDaysLeft = Math.max(0, tag.getInt("gapDaysLeft"));
        data.lastDayNumber = Math.max(0L, tag.getLong("lastDayNumber"));
        data.diamondT3Completed = tag.getBoolean("diamondT3Completed");

        if (tag.contains("anyaLocations", 9)) {
            ListTag anyaList = tag.getList("anyaLocations", 10);
            for (int i = 0; i < anyaList.size(); i++) {
                CompoundTag entry = anyaList.getCompound(i);
                try {
                    UUID uuid = UUID.fromString(entry.getString("uuid"));
                    String dimension = entry.getString("dimension");
                    if (!dimension.isEmpty()) {
                        data.anyaLocations.add(new AnyaLocation(uuid, dimension,
                                entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (!data.questItem.isEmpty() && data.questDaysLeft <= 0) data.questDaysLeft = 1;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("karma", Math.max(0, Math.min(100, karma)));
        tag.putBoolean("migrated", migrated);
        tag.putString("worldUuid", worldUuid);
        tag.putString("activeAnyaUuid", activeAnyaUuid != null ? activeAnyaUuid : "");

        tag.putInt("storyDay", storyDay);
        tag.putInt("currentAct", currentAct);
        tag.putInt("actStartDay", actStartDay);
        tag.putInt("x5NextAct", x5NextAct);
        tag.putBoolean("act3SceneDone", act3SceneDone);
        tag.putInt("storyStateId", storyStateId);
        tag.putDouble("anyaEventHp", anyaEventHp);
        tag.putBoolean("bossDefeated", bossDefeated);
        tag.putBoolean("insomniaNightActive", insomniaNightActive);
        tag.putBoolean("acidRainActive", acidRainActive);
        tag.putBoolean("acidRainDone", acidRainDone);
        tag.putBoolean("acidTorchesDone", acidTorchesDone);
        tag.putBoolean("toxicMoonActive", toxicMoonActive);
        tag.putBoolean("storyPendantGiven", storyPendantGiven);
        tag.putInt("pendantAct", pendantAct);
        tag.putBoolean("interludeX5Done", interludeX5Done);

        tag.putInt("anyaCoins", anyaCoins);
        tag.putInt("upBlade", upBlade);
        tag.putInt("upSlasher", upSlasher);
        tag.putInt("upAgility", upAgility);
        tag.putInt("upLucky", upLucky);
        tag.putInt("activeCurse", activeCurse);
        tag.putBoolean("curseRolled", curseRolled);
        tag.putInt("insomniaStartDay", insomniaStartDay);
        tag.putBoolean("kosmosDone", kosmosDone);
        tag.putBoolean("chatCoreFixed", chatCoreFixed);
        tag.putBoolean("bsodDone", bsodDone);
        tag.putBoolean("noneMoonActive", noneMoonActive);
        tag.putBoolean("kosmosActive", kosmosActive);
        tag.putBoolean("fakeBossVictory", fakeBossVictory);
        tag.putInt("discountStartDay", discountStartDay);
        tag.putInt("discountEndDay", discountEndDay);
        tag.putBoolean("discountActive", discountActive);
        tag.putInt("bossStage", bossStage);
        tag.putDouble("bossStageHp", bossStageHp);
        tag.putDouble("bossGlobalHp", bossGlobalHp);

        tag.putString("act3Victim", act3Victim == null ? "" : act3Victim);
        tag.putInt("act3CutsceneTimer", act3CutsceneTimer);
        tag.putBoolean("act3Burial", act3Burial);
        tag.putInt("act3BurialX", act3BurialX);
        tag.putInt("act3BurialZ", act3BurialZ);
        tag.putInt("act3DigY", act3DigY);
        tag.putBoolean("act3InPocket", act3InPocket);
        tag.putInt("act3PocketTicks", act3PocketTicks);
        tag.putBoolean("act3ChestTriggered", act3ChestTriggered);
        tag.putBoolean("act3PocketStunned", act3PocketStunned);
        tag.putLong("acidRainStartDayTime", acidRainStartDayTime);
        tag.putInt("acidRainKarmaAtStart", acidRainKarmaAtStart);
        tag.putBoolean("acidRainCompliment", acidRainCompliment);
        tag.putInt("kosmosBeaconsHit", kosmosBeaconsHit);
        tag.putInt("kosmosTicks", kosmosTicks);
        tag.putInt("kosmosReturnX", kosmosReturnX);
        tag.putInt("kosmosReturnZ", kosmosReturnZ);
        tag.putInt("kosmosPrevInsomniaDay", kosmosPrevInsomniaDay);
        tag.putBoolean("kosmosPrevDone", kosmosPrevDone);
        tag.putBoolean("bsodConfirmed", bsodConfirmed);
        tag.putString("storyPlayerUuid", storyPlayerUuid == null ? "" : storyPlayerUuid);
        tag.putInt("tradeCoinDay", tradeCoinDay);
        tag.putInt("tradeCoinCount", tradeCoinCount);

        ListTag listMemory = new ListTag();
        for (String fact : memory) listMemory.add(StringTag.valueOf(fact));
        tag.put("coreMemory", listMemory);

        ListTag listStory = new ListTag();
        for (String event : storyJournal) listStory.add(StringTag.valueOf(event));
        tag.put("storyJournal", listStory);

        tag.putString("questItem", questItem == null ? "" : questItem);
        tag.putInt("questTarget", questTarget);
        tag.putInt("questProgress", questProgress);
        tag.putInt("questTier", questTier);
        tag.putInt("questBaseline", questBaseline);
        tag.putInt("questDaysLeft", questDaysLeft);
        tag.putInt("gapDaysLeft", gapDaysLeft);
        tag.putLong("lastDayNumber", lastDayNumber);
        tag.putBoolean("diamondT3Completed", diamondT3Completed);

        ListTag anyaList = new ListTag();
        for (AnyaLocation location : anyaLocations) {
            CompoundTag entry = new CompoundTag();
            entry.putString("uuid", location.uuid.toString());
            entry.putString("dimension", location.dimension);
            entry.putDouble("x", location.x);
            entry.putDouble("y", location.y);
            entry.putDouble("z", location.z);
            anyaList.add(entry);
        }
        tag.put("anyaLocations", anyaList);
        return tag;
    }

    public void clearQuest() {
        questItem = "";
        questTarget = 0;
        questProgress = 0;
        questTier = 0;
        questBaseline = 0;
        questDaysLeft = 0;
    }

    public int cleanStoryJournal(int count) {
        if (count <= 0 || storyJournal.isEmpty()) return 0;
        int removed = 0;
        while (!storyJournal.isEmpty() && removed < count) {
            storyJournal.remove(storyJournal.size() - 1);
            removed++;
        }
        setDirty();
        return removed;
    }

    public static AniSaveData getServerState(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                AniSaveData::load, AniSaveData::new, "animod_state");
    }

    public net.legoman.animod.story.StoryState getStoryState() {
        return net.legoman.animod.story.StoryState.fromId(this.storyStateId);
    }

    /** Миграция: акт из старой сетки дней. */
    public static int actIndexFromDay(int day) {
        if (day <= 4) return 1;
        if (day <= 7) return 2;
        if (day <= 8) return 3;
        if (day <= 11) return 4;
        if (day <= 15) return 5;
        if (day <= 18) return 6;
        if (day <= 19) return 7;
        if (day <= 22) return 8;
        return 9;
    }

    public void setStoryState(net.legoman.animod.story.StoryState state) {
        this.storyStateId = state.getId();
        setDirty();
    }
}