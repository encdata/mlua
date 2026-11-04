package dev.mlua.runtime.client;

/** Propagates client callback authority to child Lua coroutines only while the callback is active. */
final class MLuaClientExecutionScope implements AutoCloseable {
    private static final InheritableThreadLocal<Token> CURRENT = new InheritableThreadLocal<>();
    private final Token previous;
    private final Token token;

    private MLuaClientExecutionScope() {
        this.previous = CURRENT.get();
        this.token = new Token(this.previous);
        CURRENT.set(this.token);
    }

    static MLuaClientExecutionScope open() {
        return new MLuaClientExecutionScope();
    }

    static boolean isActive() {
        for (Token current = CURRENT.get(); current != null; current = current.parent) {
            if (current.active) return true;
        }
        return false;
    }

    @Override public void close() {
        this.token.active = false;
        if (this.previous == null) CURRENT.remove();
        else CURRENT.set(this.previous);
    }

    private static final class Token {
        private final Token parent;
        private volatile boolean active = true;

        private Token(Token parent) {
            this.parent = parent;
        }
    }
}
