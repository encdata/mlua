package dev.mlua.core.service;

import dev.mlua.runtime.MLuaGameThreadAccess;

/** Reflective service base that runs live Minecraft state access on its owning game thread. */
public abstract class MLuaGameThreadReflectiveService extends MLuaReflectiveService {
    protected MLuaGameThreadReflectiveService(Context context) {
        super(context);
    }

    @Override
    protected final Object invokeServiceMethod(ServiceInvocation invocation) throws Exception {
        return MLuaGameThreadAccess.call(context, invocation::invoke);
    }
}
