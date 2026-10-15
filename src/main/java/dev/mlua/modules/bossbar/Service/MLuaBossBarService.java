package dev.mlua.modules.bossbar.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.bossbar.MLuaBossBarRegistry;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.api.MLuaDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.UUID;
import java.util.function.Function;

@MLuaDescription("Creates and manages server boss bars.")
public final class MLuaBossBarService extends MLuaReflectiveService {
    private static final MLuaBossBarRegistry<ServerBossEvent> bossBars = new MLuaBossBarRegistry<>();

    public static void clearOwned(Object runtimeOwner, String ownerModId) {
        for (ServerBossEvent event : bossBars.clearOwned(runtimeOwner, ownerModId)) {
            event.removeAllPlayers();
        }
    }

    public static void clearRuntime(Object runtimeOwner) {
        for (ServerBossEvent event : bossBars.clearRuntime(runtimeOwner)) {
            event.removeAllPlayers();
        }
    }
    private final MLuaRegistration registration;

    public MLuaBossBarService(MLuaService.Context context) {
        super(context);

        this.registration = context.mlua().registration();
    }

    @Override
    public String name() {
        return "BossBar";
    }

    @Override
    public LuaTable toLua() {
        if (this.registration == null || this.registration.classTable("BossBarHandle") == null) {
            throw new IllegalStateException("BossBarHandle class must be registered before materializing the BossBar service");
        }
        LuaTable table = super.toLua();
        table.set("__side", "Server");
        return table;
    }

    @MLuaDescription("Creates a boss bar with a title.")
    public LuaTable Create(String name, String title) {
        requireServer();
        ServerBossEvent event = new ServerBossEvent(
            //--version 26.1
            UUID.randomUUID(),
            //--endversion
            Component.literal(title),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.PROGRESS
        );
        MLuaBossBarRegistry.Key key = key(name);
        ServerBossEvent replaced = bossBars.put(key, event);
        if (replaced != null) replaced.removeAllPlayers();
        return createHandle(key, event);
    }

    @MLuaDescription("Returns a live boss bar handle by identifier, or nil when missing.")
    public LuaValue Get(String name) {
        requireServer();
        MLuaBossBarRegistry.Key key = key(name);
        ServerBossEvent event = bossBars.get(key);
        return event == null ? LuaValue.NIL : createHandle(key, event);
    }

    @MLuaDescription("Removes a boss bar by identifier.")
    public void Remove(String name) {
        requireServer();
        ServerBossEvent event = bossBars.remove(key(name));
        if (event != null) event.removeAllPlayers();
    }

    @MLuaDescription("Returns the live handles for boss bars owned by the calling mod.")
    public LuaTable GetAll() {
        requireServer();
        LuaTable result = new LuaTable();
        int index = 1;
        for (var entry : bossBars.snapshot(runtimeOwner(), ownerModId()).entrySet()) {
            LuaTable handle = createHandle(entry.getKey(), entry.getValue());
            handle.set("__name", entry.getKey().name());
            result.set(index++, handle);
        }
        return result;
    }

    private LuaTable createHandle(MLuaBossBarRegistry.Key key, ServerBossEvent event) {
        String name = key.name();
        LuaTable handle = new LuaTable();
        handle.set("__name", name);
        this.registration.attachClassInstance(handle, "BossBarHandle", null);

        MLuaClassMethods.setInstanceMethod(handle, "SetTitle", fn((args) -> {
            requireServer();
            event.setName(Component.literal(args.checkjstring(2)));
            return LuaValue.NONE;
        }));

        MLuaClassMethods.setInstanceMethod(handle, "SetHealth", fn((args) -> {
            requireServer();
            event.setProgress((float) args.checkdouble(2));
            return LuaValue.NONE;
        }));

        MLuaClassMethods.setInstanceMethod(handle, "SetColor", fn((args) -> {
            requireServer();
            String colorName = args.checkjstring(2);
            try {
                event.setColor(BossEvent.BossBarColor.valueOf(colorName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new LuaError("[MLua] Unknown boss bar color: " + colorName);
            }
            return LuaValue.NONE;
        }));

        MLuaClassMethods.setInstanceMethod(handle, "SetStyle", fn((args) -> {
            requireServer();
            String styleName = args.checkjstring(2);
            try {
                event.setOverlay(BossEvent.BossBarOverlay.valueOf(styleName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new LuaError("[MLua] Unknown boss bar style: " + styleName);
            }
            return LuaValue.NONE;
        }));

        MLuaClassMethods.setInstanceMethod(handle, "SetVisible", fn((args) -> {
            requireServer();
            event.setVisible(args.checkboolean(2));
            return LuaValue.NONE;
        }));

        MLuaClassMethods.setInstanceMethod(handle, "GetTitle", fn((args) -> {
            requireServer();
            return LuaValue.valueOf(event.getName().getString());
        }));

        MLuaClassMethods.setInstanceMethod(handle, "GetHealth", fn((args) -> {
            requireServer();
            return LuaValue.valueOf(event.getProgress());
        }));

        MLuaClassMethods.setInstanceMethod(handle, "GetColor", fn((args) -> {
            requireServer();
            return LuaValue.valueOf(event.getColor().name());
        }));

        MLuaClassMethods.setInstanceMethod(handle, "GetStyle", fn((args) -> {
            requireServer();
            return LuaValue.valueOf(event.getOverlay().name());
        }));

        MLuaClassMethods.setInstanceMethod(handle, "IsVisible", fn((args) -> {
            requireServer();
            return LuaValue.valueOf(event.isVisible());
        }));

        MLuaClassMethods.setInstanceMethod(handle, "AddPlayer", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            event.addPlayer(player);
            return LuaValue.NONE;
        }));

        MLuaClassMethods.setInstanceMethod(handle, "RemovePlayer", fn((args) -> {
            requireServer();
            ServerPlayer player = extractPlayer(args.arg(2));
            event.removePlayer(player);
            return LuaValue.NONE;
        }));

        MLuaClassMethods.setInstanceMethod(handle, "Remove", fn((args) -> {
            requireServer();
            event.removeAllPlayers();
            bossBars.remove(key, event);
            return LuaValue.NONE;
        }));

        return handle;
    }

    private void requireServer() {
        if (this.context.mlua().side() != Side.SERVER) {
            throw new LuaError("[MLua] BossBar service methods are server-side only");
        }
    }

    private Object runtimeOwner() {
        return this.context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
    }

    private String ownerModId() {
        String owner = this.context.mlua().ownerModId();
        return owner == null ? "" : owner;
    }

    private MLuaBossBarRegistry.Key key(String name) {
        return new MLuaBossBarRegistry.Key(runtimeOwner(), ownerModId(), name);
    }

    private ServerPlayer extractPlayer(LuaValue playerVal) {
        return (ServerPlayer) MLuaEntityWrapper.requireLuaInstance(playerVal, context.mlua().registration(), "Player", "Player");
    }

    private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new LuaError("[MLua] " + failure.getMessage());
                }
            }
        };
    }

}
