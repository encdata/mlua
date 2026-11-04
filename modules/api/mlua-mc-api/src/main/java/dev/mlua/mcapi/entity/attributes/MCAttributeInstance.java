package dev.mlua.mcapi.entity.attributes;

public interface MCAttributeInstance {
    MCAttribute getAttribute();
    double getBaseValue();
    void setBaseValue(double value);
    double getValue();
}
