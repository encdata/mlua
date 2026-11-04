package dev.mlua.mcapi.client;

import dev.mlua.mcapi.core.MCVec3;

public interface MCCamera {
    MCVec3 getPosition();
    float getXRot();
    float getYRot();
    MCCameraType getCameraType();

    enum MCCameraType {
        FIRST_PERSON,
        THIRD_PERSON_BACK,
        THIRD_PERSON_FRONT
    }
}
