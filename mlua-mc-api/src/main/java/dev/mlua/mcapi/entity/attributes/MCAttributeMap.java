package dev.mlua.mcapi.entity.attributes;

import java.util.Collection;

public interface MCAttributeMap {
    MCAttributeInstance getInstance(MCAttribute attribute);
    Collection<MCAttributeInstance> getSyncableAttributes();
}
