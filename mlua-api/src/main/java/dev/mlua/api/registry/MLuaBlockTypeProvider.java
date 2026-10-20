package dev.mlua.api.registry;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Java implementation for an addon-defined block kind selectable from a Lua block definition. */
public interface MLuaBlockTypeProvider {
   /** Validates addon-specific fields. The map is a detached snapshot and must not be retained or mutated. */
   default void validateDefinition(Map<String, Object> definition) { }

   /** Creates the registered block. The properties already include MLua's validated common block settings. */
   Block createNamed(Map<String, Object> definition, BlockBehaviour.Properties properties,
                     String registryName, Supplier<? extends BlockEntityType<?>> visualBlockEntityType);

   /** Creates the block used by an MLua scripted block with its behavior key. */
   Block createScripted(Map<String, Object> definition, BlockBehaviour.Properties properties,
                        String behaviorKey, Supplier<? extends BlockEntityType<?>> visualBlockEntityType);
}
