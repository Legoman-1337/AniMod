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
        data.storyStateId = tag.getInt("storyStateId");
        data.anyaEventHp = tag.contains("anyaEventHp") ? tag.getDouble("anyaEventHp") : 20.0;
        data.bossDefeated = tag.getBoolean("bossDefeated");
        data.insomniaNightActive = tag.getBoolean("insomniaNightActive");

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
        tag.putInt("storyStateId", storyStateId);
        tag.putDouble("anyaEventHp", anyaEventHp);
        tag.putBoolean("bossDefeated", bossDefeated);
        tag.putBoolean("insomniaNightActive", insomniaNightActive);

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

    public void setStoryState(net.legoman.animod.story.StoryState state) {
        this.storyStateId = state.getId();
        setDirty();
    }
}