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
    public String worldUuid = ""; // Стабильный идентификатор мира
    public List<String> memory = new ArrayList<>();
    public List<String> storyJournal = new ArrayList<>();

    public static AniSaveData load(CompoundTag tag) {
        AniSaveData data = new AniSaveData();

        if (tag.contains("karma")) {
            data.karma = Math.max(0, Math.min(100, tag.getInt("karma")));
        }

        data.migrated = tag.getBoolean("migrated");

        if (tag.contains("worldUuid")) {
            data.worldUuid = tag.getString("worldUuid");
        }
        // Генерация UUID, если мир запускается впервые с новой версией мода
        if (data.worldUuid == null || data.worldUuid.isEmpty()) {
            data.worldUuid = UUID.randomUUID().toString();
        }

        if (tag.contains("coreMemory")) {
            ListTag list = tag.getList("coreMemory", 8);
            for (int i = 0; i < list.size(); i++) {
                data.memory.add(list.getString(i));
            }
        }

        if (tag.contains("storyJournal")) {
            ListTag list = tag.getList("storyJournal", 8);
            for (int i = 0; i < list.size(); i++) {
                data.storyJournal.add(list.getString(i));
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("karma", Math.max(0, Math.min(100, karma)));
        tag.putBoolean("migrated", migrated);
        tag.putString("worldUuid", worldUuid); // Сохраняем сгенерированный UUID

        ListTag listMemory = new ListTag();
        for (String fact : memory) {
            listMemory.add(StringTag.valueOf(fact));
        }
        tag.put("coreMemory", listMemory);

        ListTag listStory = new ListTag();
        for (String event : storyJournal) {
            listStory.add(StringTag.valueOf(event));
        }
        tag.put("storyJournal", listStory);

        return tag;
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
                AniSaveData::load,
                AniSaveData::new,
                "animod_state"
        );
    }
}