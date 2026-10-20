package dev.mlua.modules.particles;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.particles.service.MLuaParticleService;
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

    @Override
    public void registerEvents(dev.mlua.loaderapi.EventBridge events) {
        events.onClientWorldUnload(world ->
            dev.mlua.runtime.client.MLuaClientParticleManager.clearAll());
        events.onClientDisconnect(dev.mlua.runtime.client.MLuaClientParticleManager::clearAll);
    }

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
        docs.register(MLuaDocBuilder.Registration.service("ParticleService", "MLuaParticleService",
            "Client-side particles, typed particle specs, and persistent emitters.",
            "services/particles.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("ParticleEmitter", "MLuaParticleService",
            "A live client-side particle emitter with mutable position, rate, spread, velocity, and particle settings.",
            "classes/particle-emitter.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:SpawnLocal(particleId: string, position: Vector3|CFrame, count: number?, spread: number?, speed: number?)", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:Spawn(spec: string|ParticleSpec, position: Vector3|CFrame, count: number?, spread: number?, speed: number?)", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:SpawnBurst(spec: string|ParticleSpec, position: Vector3|CFrame, properties: ParticleBurstProperties?)", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:SpawnRing(spec: string|ParticleSpec, position: Vector3|CFrame, count: number?, radius: number?, speed: number?)", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:SpawnCone(spec: string|ParticleSpec, position: Vector3|CFrame, direction: Vector3, count: number?, angle: number?, speed: number?)", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:CreateEmitter(particleId: string, position: Vector3|CFrame, rate: number?, spread: number?, speed: number?)", "ParticleEmitter"));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:CreateTypedEmitter(spec: string|ParticleSpec, position: Vector3|CFrame, rate: number?, spread: number?, speed: number?)", "ParticleEmitter"));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:GetAll()", "ParticleEmitter[]"));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleService", "ParticleService:GetCount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleEmitter", "ParticleEmitter:SetPosition(position: Vector3|CFrame)", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleEmitter", "ParticleEmitter:GetSpreadVector(): Vector3", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleEmitter", "ParticleEmitter:SetSpreadVector(spread: Vector3)", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleEmitter", "ParticleEmitter:SetVelocity(velocity: Vector3)", null));
        docs.register(MLuaDocBuilder.Registration.signature("ParticleEmitter", "ParticleEmitter:SetOffset(offset: Vector3)", null));
        docs.register(MLuaDocBuilder.Registration.type("ParticleSpec", null, "Particle registry identifier or options for dust, block, item, and simple particles."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleSpec", "Id", "string", "Minecraft particle, block, or item identifier."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleSpec", "Kind", "string", "Particle mode: simple, dust, block, or item."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleSpec", "Red", "number", "Dust red component, from 0 through 1."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleSpec", "Green", "number", "Dust green component, from 0 through 1."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleSpec", "Blue", "number", "Dust blue component, from 0 through 1."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleSpec", "Size", "number", "Dust particle size."));
        docs.register(MLuaDocBuilder.Registration.type("ParticleBurstProperties", null, "Options for spawning a particle burst."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleBurstProperties", "Count", "whole number >= 0", "Number of particles in the burst (default 12)."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleBurstProperties", "Spread", "number|Vector3", "Scalar spread for every axis or independent spread per axis."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleBurstProperties", "SpreadX", "number", "Optional horizontal spread override."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleBurstProperties", "SpreadY", "number", "Optional vertical spread override."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleBurstProperties", "SpreadZ", "number", "Optional depth spread override."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleBurstProperties", "Velocity", "Vector3", "Initial particle velocity."));
        docs.register(MLuaDocBuilder.Registration.guideStep("ParticleService", "Keep an emitter and update it",
            "CreateEmitter returns a live ParticleEmitter instance. Its positions accept Vector3 or CFrame; spread, velocity, direction, and offset values use native Vector3 values. Coordinate tables are rejected. The client supports up to " + dev.mlua.runtime.client.MLuaClientParticleManager.MAX_EMITTERS_PER_OWNER + " active emitters per addon runtime and " + dev.mlua.runtime.client.MLuaClientParticleManager.MAX_EMITTERS + " total. Destroy emitters when they are no longer needed. The instance properties stay connected to the running emitter, and inherited Instance methods expose its registered class.",
            "local Particles = Minecraft:GetService(\"ParticleService\")\nlocal emitter = Particles:CreateEmitter(\"minecraft:smoke\", Vector3.new(0, 65, 0))\n\nemitter.Position = CFrame.new(2, 65, 0)\nemitter:SetSpreadVector(Vector3.new(0.5, 0.2, 0.5))\nemitter:SetVelocity(Vector3.new(0, 0.1, 0))\nemitter.Rate = 12\nemitter.Enabled = true\nprint(emitter:IsA(\"ParticleEmitter\"), emitter.Id)\n\nemitter:Destroy()"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ParticleService", "Spawn particles with native vector types",
            "Every particle position accepts Vector3 or CFrame. Direction and velocity use Vector3; handmade coordinate tables are rejected. A particle spec can be a registry id string or a spec table.",
            "local Particles = Minecraft:GetService(\"ParticleService\")\n\nParticles:SpawnLocal(\"minecraft:smoke\", CFrame.new(0, 65, 0), 12)\nParticles:SpawnBurst({\n    Kind = \"dust\",\n    Id = \"minecraft:dust\",\n    Red = 0.8,\n    Green = 0.1,\n    Blue = 0.05,\n    Size = 1.2,\n}, Vector3.new(2, 65, 0), {\n    Count = 20,\n    Spread = Vector3.new(0.4, 0.1, 0.4),\n    Velocity = Vector3.new(0, 0.15, 0),\n})\nParticles:SpawnCone(\"minecraft:flame\", CFrame.new(0, 65, 0), Vector3.new(0, 0, -1), 16, 12, 0.2)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ParticleService", "Query the emitters you own",
            "GetAll returns live ParticleEmitter instances created by this addon runtime, in stable id order. GetCount reports the current owned emitter count. Destroy individual emitters or call Clear when the group is no longer needed.",
            "local Particles = Minecraft:GetService(\"ParticleService\")\nfor _, emitter in ipairs(Particles:GetAll()) do\n    if not emitter.Enabled then\n        emitter:Destroy()\n    end\nend\nprint(\"Active emitters:\", Particles:GetCount())"));
        docs.register(MLuaDocBuilder.Registration.type("ParticleDefinition", null, "Options for registering an addon particle texture."));
        docs.register(MLuaDocBuilder.Registration.field("ParticleDefinition", "texture", "string", "Resource identifier of the particle texture."));
    }
}
