package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.CoreGuiType;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.modules.coregui.service.MLuaScriptScreen;
import dev.mlua.modules.coregui.service.MLuaNativeLayout;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.MLuaTableRegistry;
import dev.mlua.minecraft.ui.element.ScreenGui;
import dev.mlua.minecraft.binding.MLuaNativeScreen;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.ui.UDim2;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Assembles Gui's Lua API table; screen and layout behavior remains in MLuaGuiService. */
final class MLuaGuiApi {
    private MLuaGuiApi() { }

    static LuaTable build(MLuaGuiService service) {
        LuaTable table = new LuaTable();
        table.set("GetEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.isEnabled());
            }
        });
        table.set("SetEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                service.setEnabled(args.checkboolean(2));
                return LuaValue.NONE;
            }
        });
        table.set("RegisterScreenTarget", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.registerScreenTarget(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring()));
            }
        });
        table.set("UnregisterScreenTarget", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.unregisterScreenTarget(args.arg(2).checkjstring()));
            }
        });
        LuaTable vanillaHudElements = new LuaTable();
        vanillaHudElements.set("Hud", LuaValue.valueOf("Hud"));
        vanillaHudElements.set("Chat", LuaValue.valueOf("Chat"));
        vanillaHudElements.set("Crosshair", LuaValue.valueOf("Crosshair"));
        vanillaHudElements.set("Hotbar", LuaValue.valueOf("Hotbar"));
        vanillaHudElements.set("Health", LuaValue.valueOf("Health"));
        vanillaHudElements.set("Experience", LuaValue.valueOf("Experience"));
        vanillaHudElements.set("Effects", LuaValue.valueOf("Effects"));
        //--version 1.21.1
        vanillaHudElements.set("Food", LuaValue.valueOf("Food"));
        //--endversion
        //--version 1.21.1,26.1
        vanillaHudElements.set("OverlayMessage", LuaValue.valueOf("OverlayMessage"));
        vanillaHudElements.set("Title", LuaValue.valueOf("Title"));
        vanillaHudElements.set("Scoreboard", LuaValue.valueOf("Scoreboard"));
        vanillaHudElements.set("PlayerList", LuaValue.valueOf("PlayerList"));
        //--endversion
        table.set("VanillaHudElements", vanillaHudElements);
        table.set("AttachElementBefore", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.attachHudElement(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring(), true, args.arg(4),
                    MLuaLuaArguments.optionalWholeInt(args, 5, 0, "Gui:AttachElementBefore", "priority")));
            }
        });
        table.set("AttachElementAfter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.attachHudElement(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring(), false, args.arg(4),
                    MLuaLuaArguments.optionalWholeInt(args, 5, 0, "Gui:AttachElementAfter", "priority")));
            }
        });
        table.set("RemoveHudElement", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.removeHudElement(args.arg(2).checkjstring()));
            }
        });
        if (service.context != null) {
            table.set("ScreenOpened", MLuaLuaBindings.signalInstance(service.state.screenOpened,
                service.context.mlua().globals(), service.context.mlua().scheduler(), service.context.mlua().ownerModId(),
                service.context.mlua().side()));
            table.set("ScreenClosed", MLuaLuaBindings.signalInstance(service.state.screenClosed,
                service.context.mlua().globals(), service.context.mlua().scheduler(), service.context.mlua().ownerModId(),
                service.context.mlua().side()));
            table.set("ChatLineAdding", MLuaLuaBindings.signalInstance(service.state.chatLineAdding,
                service.context.mlua().globals(), service.context.mlua().scheduler(), service.context.mlua().ownerModId(),
                service.context.mlua().side()));
        }

        table.set("GetScreens", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaTable list = new LuaTable();
                int i = 1;
                for (ScreenGui sg : service.state.screens) {
                    if (sg.scheduler() == null && service.context != null) sg.setScheduler(service.context.mlua().scheduler());
                    if (sg.registration() == null && service.context != null) sg.setRegistration(service.context.mlua().registration());
                    list.set(i++, sg.toLua());
                }
                return list;
            }
        });

        table.set("CreateScreen", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).optjstring("Screen");
                ScreenGui sg = new ScreenGui(name);
                service.attachScriptOwner(sg);
                if (args.arg(3).istable()) service.applyScreenProperties(sg, args.arg(3).checktable());
                service.addScreenGui(sg);
                return sg.toLua();
            }
        });

        table.set("OpenNativeScreen", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Object title = service.nativeText(args.arg(2));
                LuaValue initialize = args.arg(3);
                if (!initialize.isfunction()) throw new LuaError("[MLua] Gui:OpenNativeScreen expects an initialization function.");
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null) throw new LuaError("[MLua] Minecraft client is not available.");
                MLuaRuntime runtime = service.requireActiveClientRuntime();
                MLuaScriptScreen screen = new MLuaScriptScreen(title, initialize,
                    service.context.mlua().registration(), service.state,
                    service.context.mlua().ownerModId(), screenExecutionContext(service));
                service.state.scriptScreens.add(screen);
                service.openNativeScreen(screen);
                return MLuaNativeScreen.instance(runtime, screen);
            }
        });

        table.set("OpenScreen", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (service.context == null) throw new LuaError("[MLua] Gui service context is unavailable.");
                String className = args.arg(2).checkjstring();
                Object title = service.nativeText(args.arg(3));
                LuaValue fieldsValue = args.arg(4);
                if (!fieldsValue.isnil() && !fieldsValue.istable()) throw new LuaError("[MLua] Gui:OpenScreen fields must be a table.");
                if (fieldsValue.istable()) {
                    Set<String> reserved = Set.of("NativeScreen", "Close", "AddText", "AddButton", "AddEditBox", "AddRenderableWidget", "SetOnClose", "SetOnTick", "SetOnRender");
                    for (LuaValue key : fieldsValue.checktable().keys()) {
                        if (key.type() == LuaValue.TSTRING && reserved.contains(key.tojstring())) {
                            throw new LuaError("[MLua] Gui:OpenScreen fields cannot replace the native screen member '" + key.tojstring() + "'.");
                        }
                    }
                }
                MLuaRegistration registration = service.context.mlua().registration();
                MLuaRuntime runtime = service.requireActiveClientRuntime();
                if (!registration.isSubclassOf(className, "Screen")) {
                    throw new LuaError("[MLua] Gui:OpenScreen requires a registered class that extends Screen.");
                }
                LuaTable instance;
                try { instance = registration.attachClassInstance(new LuaTable(), className, null); }
                catch (RuntimeException failure) { throw new LuaError("[MLua] Could not create screen class '" + className + "': " + failure.getMessage()); }
                MLuaScriptScreen screen = new MLuaScriptScreen(title, instance, registration, className,
                    fieldsValue.istable() ? fieldsValue.checktable() : null, service.state,
                    service.context.mlua().ownerModId(), screenExecutionContext(service));
                service.state.scriptScreens.add(screen);
                instance.set("NativeScreen", MLuaNativeScreen.instance(runtime, screen));
                instance.rawset("Close", new VarArgFunction() {
                    @Override public Varargs invoke(Varargs closeArgs) {
                        return LuaValue.valueOf(MLuaNativeScreenManager.close(screen));
                    }
                });
                service.openNativeScreen(screen);
                return instance;
            }
        });

        table.set("RemoveScreen", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.removeScreen(args.arg(2)));
            }
        });

        table.set("SetCoreGuiEnabled", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue typeVal = args.arg(2);
                boolean enabled = args.arg(3).optboolean(true);
                CoreGuiType type = service.requireCoreGuiType(typeVal, "SetCoreGuiEnabled");
                service.setCoreGuiEnabled(type, enabled);
                return LuaValue.NONE;
            }
        });

        table.set("GetCoreGuiEnabled", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue typeVal = args.arg(2);
                CoreGuiType type = service.requireCoreGuiType(typeVal, "GetCoreGuiEnabled");
                return LuaValue.valueOf(service.isCoreGuiEnabled(type));
            }
        });

        table.set("AddScreenGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaTable t = args.checktable(2);
                GuiBase existing = MLuaTableRegistry.getElement(t);
                if (existing != null) {
                    if (!(existing instanceof ScreenGui screen)) {
                        throw new LuaError("[MLua] Gui:AddScreenGui expects a ScreenGui instance or a screen definition table");
                    }
                    service.attachScriptOwner(screen);
                    service.addScreenGui(screen);
                    return screen.toLua();
                }
                String name = t.get("Name").optjstring("Screen");
                ScreenGui sg = new ScreenGui(name);
                service.attachScriptOwner(sg);
                try {
                    service.applyScreenProperties(sg, t);
                    MLuaGuiTreeFactory.populateChildren(t, sg);
                } catch (RuntimeException | Error failure) {
                    sg.destroy();
                    throw failure;
                }

                service.addScreenGui(sg);
                return sg.toLua();
            }
        });

        table.set("RemoveScreenGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.removeScreen(args.arg(2)));
            }
        });

        table.set("GetScreenGuis", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaTable list = new LuaTable();
                int i = 1;
                for (ScreenGui sg : service.state.screens) {
                    if (sg.scheduler() == null && service.context != null) sg.setScheduler(service.context.mlua().scheduler());
                    if (sg.registration() == null && service.context != null) sg.setRegistration(service.context.mlua().registration());
                    list.set(i++, sg.toLua());
                }
                return list;
            }
        });

        table.set("GetCoreGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                CoreGuiType type = service.requireCoreGuiType(args.arg(2), "GetCoreGui");
                ScreenGui screen = service.getCoreGui(type);
                return screen == null ? LuaValue.NIL : screen.toLua();
            }
        });

        table.set("CreateCoreGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                CoreGuiType type = service.requireCoreGuiType(args.arg(2), "CreateCoreGui");
                if (type == CoreGuiType.All) {
                    throw new LuaError("[MLua] Gui:CreateCoreGui requires one specific CoreGuiType, not All");
                }
                String name = args.arg(3).optjstring(null);
                return service.createCoreGui(type, name).toLua();
            }
        });

        table.set("GetNativeControls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue selector = args.arg(2);
                if (!selector.isnil() && !selector.istable()) throw new LuaError("[MLua] Gui:GetNativeControls selector must be a table");
                if (selector.istable()) service.requireNativeSelector(selector.checktable());
                return service.nativeControls(selector, false);
            }
        });

        table.set("GetNativeControl", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable selector = args.checktable(2);
                service.requireNativeSelector(selector);
                return service.nativeControls(selector, true).get(1);
            }
        });

        table.set("SetNativeLayout", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                service.requireNativeScreenClientThread("Gui:SetNativeLayout");
                String target = args.checkjstring(2);
                if (target.isBlank()) throw new LuaError("[MLua] Gui:SetNativeLayout requires a screen class name or 'Any'");
                LuaTable selector = args.checktable(3);
                LuaTable properties = args.checktable(4);
                service.requireNativeSelector(selector);
                if (properties.keys().length == 0) throw new LuaError("[MLua] Native layout needs at least one property");
                for (LuaValue key : properties.keys()) {
                    if (key.type() != LuaValue.TSTRING || key.tojstring().isBlank() || !Character.isLetter(key.tojstring().charAt(0))
                        || key.tojstring().startsWith("__")) {
                        throw new LuaError("[MLua] Native layout property names must start with a letter and cannot be runtime metadata");
                    }
                }
                if (!properties.get("X").isnil() || !properties.get("Y").isnil()
                    || !properties.get("Width").isnil() || !properties.get("Height").isnil()) {
                    throw new LuaError("[MLua] Native layout uses Position and Size UDim2 values, not X/Y/Width/Height fields");
                }
                if (!properties.get("Position").isnil() && !MLuaLuauTypes.isUDim2(properties.get("Position"))
                    || !properties.get("Size").isnil() && !MLuaLuauTypes.isUDim2(properties.get("Size"))
                    || !properties.get("Visible").isnil() && !properties.get("Visible").isboolean()
                    || !properties.get("Text").isnil() && properties.get("Text").type() != LuaValue.TSTRING) {
                    throw new LuaError("[MLua] Native layout Position and Size require UDim2 values; Visible must be boolean and Text must be a string");
                }
                for (LuaValue key : properties.keys()) {
                    if (key.type() != LuaValue.TSTRING || Set.of("Position", "Size", "Visible", "Text", "Replacement", "Before", "After").contains(key.tojstring())) continue;
                    LuaValue value = properties.get(key);
                    if (value.type() != LuaValue.TSTRING && value.type() != LuaValue.TNUMBER
                        && value.type() != LuaValue.TBOOLEAN) {
                        throw new LuaError("[MLua] Native layout property '" + key
                            + "' must use a string, number, boolean, enum name, or text value");
                    }
                }
                if (!properties.get("Replacement").isnil()
                    && ((!selector.get("Index").isint() && selector.get("Text").type() != LuaValue.TSTRING)
                        || properties.get("Visible").isboolean() && properties.get("Visible").checkboolean())) {
                    throw new LuaError("[MLua] A replacement requires a specific Index or Text selector and cannot set Visible=true");
                }
                if ((!properties.get("Before").isnil() || !properties.get("After").isnil()) && !selector.get("Index").isint()) {
                    throw new LuaError("[MLua] NativeControl.Before and After require a specific Index selector");
                }
                MLuaNativeLayout layout = service.addNativeLayout(target, selector, properties);
                try {
                    service.recomposeLayouts(MLuaNativeScreenManager.currentScreen(), layout);
                } catch (RuntimeException | Error failure) {
                    service.removeNativeLayout(layout.id());
                    throw failure;
                }
                return LuaValue.valueOf(layout.id());
            }
        });

        table.set("RemoveNativeLayout", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                service.requireNativeScreenClientThread("Gui:RemoveNativeLayout");
                return LuaValue.valueOf(service.removeNativeLayout(args.checklong(2)));
            }
        });

        return MLuaServiceBase.normalizeServiceCalls(table, service.name());
    }

    private static MLuaContext screenExecutionContext(MLuaGuiService service) {
        var globals = service.context.mlua().globals();
        MLuaContext active = MLuaLuaBindings.currentContext();
        if (active != null && active.globals() == globals) return active;

        MLuaContext bound = MLuaLuaBindings.boundContext(globals);
        if (bound != null) return bound;
        throw new LuaError("[MLua] Gui cannot retain a native screen callback without an active Lua runtime context.");
    }
}
