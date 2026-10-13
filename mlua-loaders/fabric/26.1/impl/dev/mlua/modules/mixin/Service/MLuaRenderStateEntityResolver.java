package dev.mlua.modules.mixin.Service;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves the live entity for 26.1 render states, which no longer carry an entity id. */
final class MLuaRenderStateEntityResolver {
    private static final int CELL_SIZE = 4;
    private static WeakReference<ClientLevel> indexedLevel = new WeakReference<>(null);
    private static long indexedGameTime = Long.MIN_VALUE;
    private static Map<Cell, List<Integer>> entityIdsByCell = Map.of();

    private MLuaRenderStateEntityResolver() {}

    static int findEntityId(EntityRenderState state) {
        if (state == null) return -1;
        if (state instanceof AvatarRenderState avatar && avatar.id != 0) return avatar.id;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return -1;
        refreshIndex(level);

        EntityType<?> stateType = state.entityType;
        int cellX = cell(state.x);
        int cellY = cell(state.y);
        int cellZ = cell(state.z);
        int bestId = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int x = cellX - 1; x <= cellX + 1; x++) {
            for (int y = cellY - 1; y <= cellY + 1; y++) {
                for (int z = cellZ - 1; z <= cellZ + 1; z++) {
                    for (int entityId : entityIdsByCell.getOrDefault(new Cell(x, y, z), List.of())) {
                        Entity entity = level.getEntity(entityId);
                        if (!(entity instanceof LivingEntity) || (stateType != null && entity.getType() != stateType)) continue;
                        double dx = entity.getX() - state.x;
                        double dy = entity.getY() - state.y;
                        double dz = entity.getZ() - state.z;
                        double distance = dx * dx + dy * dy + dz * dz;
                        if (distance < bestDistance) {
                            bestId = entityId;
                            bestDistance = distance;
                        }
                    }
                }
            }
        }
        return bestDistance < 9.0 ? bestId : -1;
    }

    private static void refreshIndex(ClientLevel level) {
        long gameTime = level.getGameTime();
        if (indexedLevel.get() == level && indexedGameTime == gameTime) return;

        Map<Cell, List<Integer>> mutableIndex = new HashMap<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            Cell cell = new Cell(cell(entity.getX()), cell(entity.getY()), cell(entity.getZ()));
            mutableIndex.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(entity.getId());
        }
        Map<Cell, List<Integer>> snapshot = new HashMap<>(mutableIndex.size());
        mutableIndex.forEach((cell, ids) -> snapshot.put(cell, List.copyOf(ids)));
        entityIdsByCell = Map.copyOf(snapshot);
        indexedGameTime = gameTime;
        indexedLevel = new WeakReference<>(level);
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    private record Cell(int x, int y, int z) {}
}
