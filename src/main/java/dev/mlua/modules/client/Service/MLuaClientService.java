package dev.mlua.modules.client.Service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Read-only access to local Minecraft client and window state.")
public final class MLuaClientService extends MLuaReflectiveService {
    public MLuaClientService(Context context) { super(context); }
    @Override public String name() { return "Client"; }
    @Override public LuaTable toLua() {
        requireClientSide(name());
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Returns the current client frame rate, or zero when this version does not expose it.")
    public int GetFPS() { return number(client(), new String[]{"getFps", "getFramerate", "fps"}, 0).intValue(); }

    @MLuaDescription("Returns whether the integrated client is paused.")
    public boolean IsPaused() { return bool(client(), new String[]{"isPaused"}, false); }

    @MLuaDescription("Returns whether the Minecraft window is focused.")
    public boolean IsWindowFocused() { return bool(window(), new String[]{"isActive", "isFocused"}, false); }

    @MLuaDescription("Returns the Minecraft version name.")
    public String GetVersion() {
        requireClientSide(name());
        Object version = SharedConstants.getCurrentVersion();
        Object name = invoke(version, new String[]{"getName", "name", "getId", "getReleaseTarget"});
        return name == null ? String.valueOf(version) : String.valueOf(name);
    }

    @MLuaDescription("Returns the active screen class name, or nil when no screen is open.")
    public LuaValue GetScreenName() {
        Object screen = field(client(), "screen", "screen");
        return screen == null ? LuaValue.NIL : LuaValue.valueOf(screen.getClass().getName());
    }

    @MLuaDescription("Checks whether a mod id is loaded in this client instance.")
    public boolean IsModLoaded(String id) {
        requireClientSide(name());
        if (id == null || id.isBlank()) throw new LuaError("[MLua] Client:IsModLoaded expects a non-empty mod id.");
        return LoaderApi.platform().hasMod(id);
    }

    @MLuaDescription("Returns the Minecraft window size in physical pixels.")
    public LuaValue GetWindowSize() { Object w = window(); return MLuaVector.vector2(integer(w, new String[]{"getWidth"}, 0), integer(w, new String[]{"getHeight"}, 0)); }

    @MLuaDescription("Returns the window size in GUI-scaled pixels.")
    public LuaValue GetScaledWindowSize() { Object w = window(); return MLuaVector.vector2(integer(w, new String[]{"getGuiScaledWidth"}, 0), integer(w, new String[]{"getGuiScaledHeight"}, 0)); }

    @MLuaDescription("Returns the current GUI scale factor.")
    public double GetGuiScale() { return number(window(), new String[]{"getGuiScale"}, 1).doubleValue(); }

    @MLuaDescription("Returns the mouse position in GUI-scaled pixels.")
    public LuaValue GetMousePosition() {
        Object minecraft = client();
        Object mouse = field(minecraft, "mouseHandler", "mouseHandler");
        Object window = window();
        double x = number(mouse, new String[]{"xpos"}, 0).doubleValue();
        double y = number(mouse, new String[]{"ypos"}, 0).doubleValue();
        double width = Math.max(1, number(window, new String[]{"getWidth"}, 1).doubleValue());
        double height = Math.max(1, number(window, new String[]{"getHeight"}, 1).doubleValue());
        double guiWidth = number(window, new String[]{"getGuiScaledWidth"}, width).doubleValue();
        double guiHeight = number(window, new String[]{"getGuiScaledHeight"}, height).doubleValue();
        return MLuaVector.vector2(x * guiWidth / width, y * guiHeight / height);
    }

    private Object client() {
        requireClientSide(name());
        try { return MLuaMinecraftClient.instance(); }
        catch (RuntimeException error) { throw new LuaError("[MLua] Client service is unavailable outside an initialized game client."); }
    }
    private Object window() { return invoke(client(), new String[]{"getWindow"}); }

    private static boolean bool(Object object, String[] methods, boolean fallback) {
        Object value = invoke(object, methods);
        return value instanceof Boolean result ? result : fallback;
    }
    private static int integer(Object object, String[] methods, int fallback) { return number(object, methods, fallback).intValue(); }
    private static Number number(Object object, String[] names, Number fallback) {
        if (object == null) return fallback;
        Object value = invoke(object, names);
        if (value instanceof Number result) return result;
        for (String name : names) {
            Object field = field(object, name, name);
            if (field instanceof Number result) return result;
        }
        return fallback;
    }
    private static Object invoke(Object object, String[] names) {
        if (object == null) return null;
        for (String name : names) {
            try { Method method = object.getClass().getMethod(name); return method.invoke(object); }
            catch (ReflectiveOperationException ignored) { }
            try { Method method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object); }
            catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }
    private static Object field(Object object, String... names) {
        if (object == null) return null;
        for (String name : names) {
            Class<?> type = object.getClass();
            while (type != null) {
                try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
                catch (ReflectiveOperationException ignored) { type = type.getSuperclass(); }
            }
        }
        return null;
    }
}
