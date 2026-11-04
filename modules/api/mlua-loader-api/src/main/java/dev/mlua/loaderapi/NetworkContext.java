package dev.mlua.loaderapi;

public interface NetworkContext {
    void enqueueWork(Runnable task);
    void setPacketHandled(boolean handled);
    String getSenderUuid();
    boolean isClientSide();
}
