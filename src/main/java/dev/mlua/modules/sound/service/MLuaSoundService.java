package dev.mlua.modules.sound.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.modules.entity.MLuaEntityScopeResolver;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.runtime.client.MLuaClientSoundManager;
import dev.mlua.runtime.client.MLuaSoundOwnershipRegistry;
import java.util.Optional;
import net.minecraft.sounds.SoundSource;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Client-side sound playback with loop, stop, and entity-follow handles.")
public final class MLuaSoundService extends MLuaReflectiveService {
    private final MLuaSoundHandleFactory handleFactory;
    public static void registerSoundHandleClass(MLuaRegistration registration) {
        MLuaSoundHandleBinding.register(registration);
    }

    public MLuaSoundService(MLuaService.Context context) {
        super(context);
        this.handleFactory = new MLuaSoundHandleFactory(context);
    }

    @Override
    public String name() {
        return "SoundService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Plays a non-spatial sound and returns its live SoundHandle. Volume and pitch default to one and must be finite and non-negative. The client supports " + MLuaClientSoundManager.MAX_ACTIVE_SOUNDS_PER_OWNER + " active sounds per addon runtime and " + MLuaClientSoundManager.MAX_ACTIVE_SOUNDS + " total.")
    public LuaValue PlayLocal(String soundId, Optional<Double> volume, Optional<Double> pitch) {
        this.requireClientSide(this.name());
        float checkedVolume = MLuaLuaArguments.finiteNonNegativeFloat(
            volume.orElse(1.0), "SoundService:PlayLocal", "volume");
        float checkedPitch = MLuaLuaArguments.finiteNonNegativeFloat(
            pitch.orElse(1.0), "SoundService:PlayLocal", "pitch");
        int id = MLuaClientSoundManager.playLocalId(soundId, checkedVolume, checkedPitch, owner());
        return soundHandle(id, owner());
    }

    @MLuaDescription("Plays a sound at the local player's position and returns its live SoundHandle. Volume and pitch default to one and must be finite and non-negative. The client supports " + MLuaClientSoundManager.MAX_ACTIVE_SOUNDS_PER_OWNER + " active sounds per addon runtime and " + MLuaClientSoundManager.MAX_ACTIVE_SOUNDS + " total.")
    public LuaValue PlayAtPlayer(String soundId, Optional<Double> volume, Optional<Double> pitch) {
        this.requireClientSide(this.name());
        float checkedVolume = MLuaLuaArguments.finiteNonNegativeFloat(
            volume.orElse(1.0), "SoundService:PlayAtPlayer", "volume");
        float checkedPitch = MLuaLuaArguments.finiteNonNegativeFloat(
            pitch.orElse(1.0), "SoundService:PlayAtPlayer", "pitch");
        int id = MLuaClientSoundManager.playAtPlayerId(soundId, checkedVolume, checkedPitch, owner());
        return soundHandle(id, owner());
    }

