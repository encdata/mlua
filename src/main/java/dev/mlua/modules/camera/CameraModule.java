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
            .reflective(MLuaCameraService.class);
        return List.of(camera.build());
    }
}
