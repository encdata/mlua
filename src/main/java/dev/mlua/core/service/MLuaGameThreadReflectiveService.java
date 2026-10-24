package dev.mlua.core.service;

import dev.mlua.common.enums.Side;
import dev.mlua.runtime.client.MLuaClientThread;
import dev.mlua.runtime.server.MLuaServerThread;

/** Reflective service base that runs live Minecraft state access on its owning game thread. */
public abstract class MLuaGameThreadReflectiveService extends MLuaReflectiveService {
    protected MLuaGameThreadReflectiveService(Context context) {
        super(context);
    }

    @Override
    protected final Object invokeServiceMethod(ServiceInvocation invocation) throws Exception {
        var minecraftContext = context.mlua();
        if (minecraftContext.side() == Side.SERVER) {
            var server = minecraftContext.server();
            if (server == null || server.isSameThread()) return invocation.invoke();
            return MLuaServerThread.callChecked(server, invocation::invoke);
        }
        return MLuaClientThread.callForClientState(invocation::invoke);
    }
}
