package dev.mlua.mc.impl.client;

import dev.mlua.mcapi.client.MCCamera;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mc.impl.core.MCVec3Impl;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

public class MCCameraImpl implements MCCamera {
    private final Camera inner;

    public MCCameraImpl(Camera inner) {
        this.inner = inner;
    }

    public static MCCameraImpl fromReal(Camera inner) {
        return inner == null ? null : new MCCameraImpl(inner);
    }

    public Camera inner() {
        return inner;
    }

    @Override
    public MCVec3 getPosition() {
        return MCVec3Impl.fromReal(inner.getPosition());
    }

    @Override
    public float getXRot() {
        return inner.getXRot();
    }

    @Override
    public float getYRot() {
        return inner.getYRot();
    }

    @Override
    public MCCameraType getCameraType() {
        CameraType type = Minecraft.getInstance().options.getCameraType();
        if (type == CameraType.FIRST_PERSON) return MCCameraType.FIRST_PERSON;
        if (type == CameraType.THIRD_PERSON_BACK) return MCCameraType.THIRD_PERSON_BACK;
        if (type == CameraType.THIRD_PERSON_FRONT) return MCCameraType.THIRD_PERSON_FRONT;
        return MCCameraType.FIRST_PERSON;
    }
}
