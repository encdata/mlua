package dev.mlua.mcapi.entity.attributes;

import java.util.UUID;

public interface MCAttributeModifier {
    int ADDITION = 0;
    int MULTIPLY_BASE = 1;
    int MULTIPLY_TOTAL = 2;

    UUID getId();
    String getName();
    double getAmount();
    int getOperation();

    static MCAttributeModifier create(String name, double amount, int operation, UUID id) {
        return new MCAttributeModifier() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public double getAmount() {
                return amount;
            }

            @Override
            public int getOperation() {
                return operation;
            }
        };
    }

    static MCAttributeModifier create(String name, double amount, int operation) {
        return create(name, amount, operation, UUID.nameUUIDFromBytes(name.getBytes()));
    }
}
