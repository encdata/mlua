package dev.mlua.core.service;

import dev.mlua.common.enums.Side;
import dev.mlua.runtime.server.MLuaServerThread;

/** Reflective service base for APIs that inspect or mutate server-owned Minecraft state. */
public abstract class MLuaServerThreadReflectiveService extends MLuaReflectiveService {
    protected MLuaServerThreadReflectiveService(Context context) {
        super(context);
    }

    @Override
    protected final Object invokeServiceMethod(ServiceInvocation invocation) throws Exception {
        var server = context.mlua().server();
        if (context.mlua().side() != Side.SERVER || server == null || server.isSameThread()) {
            return invocation.invoke();
        }
        return MLuaServerThread.callChecked(server, invocation::invoke);
    }
}
