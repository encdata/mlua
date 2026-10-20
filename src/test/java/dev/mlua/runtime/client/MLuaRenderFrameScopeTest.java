package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRenderFrameScopeTest {
    @Test
    void frameObjectsExpireAndScopeBindingsRestoreNesting() {
        MLuaRenderFrameScope outer = new MLuaRenderFrameScope();
        MLuaRenderFrameScope inner = new MLuaRenderFrameScope();
        Object nativeObject = new Object();
        var nativeReference = (MLuaRenderFrameScope.MLuaScopedRenderObject)
            outer.view().scopedReference(nativeObject);
        var outerBinding = outer.view().enter();

        assertSame(outer.view(), MLuaRenderFrameScope.currentView());
        assertSame(nativeObject, nativeReference.unwrap());

        var innerBinding = inner.view().enter();
        assertSame(inner.view(), MLuaRenderFrameScope.currentView());
        innerBinding.close();
        assertSame(outer.view(), MLuaRenderFrameScope.currentView());

        outerBinding.close();
        outer.close();
        assertFalse(outer.view().isActive());
        assertThrows(IllegalStateException.class, nativeReference::unwrap);
        assertTrue(inner.view().isActive());
        inner.close();
        assertFalse(inner.view().isActive());
    }

    @Test
    void scopeBindingCannotBeClosedFromAnotherThread() throws Exception {
        MLuaRenderFrameScope scope = new MLuaRenderFrameScope();
        var reference = (MLuaRenderFrameScope.MLuaScopedRenderObject)
            scope.view().scopedReference(new Object());
        var binding = scope.view().enter();
        FutureTask<Boolean> closeOnOtherThread = new FutureTask<>(() -> {
            try {
                binding.close();
                return false;
            } catch (IllegalStateException expected) {
                try {
                    reference.unwrap();
                    return false;
                } catch (IllegalStateException wrongThread) {
                    return true;
                }
            }
        });
        Thread worker = new Thread(closeOnOtherThread, "mlua-render-scope-test");
        worker.start();

        assertTrue(closeOnOtherThread.get());
        assertTrue(scope.view().isActive());
        assertSame(scope.view(), MLuaRenderFrameScope.currentView());
        binding.close();
        scope.close();
        assertFalse(scope.view().isActive());
    }
}
