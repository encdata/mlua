package dev.mlua.mcapi.client;

import java.util.Collection;

public interface MCOptions {
    int guiScale();
    double getMouseSensitivity();
    int getRenderDistance();
    Collection<Object> getKeyMappings();
}
