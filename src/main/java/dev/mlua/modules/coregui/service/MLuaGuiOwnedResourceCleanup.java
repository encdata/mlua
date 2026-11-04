package dev.mlua.modules.coregui.service;

import dev.mlua.minecraft.ui.element.GuiObject;
import java.util.List;
import java.util.Map;

/** Removes GUI resources belonging to one addon while restoring changes owned by other addons. */
final class MLuaGuiOwnedResourceCleanup {
    private final MLuaGuiState state;
    private final MLuaGuiScreenRegistry screens;
    private final MLuaGuiNativeLayoutController layouts;

    MLuaGuiOwnedResourceCleanup(MLuaGuiState state, MLuaGuiScreenRegistry screens,
                                MLuaGuiNativeLayoutController layouts) {
        this.state = state;
        this.screens = screens;
        this.layouts = layouts;
    }

    int clear(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return 0;
        int removed = 0;
        for (MLuaScriptScreen screen : List.copyOf(this.state.scriptScreens)) {
            if (!ownerId.equals(screen.ownerModId)) continue;
            screen.dispose();
            removed++;
        }

        synchronized (this.state) {
            for (Map.Entry<String, MLuaHudElement> entry : List.copyOf(this.state.hudElements.entrySet())) {
                if (ownerId.equals(entry.getValue().owner)
                    && this.state.hudElements.remove(entry.getKey(), entry.getValue())) removed++;
            }
            if (removed > 0) MLuaHudElementSnapshot.invalidate();

            boolean removedScreenTarget = false;
            for (Map.Entry<String, MLuaGuiState.ScreenTarget> entry : List.copyOf(this.state.screenTargets.entrySet())) {
                if (ownerId.equals(entry.getValue().owner())
                    && this.state.screenTargets.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                    removedScreenTarget = true;
                }
            }
            if (removedScreenTarget) this.layouts.recomposeTrackedScreens();

            for (MLuaNativeLayout layout : List.copyOf(this.state.nativeLayouts)) {
                if (!ownerId.equals(layout.owner())) continue;
                GuiObject[] ownedObjects = {layout.replacement(), layout.before(), layout.after()};
                this.layouts.removeNativeLayoutEntry(layout);
                for (GuiObject object : ownedObjects) {
                    if (object != null && ownerId.equals(object.ownerModId())) {
                        object.destroyPreservingForeign(ownerId);
                    }
                }
                removed++;
            }
            removed += this.screens.removeOwnedChildren(ownerId);
        }
        return removed;
    }
}
