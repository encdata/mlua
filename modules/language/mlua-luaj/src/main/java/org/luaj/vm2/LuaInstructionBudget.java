package org.luaj.vm2;

/** Scoped limit for interpreted Lua bytecode. Java/native calls are not counted. */
public final class LuaInstructionBudget {

    private LuaInstructionBudget() { }

    static final class Counter {
        private final long maximum;
        private final Counter parent;
        private long remaining;

        Counter(long maximum, Counter parent) {
            this.maximum = maximum;
            this.parent = parent;
            this.remaining = maximum;
        }

        void consume() {
            if (--this.remaining < 0) {
                throw new LuaError("[MLua] Lua callback exceeded its instruction budget of "
                    + this.maximum + " instructions");
            }
            if (this.parent != null) this.parent.consume();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Globals globals;
        private final Counter previous;
        private boolean closed;

        Scope(Globals globals, Counter previous) {
            this.globals = globals;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            this.globals.activeInstructionBudget = this.previous;
        }
    }
}
