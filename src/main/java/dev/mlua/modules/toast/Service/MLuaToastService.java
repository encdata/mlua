package dev.mlua.modules.toast.Service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaReflectiveService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Displays native Minecraft client toasts with vanilla multiline wrapping.")
public final class MLuaToastService extends MLuaReflectiveService {
    public MLuaToastService(Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "Toast";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Adds a native Minecraft system toast and wraps long messages to fit; title and message accept strings or Java Components.")
    public void Show(LuaValue title, LuaValue message) {
        if (context.side() != Side.CLIENT) {
            throw new LuaError("[MLua] Toast:Show is client-side only.");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw new LuaError("[MLua] Minecraft client is not available.");
        }
        Component toastTitle = component(title, "title");
        Component toastMessage = component(message, "message");
        Runnable show = () -> display(minecraft, toastTitle, toastMessage);
        if (minecraft.isSameThread()) show.run();
        else minecraft.execute(show);
    }

    private static void display(Minecraft minecraft, Component title, Component message) {
        //--version 26.1
        minecraft.getToastManager().addToast(SystemToast.multiline(
            minecraft, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message));
        //--endversion
        //--version 1.21.1
        minecraft.getToasts().addToast(SystemToast.multiline(
            minecraft, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message));
        //--endversion
        //--version 1.20.1
        minecraft.getToasts().addToast(SystemToast.multiline(
            minecraft, SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, title, message));
        //--endversion
    }

    private static Component component(LuaValue value, String name) {
        if (value.isstring()) return Component.literal(value.checkjstring());
        try {
            Object component = MLuaLuaCoercions.javaValue(value, Component.class);
            if (component instanceof Component text) return text;
        } catch (RuntimeException ignored) { }
        throw new LuaError("[MLua] Toast:Show " + name + " expects a string or Java Component value.");
    }
}
