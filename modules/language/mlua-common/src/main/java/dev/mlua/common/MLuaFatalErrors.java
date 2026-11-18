package dev.mlua.common;

public final class MLuaFatalErrors {
    private MLuaFatalErrors() { }

    @SuppressWarnings("removal")
    public static void rethrowIfFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) throw fatal;
        if (failure instanceof ThreadDeath fatal) throw fatal;
    }
}
