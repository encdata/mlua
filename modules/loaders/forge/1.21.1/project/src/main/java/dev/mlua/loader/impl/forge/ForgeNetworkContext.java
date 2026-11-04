package dev.mlua.loader.impl.forge;

import dev.mlua.loaderapi.NetworkContext;
import java.lang.reflect.Method;
import java.util.function.Supplier;

public class ForgeNetworkContext implements NetworkContext {
    private final Object context;

    public ForgeNetworkContext(Supplier<?> supplier) {
        this.context = supplier.get();
    }

    @Override
    public void enqueueWork(Runnable task) {
        invoke("enqueueWork", new Class<?>[]{Runnable.class}, task);
    }

    @Override
    public void setPacketHandled(boolean handled) {
        invoke("setPacketHandled", new Class<?>[]{boolean.class}, handled);
    }

    @Override
    public String getSenderUuid() {
        Object sender = invoke("getSender", new Class<?>[0]);
        if (sender == null) {
            return null;
        }
        try {
            Method method = sender.getClass().getMethod("getUUID");
            Object uuid = method.invoke(sender);
            return uuid != null ? uuid.toString() : null;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isClientSide() {
        Object result = invoke("isClientSide", new Class<?>[0]);
        return result instanceof Boolean value && value;
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = context.getClass().getMethod(name, parameterTypes);
            return method.invoke(context, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
