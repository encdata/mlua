package dev.mlua.mcapi.item;

public enum MCTooltipFlag {
    NORMAL(false),
    ADVANCED(true);

    private final boolean advanced;

    MCTooltipFlag(boolean advanced) {
        this.advanced = advanced;
    }

    public boolean isAdvanced() {
        return advanced;
    }
}
