package dev.mlua.modules.coregui.service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.minecraft.binding.MLuaLuaArguments;

/** Registers HUD elements and screen anchors on the Gui service table. */
final class MLuaGuiHudApi {
    private MLuaGuiHudApi() { }

    static void install(MLuaGuiService service, LuaTable table) {
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
    }

}
