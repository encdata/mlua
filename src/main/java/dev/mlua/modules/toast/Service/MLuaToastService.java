package dev.mlua.modules.toast.Service;

import dev.mlua.api.MLuaDescription;
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

    @MLuaDescription("Adds a native Minecraft system toast with a title and message.")
    public void Show(String title, String message) {
        if (context.side() != Side.CLIENT) {
            throw new LuaError("[MLua] Toast:Show is client-side only.");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw new LuaError("[MLua] Minecraft client is not available.");
        }
        Component toastTitle = Component.literal(title);
        Component toastMessage = Component.literal(message);
        //--version 26.1
        minecraft.gui.toastManager().addToast(new SystemToast(
            SystemToast.SystemToastId.PERIODIC_NOTIFICATION, toastTitle, toastMessage));
        //--endversion
        //--version 1.21.1
        minecraft.getToastManager().addToast(new SystemToast(
            SystemToast.SystemToastId.PERIODIC_NOTIFICATION, toastTitle, toastMessage));
        //--endversion
        //--version 1.20.1
        minecraft.getToasts().addToast(new SystemToast(
            SystemToast.SystemToastIds.PERIODIC_NOTIFICATION, toastTitle, toastMessage));
        //--endversion
    }
}
