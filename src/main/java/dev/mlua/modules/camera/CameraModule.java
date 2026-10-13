package dev.mlua.modules.camera;

import dev.mlua.module.MLuaDocBuilder;

import dev.mlua.modules.camera.Service.MLuaCameraService;
import dev.mlua.module.MLuaModule;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.common.enums.Side;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class CameraModule implements MLuaModule {
    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Camera", "Camera:SetCFrame(cframe: CFrame)", null);
        docs.signature("Camera", "Camera:SetPosition(position: Vector3)", null);
        docs.signature("Camera", "Camera:SetFOV(fov: number)", null);
        docs.signature("Camera", "Camera:SetFOVMultiplier(multiplier: number)", null);
        docs.signature("Camera", "Camera:MultiplyFOV(multiplier: number)", null);
        docs.signature("Camera", "Camera:SetMode(mode: string)", null);
        docs.signature("Camera", "Camera:SetViewMode(mode: string)", null);
        docs.signature("Camera", "Camera:SetSubject(entity: Entity)", null);
        docs.signature("Camera", "Camera:LookAt(target: Vector3)", null);
        docs.signature("Camera", "Camera:SetRightHandOffset(cframe: CFrame)", null);
        docs.signature("Camera", "Camera:SetLeftHandOffset(cframe: CFrame)", null);
    }

    @Override
    public String id() { return "camera"; }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public List<ServiceEntry> services() {
        if (!LoaderApi.platform().isClient()) return List.of();
        ServiceEntry.Builder camera = ServiceEntry.builder("Camera", MLuaCameraService::new)
            .description("Client-side camera service.");
        camera.function("GetCFrame", "Returns the current camera transform.")
            .function("SetCFrame", "Sets the scriptable camera transform.")
            .function("GetPosition", "Returns the current camera position.")
            .function("SetPosition", "Sets the scriptable camera position while keeping its current look direction.")
            .function("GetLookVector", "Returns the current camera forward direction.")
            .function("LookAt", "Turns the camera toward a position or entity.")
            .function("GetFOV", "Returns the camera field of view.")
            .function("SetFOV", "Sets the camera field of view.")
            .function("GetFOVMultiplier", "Returns the current field-of-view multiplier.")
            .function("SetFOVMultiplier", "Sets the field-of-view multiplier.")
            .function("MultiplyFOV", "Multiplies the current field of view by a value.")
            .function("GetSubject", "Returns the camera subject, if one is set.")
            .function("SetSubject", "Sets the camera subject to an entity or player.")
            .function("GetMode", "Returns the current camera control mode.")
            .function("SetMode", "Sets the camera control mode.")
            .function("GetViewMode", "Returns the current first-person or third-person view mode.")
            .function("SetViewMode", "Sets the first-person or third-person view mode.")
            .function("SetRightHandOffset", "Sets the first-person right-hand camera offset.")
            .function("GetRightHandOffset", "Returns the first-person right-hand camera offset.")
            .function("SetLeftHandOffset", "Sets the first-person left-hand camera offset.")
            .function("GetLeftHandOffset", "Returns the first-person left-hand camera offset.")
            .function("Reset", "Clears scriptable camera overrides.");
        return List.of(camera.build());
    }
}
