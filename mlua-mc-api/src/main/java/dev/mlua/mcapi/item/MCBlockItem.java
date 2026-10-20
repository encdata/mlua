package dev.mlua.mcapi.item;

import dev.mlua.mcapi.block.MCBlock;

public interface MCBlockItem extends MCItem {
    MCBlock getBlock();
}
