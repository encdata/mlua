package dev.mlua.mcapi.world;

public enum MCDifficulty {
    PEACEFUL("peaceful", "peaceful", 0),
    EASY("easy", "easy", 1),
    NORMAL("normal", "normal", 2),
    HARD("hard", "hard", 3);

    private final String name;
    private final String key;
    private final int id;

    MCDifficulty(String name, String key, int id) {
        this.name = name;
        this.key = key;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        return key;
    }

    public int getId() {
        return id;
    }
}
