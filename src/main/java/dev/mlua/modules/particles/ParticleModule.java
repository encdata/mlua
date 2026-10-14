package dev.mlua.modules.particles;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.particles.Service.MLuaParticleService;
import dev.mlua.common.enums.Side;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public final class ParticleModule implements MLuaModule {
    @Override
    public String id() {
        return "particles";
    }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        dev.mlua.runtime.client.MLuaClientParticleManager.clearRuntime(runtimeSession);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        dev.mlua.runtime.client.MLuaClientParticleManager.clearOwned(
            new dev.mlua.runtime.client.MLuaParticleOwnershipRegistry.Owner(runtimeSession, ownerModId));
    }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override public List<String> dependencies() { return List.of("core-classes"); }

    @Override public void registerClasses(MLuaRegistration registration) {
        MLuaParticleService.registerEmitterClass(registration);
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("ParticleService", MLuaParticleService::new)
            .reflective(MLuaParticleService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("ParticleService", "MLuaParticleService",
            "Client-side particles, typed particle specs, and persistent emitters.",
            "services/particles.html");
        docs.clazz("ParticleEmitter", "MLuaParticleService",
            "A live client-side particle emitter with mutable position, rate, spread, velocity, and particle settings.",
            "classes/particle-emitter.html", "Instance");
        docs.signature("ParticleService", "ParticleService:CreateEmitter(particleId: string, position: Vector3, rate: number?, spread: number?, speed: number?)", "ParticleEmitter");
        docs.signature("ParticleService", "ParticleService:CreateTypedEmitter(spec: ParticleSpec, position: Vector3, rate: number?, spread: number?, speed: number?)", "ParticleEmitter");
        docs.guideStep("ParticleService", "Keep an emitter and update it",
            "CreateEmitter returns a live ParticleEmitter instance. Its properties stay connected to the running emitter, and inherited Instance methods expose its registered class.",
            "local Particles = Minecraft:GetService(\"ParticleService\")\nlocal emitter = Particles:CreateEmitter(\"minecraft:smoke\", Vector3.new(0, 65, 0))\n\nemitter.Position = Vector3.new(2, 65, 0)\nemitter.Rate = 12\nemitter.Enabled = true\nprint(emitter:IsA(\"ParticleEmitter\"), emitter.Id)\n\nemitter:Destroy()");
        docs.type("ParticleDefinition", null, "Options for registering an addon particle texture.");
        docs.field("ParticleDefinition", "texture", "string", "Resource identifier of the particle texture.");
    }
}
