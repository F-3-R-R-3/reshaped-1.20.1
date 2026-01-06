package net.f3rr3.reshaped.mixin;

import net.f3rr3.reshaped.util.BlockSourceTracker;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class BlockMixin implements BlockSourceTracker {
    @Unique
    private Block reshaped$sourceBlock;

    @Override
    public void reshaped$setSourceBlock(Block block) {
        this.reshaped$sourceBlock = block;
    }

    @Override
    public Block reshaped$getSourceBlock() {
        return this.reshaped$sourceBlock;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(AbstractBlock.Settings settings, CallbackInfo ci) {
        Block source = ((BlockSourceTracker) settings).reshaped$getSourceBlock();
        if (source != null) {
            this.reshaped$sourceBlock = source;
        }
    }
}
