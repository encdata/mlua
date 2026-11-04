package dev.mlua.mcapi.world;

public enum MCGameType {
    SURVIVAL("survival", 0),
    CREATIVE("creative", 1),
    ADVENTURE("adventure", 2),
    SPECTATOR("spectator", 3);

    private final String name;
    private final int id;

    MCGameType(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public static MCGameType byId(int id) {
        for (MCGameType type : values()) {
            if (type.id == id) return type;
        }
        return SURVIVAL;
    }

    public static MCGameType byName(String name) {
        for (MCGameType type : values()) {
            if (type.name.equalsIgnoreCase(name)) return type;
        }
        return SURVIVAL;
    }
}
