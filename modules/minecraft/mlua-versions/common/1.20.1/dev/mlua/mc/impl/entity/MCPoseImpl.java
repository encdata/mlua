package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.entity.MCPose;

import java.util.EnumMap;

public final class MCPoseImpl {
    private static final EnumMap<net.minecraft.world.entity.Pose, MCPose> MC_TO_API = new EnumMap<>(net.minecraft.world.entity.Pose.class);
    private static final EnumMap<MCPose, net.minecraft.world.entity.Pose> API_TO_MC = new EnumMap<>(MCPose.class);

    static {
        MC_TO_API.put(net.minecraft.world.entity.Pose.STANDING, MCPose.STANDING);
        MC_TO_API.put(net.minecraft.world.entity.Pose.FALL_FLYING, MCPose.FALL_FLYING);
        MC_TO_API.put(net.minecraft.world.entity.Pose.SLEEPING, MCPose.SLEEPING);
        MC_TO_API.put(net.minecraft.world.entity.Pose.SWIMMING, MCPose.SWIMMING);
        MC_TO_API.put(net.minecraft.world.entity.Pose.SPIN_ATTACK, MCPose.SPIN_ATTACK);
        MC_TO_API.put(net.minecraft.world.entity.Pose.CROUCHING, MCPose.CROUCHING);
        MC_TO_API.put(net.minecraft.world.entity.Pose.LONG_JUMPING, MCPose.LONG_JUMPING);

        for (var entry : MC_TO_API.entrySet()) {
            API_TO_MC.put(entry.getValue(), entry.getKey());
        }
    }

    private MCPoseImpl() {
    }

    public static MCPose fromReal(net.minecraft.world.entity.Pose pose) {
        return MC_TO_API.get(pose);
    }

    public static net.minecraft.world.entity.Pose toReal(MCPose pose) {
        return API_TO_MC.get(pose);
    }
}
