package dev.mlua.modules.sound;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.sound.Service.MLuaSoundService;
import dev.mlua.common.enums.Side;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public final class SoundModule implements MLuaModule {
    @Override
    public String id() {
        return "sound";
    }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        dev.mlua.runtime.client.MLuaClientSoundManager.clearRuntime(runtimeSession);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        dev.mlua.runtime.client.MLuaClientSoundManager.clearOwned(
            new dev.mlua.runtime.client.MLuaSoundOwnershipRegistry.Owner(runtimeSession, ownerModId));
    }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override public List<String> dependencies() { return List.of("core-classes"); }

    @Override public void registerClasses(MLuaRegistration registration) {
        MLuaSoundService.registerSoundHandleClass(registration);
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("SoundService", MLuaSoundService::new)
            .reflective(MLuaSoundService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("SoundService", "MLuaSoundService",
            "Client-side sound playback with loop, stop, and entity-follow handles.",
            "services/sound.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("SoundHandle", "MLuaSoundService",
            "A live client sound instance returned by SoundService playback functions. It exposes playback state, position, looping, volume, pitch, and entity-follow settings.",
            "classes/sound-handle.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.signature("SoundService", "SoundService:Play(soundId: string, properties: SoundPlayOptions?)", "SoundHandle?"));
        docs.register(MLuaDocBuilder.Registration.signature("SoundService", "SoundService:PlayLocal(soundId: string, volume: number?, pitch: number?)", "SoundHandle?"));
        docs.register(MLuaDocBuilder.Registration.signature("SoundService", "SoundService:PlayAtPlayer(soundId: string, volume: number?, pitch: number?)", "SoundHandle?"));
        docs.register(MLuaDocBuilder.Registration.signature("SoundHandle", "SoundHandle:SetPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("SoundHandle", "SoundHandle:SetFollowOffset(offset: Vector3)", null));
        docs.register(MLuaDocBuilder.Registration.signature("SoundHandle", "SoundHandle:AttachToEntity(entity: Entity | number)", null));
        docs.register(MLuaDocBuilder.Registration.signature("SoundHandle", "SoundHandle:ClearFollowEntity()", null));
        docs.register(MLuaDocBuilder.Registration.type("SoundPlayOptions", null, "Optional playback settings accepted by SoundService:Play."));
        docs.register(MLuaDocBuilder.Registration.field("SoundPlayOptions", "Volume", "number", "Finite, non-negative playback volume. Defaults to 1."));
        docs.register(MLuaDocBuilder.Registration.field("SoundPlayOptions", "Pitch", "number", "Finite, non-negative playback pitch. Defaults to 1."));
        docs.register(MLuaDocBuilder.Registration.field("SoundPlayOptions", "Looped", "boolean", "Whether the sound should repeat."));
        docs.register(MLuaDocBuilder.Registration.field("SoundPlayOptions", "Position", "Vector3|CFrame", "Optional world position used when FollowEntity is not set."));
        docs.register(MLuaDocBuilder.Registration.field("SoundPlayOptions", "FollowEntity", "Entity | number", "Optional entity instance or entity id to follow."));
        docs.register(MLuaDocBuilder.Registration.field("SoundPlayOptions", "FollowOffset", "Vector3", "Offset from the followed entity; used with FollowEntity."));
        docs.register(MLuaDocBuilder.Registration.field("SoundPlayOptions", "Category", "string", "Minecraft sound category. Defaults to players."));
        docs.register(MLuaDocBuilder.Registration.guideStep("SoundService", "Control a sound after starting it",
            "Playback functions return a SoundHandle instance. Its positions accept Vector3 or CFrame; follow offsets use native Vector3 values. Coordinate tables are rejected. Setting Position clears an entity-follow target. Its properties read and update the live sound, and Destroy stops playback.",
            "local SoundService = Minecraft:GetService(\"SoundService\")\nlocal sound = SoundService:Play(\"minecraft:ambient.cave\", {\n    Looped = true,\n    Volume = 0.6,\n    Position = CFrame.new(12, 65, -4),\n})\nif sound then\n    sound.Pitch = 0.9\n    sound.Position = CFrame.new(14, 65, -4)\n    print(sound:IsA(\"SoundHandle\"), sound.IsPlaying)\n    sound:Destroy()\nend"));
    }
}
