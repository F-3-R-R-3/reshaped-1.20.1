package net.f3rr3.reshaped.util;

import net.minecraft.block.Block;

public interface BlockSourceTracker {
    void reshaped$setSourceBlock(Block block);
    Block reshaped$getSourceBlock();
}
