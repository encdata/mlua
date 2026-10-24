package dev.mlua.modules.mixin.api;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.mixin.MLuaMixinCallbackRegistry;
import dev.mlua.common.mixin.MLuaMixinLuaApi;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import org.luaj.vm2.LuaTable;

/** Lua service facade; kept outside the Mixin configuration package with transformed classes. */
public final class MLuaMixinService extends MLuaServiceBase {
    public MLuaMixinService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "Mixin";
    }

    @Override
    public LuaTable toLua() {
        return MLuaMixinLuaApi.create(this::scope, this::registrationAllowed,
            () -> this.context.mlua().executionLock(),
            table -> MLuaServiceBase.normalizeServiceCalls(table, this.name()));
    }

    private boolean registrationAllowed() {
        RegistrationPhase phase = this.context.mlua().phase();
        return phase == RegistrationPhase.MIXIN || phase == RegistrationPhase.REGISTRY;
    }

    private String scope() {
        if (this.context.mlua() instanceof MLuaContext mlua) {
            return MLuaMixinCallbackRegistry.scriptScope(
                mlua.script().path().toAbsolutePath().normalize().toString());
        }
        return "unscoped";
    }
}
