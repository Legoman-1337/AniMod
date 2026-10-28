package net.legoman.animod.story;

public enum StoryState {
    NORMAL(0, "Обычное состояние", false),
    ANYA_IS_WATCHING(1, "Аня наблюдает", false),
    ANYA_IS_SCARED(2, "Аня напугана", false),
    ANYA_IS_CRYING(3, "Аня плачет", false),
    ANYA_IS_ANGRY(4, "Аня в ярости", true),
    ANYA_IS_NOT_HERE(5, "Аня исчезла", false),
    ANYA_BODY_EVENT(6, "Босс: Воплощение Сбоя", true),
    STORY_COMPLETED(7, "Сюжет завершён", false);

    private final int id;
    private final String description;
    private final boolean hostile;

    StoryState(int id, String description, boolean hostile) {
        this.id = id;
        this.description = description;
        this.hostile = hostile;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHostile() {
        return hostile;
    }

    public static StoryState fromId(int id) {
        for (StoryState state : values()) {
            if (state.id == id) return state;
        }
        return NORMAL;
    }

    public static StoryState fromName(String name) {
        for (StoryState state : values()) {
            if (state.name().equalsIgnoreCase(name)) return state;
        }
        return null;
    }
}