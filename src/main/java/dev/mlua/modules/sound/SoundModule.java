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
        docs.service("SoundService", "MLuaSoundService",
            "Client-side sound playback with loop, stop, and entity-follow handles.",
            "services/sound.html");
        docs.clazz("SoundHandle", "MLuaSoundService",
            "A live client sound instance returned by SoundService playback functions. It exposes playback state, position, looping, volume, pitch, and entity-follow settings.",
            "classes/sound-handle.html", "Instance");
        docs.signature("SoundService", "SoundService:Play(soundId: string, properties: SoundPlayOptions?)", "SoundHandle?");
        docs.signature("SoundService", "SoundService:PlayLocal(soundId: string, volume: number?, pitch: number?)", "boolean");
        docs.signature("SoundService", "SoundService:PlayAtPlayer(soundId: string, volume: number?, pitch: number?)", "boolean");
        docs.type("SoundPlayOptions", null, "Optional playback settings accepted by SoundService:Play.");
        docs.field("SoundPlayOptions", "Volume", "number", "Playback volume. Defaults to 1.");
        docs.field("SoundPlayOptions", "Pitch", "number", "Playback pitch. Defaults to 1.");
        docs.field("SoundPlayOptions", "Looped", "boolean", "Whether the sound should repeat.");
        docs.field("SoundPlayOptions", "Position", "Vector3", "Optional world position for the sound.");
        docs.field("SoundPlayOptions", "FollowEntity", "Entity | number", "Optional entity instance or entity id to follow.");
        docs.field("SoundPlayOptions", "FollowOffset", "Vector3", "Offset from the followed entity.");
        docs.field("SoundPlayOptions", "Category", "string", "Minecraft sound category. Defaults to players.");
        docs.guideStep("SoundService", "Control a sound after starting it",
            "Playback functions return a SoundHandle instance. Its properties read and update the live sound, and Destroy stops playback.",
            "local SoundService = Minecraft:GetService(\"SoundService\")\nlocal sound = SoundService:Play(\"minecraft:ambient.cave\", { Looped = true, Volume = 0.6 })\nif sound then\n    sound.Pitch = 0.9\n    print(sound:IsA(\"SoundHandle\"), sound.IsPlaying)\n    sound:Destroy()\nend");
    }
}
