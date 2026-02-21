package net.f3rr3.reshaped.mixin;

import com.google.common.collect.BiMap;
import net.minecraft.block.Block;
import net.minecraft.item.HoneycombItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(HoneycombItem.class)
public interface HoneycombItemAccessor {

    @Accessor("UNWAXED_TO_WAXED_BLOCKS")
    static Supplier<BiMap<Block, Block>> getUnwaxedToWaxedSupplier() {
        throw new UnsupportedOperationException();
    }
}
