package dev.mlua.core.service;

import java.util.Arrays;

/** Reuses exact-sized reflective argument arrays while remaining safe for reentrant Lua calls. */
final class MLuaServiceArgumentBuffers {
    private static final int MAX_CACHED_DEPTH = 16;
    private static final int MAX_CACHED_ARGUMENTS = 32;
    private Object[][][] buffersByDepth = new Object[2][][];
    private int depth;

    Object[] acquire(int argumentCount) {
        if (argumentCount < 0) throw new IllegalArgumentException("Argument count cannot be negative");
        if (depth >= MAX_CACHED_DEPTH || argumentCount > MAX_CACHED_ARGUMENTS) {
            Object[] arguments = new Object[argumentCount];
            depth++;
            return arguments;
        }
        ensureDepth(depth);
        Object[][] buffersBySize = buffersByDepth[depth];
        if (buffersBySize == null || argumentCount >= buffersBySize.length) {
            int capacity = buffersBySize == null ? 4 : buffersBySize.length;
            while (capacity <= argumentCount) capacity *= 2;
            buffersBySize = buffersBySize == null ? new Object[capacity][]
                : Arrays.copyOf(buffersBySize, capacity);
            buffersByDepth[depth] = buffersBySize;
        }
        Object[] arguments = buffersBySize[argumentCount];
        if (arguments == null) {
            arguments = new Object[argumentCount];
            buffersBySize[argumentCount] = arguments;
        }
        depth++;
        return arguments;
    }

    void release(Object[] arguments) {
        if (depth == 0) throw new IllegalStateException("No reflective argument buffer is active");
        int ownerDepth = depth - 1;
        if (arguments == null) throw new IllegalStateException("Reflective argument buffer cannot be null");
        if (ownerDepth < MAX_CACHED_DEPTH && arguments.length <= MAX_CACHED_ARGUMENTS) {
            Object[][] buffersBySize = buffersByDepth[ownerDepth];
            if (buffersBySize == null || arguments.length >= buffersBySize.length
                || buffersBySize[arguments.length] != arguments) {
                throw new IllegalStateException("Reflective argument buffers must be released in reverse acquisition order");
            }
        }
        Arrays.fill(arguments, null);
        depth = ownerDepth;
    }

    private void ensureDepth(int index) {
        if (index < buffersByDepth.length) return;
        int capacity = buffersByDepth.length;
        while (capacity <= index) capacity *= 2;
        buffersByDepth = Arrays.copyOf(buffersByDepth, capacity);
    }
}
