package dev.mlua.mcapi.client;

public interface MCKeyMapping {
    String getName();
    String getCategory();
    boolean isDown();
    boolean consumeClick();
    int getKeyCode();
}
