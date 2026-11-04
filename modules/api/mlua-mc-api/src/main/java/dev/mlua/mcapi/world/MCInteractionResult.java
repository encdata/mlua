package dev.mlua.mcapi.world;

public enum MCInteractionResult {
    SUCCESS(true, true),
    CONSUME(true, false),
    CONSUME_PARTIAL(true, false),
    PASS(false, false),
    FAIL(false, false);

    private final boolean consumesAction;
    private final boolean succeeds;

    MCInteractionResult(boolean consumesAction, boolean succeeds) {
        this.consumesAction = consumesAction;
        this.succeeds = succeeds;
    }

    public boolean consumesAction() {
        return consumesAction;
    }

    public boolean succeeds() {
        return succeeds;
    }
}
