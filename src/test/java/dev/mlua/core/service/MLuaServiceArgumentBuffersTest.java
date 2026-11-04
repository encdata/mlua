package dev.mlua.core.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaServiceArgumentBuffersTest {
    @Test
    void reusesExactSizedBuffersAndClearsReferences() {
        MLuaServiceArgumentBuffers buffers = new MLuaServiceArgumentBuffers();
        Object[] first = buffers.acquire(2);
        first[0] = new Object();
        buffers.release(first);

        Object[] reused = buffers.acquire(2);

        assertSame(first, reused);
        assertNull(reused[0]);
        buffers.release(reused);
    }

    @Test
    void nestedServiceCallsReceiveIndependentBuffers() {
        MLuaServiceArgumentBuffers buffers = new MLuaServiceArgumentBuffers();
        Object[] outer = buffers.acquire(2);
        Object[] inner = buffers.acquire(2);

        assertNotSame(outer, inner);
        buffers.release(inner);
        buffers.release(outer);
    }

    @Test
    void deepAndWideCallsDoNotGrowTheRetainedBufferPoolWithoutBound() {
        MLuaServiceArgumentBuffers buffers = new MLuaServiceArgumentBuffers();
        Object[][] nested = new Object[17][];
        for (int index = 0; index < nested.length; index++) nested[index] = buffers.acquire(2);
        for (int index = nested.length - 1; index >= 0; index--) buffers.release(nested[index]);

        Object[] wide = buffers.acquire(33);
        buffers.release(wide);
        Object[] anotherWide = buffers.acquire(33);
        assertNotSame(wide, anotherWide);
        buffers.release(anotherWide);
    }
}
