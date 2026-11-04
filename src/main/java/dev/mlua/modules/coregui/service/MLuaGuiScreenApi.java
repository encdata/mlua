package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.CoreGuiType;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaNativeScreen;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.MLuaTableRegistry;
import dev.mlua.minecraft.ui.element.ScreenGui;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.util.Set;

/** Registers script screens, native screens, and CoreGui access on Gui. */
final class MLuaGuiScreenApi {
    private MLuaGuiScreenApi() { }

    static void install(MLuaGuiService service, LuaTable table) {
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
                Object title = MLuaGuiValueParser.nativeText(args.arg(2));
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
                Object title = MLuaGuiValueParser.nativeText(args.arg(3));
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

        table.set("CloseCurrentScreen", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaNativeScreenManager.closeCurrentScreen());
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
                CoreGuiType type = MLuaGuiValueParser.requireCoreGuiType(typeVal, "SetCoreGuiEnabled");
                service.setCoreGuiEnabled(type, enabled);
                return LuaValue.NONE;
            }
        });

        table.set("GetCoreGuiEnabled", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue typeVal = args.arg(2);
                CoreGuiType type = MLuaGuiValueParser.requireCoreGuiType(typeVal, "GetCoreGuiEnabled");
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
                CoreGuiType type = MLuaGuiValueParser.requireCoreGuiType(args.arg(2), "GetCoreGui");
                ScreenGui screen = service.getCoreGui(type);
                return screen == null ? LuaValue.NIL : screen.toLua();
            }
        });

        table.set("CreateCoreGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                CoreGuiType type = MLuaGuiValueParser.requireCoreGuiType(args.arg(2), "CreateCoreGui");
                if (type == CoreGuiType.All) {
                    throw new LuaError("[MLua] Gui:CreateCoreGui requires one specific CoreGuiType, not All");
                }
                String name = args.arg(3).optjstring(null);
                return service.createCoreGui(type, name).toLua();
            }
        });

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