    @MLuaDescription("Plays a sound with optional position, loop, and entity-follow settings. Volume and pitch default to one and must be finite and non-negative. The client supports " + MLuaClientSoundManager.MAX_ACTIVE_SOUNDS_PER_OWNER + " active sounds per addon runtime and " + MLuaClientSoundManager.MAX_ACTIVE_SOUNDS + " total.")
    public LuaValue Play(String soundId, Optional<LuaValue> properties) {
        this.requireClientSide(this.name());
        LuaValue propertiesValue = properties.orElse(LuaValue.NIL);
        if (!propertiesValue.isnil() && !propertiesValue.istable()) {
            throw new LuaError("[MLua] SoundService:Play properties must be a table when provided.");
        }
        LuaTable props = propertiesValue.istable() ? propertiesValue.checktable() : new LuaTable();
        float volume = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            option(props, "Volume"), 1.0f, "SoundService:Play", "volume");
        float pitch = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            option(props, "Pitch"), 1.0f, "SoundService:Play", "pitch");
        LuaValue loopedOption = option(props, "Looped");
        LuaValue loopOption = option(props, "Loop");
        if (!loopedOption.isnil() && !loopOption.isnil()) {
            throw new LuaError("[MLua] SoundService:Play options Looped and Loop are aliases; provide only one.");
        }
        LuaValue loopValue = !loopedOption.isnil() ? loopedOption : loopOption;
        if (!loopValue.isnil() && !loopValue.isboolean()) {
            String loopProperty = !loopedOption.isnil() ? "Looped" : "Loop";
            throw new LuaError("[MLua] SoundService:Play " + loopProperty + " must be a boolean.");
        }
        boolean loop = loopValue.optboolean(false);
        MLuaEntityScope followEntity = resolveOptionalEntityScope(
            option(props, "FollowEntity"));
        LuaValue categoryValue = option(props, "Category");
        SoundSource category = parseSoundSource(categoryValue, "SoundService:Play");
        Double x = null;
        Double y = null;
        Double z = null;
        double followOffsetX = 0.0;
        double followOffsetY = 0.0;
        double followOffsetZ = 0.0;
        LuaValue position = option(props, "Position");
        if (!position.isnil()) {
            double[] coordinates = MLuaLuaArguments.finiteVector3(position, "SoundService:Play position");
            x = coordinates[0];
            y = coordinates[1];
            z = coordinates[2];
        }
        LuaValue followOffset = option(props, "FollowOffset");
        if (!followOffset.isnil()) {
            double[] offset = MLuaSoundHandleFactory.vector3(followOffset, "FollowOffset");
            followOffsetX = offset[0];
            followOffsetY = offset[1];
            followOffsetZ = offset[2];
        }
        MLuaSoundOwnershipRegistry.Owner owner = owner();
        int id = MLuaClientSoundManager.play(soundId, volume, pitch, loop, x, y, z, followEntity,
            category, followOffsetX, followOffsetY, followOffsetZ, owner);
        return soundHandle(id, owner);
    }

    @MLuaDescription("Stops sounds owned by the calling mod.")
    public void Clear() {
        this.requireClientSide(this.name());
        MLuaClientSoundManager.clearOwned(owner());
    }

    @MLuaDescription("Returns live SoundHandle instances for sounds owned by the calling addon that are still playing, ordered by handle id.")
    public LuaTable GetAll() {
        this.requireClientSide(this.name());
        MLuaSoundOwnershipRegistry.Owner owner = owner();
        LuaTable result = new LuaTable();
        int index = 1;
        for (int id : MLuaClientSoundManager.activeIds(owner)) {
            result.set(index++, handleFactory.create(id, owner));
        }
        return result;
    }

    @MLuaDescription("Returns the number of sounds owned by the calling addon that are still playing.")
    public int GetCount() {
        this.requireClientSide(this.name());
        return MLuaClientSoundManager.activeIds(owner()).size();
    }

    @MLuaDescription("Returns the sound categories supported by this Minecraft version as lowercase names.")
    public LuaTable GetCategories() {
        LuaTable result = new LuaTable();
        SoundSource[] categories = SoundSource.values();
        java.util.Arrays.sort(categories, java.util.Comparator.comparing(source -> source.name().toLowerCase(java.util.Locale.ROOT)));
        for (int index = 0; index < categories.length; index++) {
            result.set(index + 1, categories[index].name().toLowerCase(java.util.Locale.ROOT));
        }
        return result;
    }

    private LuaValue soundHandle(int id, MLuaSoundOwnershipRegistry.Owner owner) {
        if (id == MLuaClientSoundManager.CAPACITY_REACHED) {
            throw new LuaError("[MLua] SoundService active sound capacity reached ("
                + MLuaClientSoundManager.MAX_ACTIVE_SOUNDS_PER_OWNER + " per addon runtime, "
                + MLuaClientSoundManager.MAX_ACTIVE_SOUNDS + " total). Stop unused sounds or clear the service.");
        }
        return id >= 0 ? handleFactory.create(id, owner) : LuaValue.NIL;
    }

    private MLuaSoundOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaSoundOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private static LuaValue option(LuaTable options, String name) {
        LuaValue canonical = options.rawget(name);
        String aliasName = name.isEmpty() ? name
            : Character.toLowerCase(name.charAt(0)) + name.substring(1);
        LuaValue alias = options.rawget(aliasName);
        if (!canonical.isnil() && !alias.isnil()) {
            throw new LuaError("[MLua] SoundService:Play options '" + name + "' and '" + aliasName
                + "' are aliases; provide only one.");
        }
        return canonical.isnil() ? alias : canonical;
    }

    private MLuaEntityScope resolveOptionalEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveOptionalClientTarget(value,
            context.mlua().registration(), "FollowEntity must reference an entity in the current client world");
    }

    private static SoundSource parseSoundSource(LuaValue value, String method) {
        if (value.isnil()) return SoundSource.PLAYERS;
        if (!value.isstring()) throw new LuaError("[MLua] " + method + " Category must be one of SoundService:GetCategories().");
        String name = value.tojstring().trim();
        if (name.isEmpty()) throw new LuaError("[MLua] " + method + " Category must be one of SoundService:GetCategories().");
        try {
            return SoundSource.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] " + method + " has unknown Category '" + name
                + "'. Use one of SoundService:GetCategories().");
        }
    }


}
