package dev.mlua.modules.modal.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaModalStateTest {
    @Test
    void modalOwnershipCanBeClearedPerAddonOrPerRuntime() {
        MLuaModalState state = new MLuaModalState();
        state.setModal("first-addon", true);
        state.setModal("second-addon", true);

        assertTrue(state.isModal("first-addon"));
        assertTrue(state.isModal("second-addon"));
        assertTrue(state.hasModal());

        state.clearOwner("first-addon");
        assertFalse(state.isModal("first-addon"));
        assertTrue(state.isModal("second-addon"));
        assertTrue(state.hasModal());

        state.clear();
        assertFalse(state.isModal("second-addon"));
        assertFalse(state.hasModal());
    }
}
