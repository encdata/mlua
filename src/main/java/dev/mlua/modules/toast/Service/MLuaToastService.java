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

@MLuaDescription("Displays native Minecraft client toasts.")
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

    @MLuaDescription("Adds a native Minecraft system toast; title and message accept strings or Java Components.")
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
        //--version 26.1
        minecraft.getToastManager().addToast(new SystemToast(
            SystemToast.SystemToastId.PERIODIC_NOTIFICATION, toastTitle, toastMessage));
        //--endversion
        //--version 1.21.1
        minecraft.getToasts().addToast(new SystemToast(
            SystemToast.SystemToastId.PERIODIC_NOTIFICATION, toastTitle, toastMessage));
        //--endversion
        //--version 1.20.1
        minecraft.getToasts().addToast(new SystemToast(
            SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, toastTitle, toastMessage));
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
