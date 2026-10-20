package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.CoreGuiType;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.MLuaTableRegistry;
import dev.mlua.minecraft.ui.element.ScreenGui;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Owns script-created ScreenGui instances and CoreGui enablement for one GUI runtime. */
final class MLuaGuiScreenRegistry {
    private final MLuaGuiService service;
    private final MLuaGuiState state;

    MLuaGuiScreenRegistry(MLuaGuiService service, MLuaGuiState state) {
        this.service = service;
        this.state = state;
    }

    void add(ScreenGui screen) {
        java.util.Objects.requireNonNull(screen, "screenGui");
        synchronized (this.state) {
            if (!this.state.screens.contains(screen)) this.state.screens.add(screen);
        }
    }

    void remove(ScreenGui screen) {
        synchronized (this.state) {
            this.state.screens.remove(screen);
        }
    }

    List<ScreenGui> all() {
        synchronized (this.state) {
            return List.copyOf(this.state.screens);
        }
    }

    ScreenGui core(CoreGuiType type) {
        synchronized (this.state) {
            for (ScreenGui screen : this.state.screens) {
                if (screen.getCoreGuiType() == type) return screen;
            }
        }
        return null;
    }

    ScreenGui createCore(CoreGuiType type, String name) {
        if (type == null || type == CoreGuiType.All) {
            throw new IllegalArgumentException("A specific CoreGuiType is required for a core overlay");
        }
        synchronized (this.state) {
            ScreenGui existing = this.core(type);
            if (existing != null) return existing;
            ScreenGui screen = new ScreenGui(name == null || name.isBlank() ? type.name() + "Overlay" : name);
            screen.setCoreGuiType(type);
            this.service.attachScriptOwner(screen);
            this.state.screens.add(screen);
            return screen;
        }
    }

    boolean isCoreEnabled(CoreGuiType type) {
        if (type == CoreGuiType.All) return true;
        synchronized (this.state) {
            return !this.state.disabledTypes.contains(type);
        }
    }

    void setCoreEnabled(CoreGuiType type, boolean enabled) {
        synchronized (this.state) {
            if (type == CoreGuiType.All) {
                if (enabled) this.state.disabledTypes.clear();
                else for (CoreGuiType candidate : CoreGuiType.values()) {
                    if (candidate != CoreGuiType.All) this.state.disabledTypes.add(candidate);
                }
            } else if (enabled) {
                this.state.disabledTypes.remove(type);
            } else {
                this.state.disabledTypes.add(type);
            }
        }
    }

    boolean remove(LuaValue target) {
        ScreenGui found = null;
        if (target.istable()) {
            GuiBase instance = MLuaTableRegistry.getElement(target.checktable());
            if (instance instanceof ScreenGui screen) {
                synchronized (this.state) {
                    if (this.state.screens.contains(screen)) found = screen;
                }
            }
        } else if (target.type() == LuaValue.TSTRING) {
            String name = target.checkjstring();
            synchronized (this.state) {
                for (ScreenGui screen : this.state.screens) {
                    if (name.equals(screen.getName())) {
                        found = screen;
                        break;
                    }
                }
            }
        } else {
            throw new LuaError("[MLua] Gui:RemoveScreenGui expects a ScreenGui instance or name");
        }
        if (found == null) return false;
        this.remove(found);
        found.destroy();
        return true;
    }

    int removeOwnedChildren(String ownerId) {
        int removed = 0;
        synchronized (this.state) {
            for (ScreenGui screen : List.copyOf(this.state.screens)) {
                removed += screen.removeOwnedChildren(ownerId);
                if (ownerId.equals(screen.ownerModId()) && screen.getChildren().isEmpty()
                    && this.state.screens.remove(screen)) {
                    screen.destroy();
                    removed++;
                }
            }
        }
        return removed;
    }
}
