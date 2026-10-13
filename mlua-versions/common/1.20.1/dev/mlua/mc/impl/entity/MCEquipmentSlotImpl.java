package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.entity.MCEquipmentSlot;

import java.util.EnumMap;

public final class MCEquipmentSlotImpl {
    private static final EnumMap<net.minecraft.world.entity.EquipmentSlot, MCEquipmentSlot> MC_TO_API = new EnumMap<>(net.minecraft.world.entity.EquipmentSlot.class);
    private static final EnumMap<MCEquipmentSlot, net.minecraft.world.entity.EquipmentSlot> API_TO_MC = new EnumMap<>(MCEquipmentSlot.class);

    static {
        MC_TO_API.put(net.minecraft.world.entity.EquipmentSlot.MAINHAND, MCEquipmentSlot.MAINHAND);
        MC_TO_API.put(net.minecraft.world.entity.EquipmentSlot.OFFHAND, MCEquipmentSlot.OFFHAND);
        MC_TO_API.put(net.minecraft.world.entity.EquipmentSlot.FEET, MCEquipmentSlot.FEET);
        MC_TO_API.put(net.minecraft.world.entity.EquipmentSlot.LEGS, MCEquipmentSlot.LEGS);
        MC_TO_API.put(net.minecraft.world.entity.EquipmentSlot.CHEST, MCEquipmentSlot.CHEST);
        MC_TO_API.put(net.minecraft.world.entity.EquipmentSlot.HEAD, MCEquipmentSlot.HEAD);

        for (var entry : MC_TO_API.entrySet()) {
            API_TO_MC.put(entry.getValue(), entry.getKey());
        }
    }

    private MCEquipmentSlotImpl() {
    }

    public static MCEquipmentSlot fromReal(net.minecraft.world.entity.EquipmentSlot slot) {
        return MC_TO_API.get(slot);
    }

    public static net.minecraft.world.entity.EquipmentSlot toReal(MCEquipmentSlot slot) {
        return API_TO_MC.get(slot);
    }
}
