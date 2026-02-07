package net.f3rr3.reshaped.block.VericalStairs;

import net.f3rr3.reshaped.block.Template.ReshapedOxidizable;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Oxidizable;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

@SuppressWarnings("deprecation")
public class OxidizableVerticalStairsBlock extends VerticalStairsBlock implements ReshapedOxidizable {
    private final Oxidizable.OxidationLevel oxidationLevel;

    public OxidizableVerticalStairsBlock(OxidationLevel oxidationLevel, AbstractBlock.Settings settings) {
        super(settings);
        this.oxidationLevel = oxidationLevel;
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        this.onRandomTick(state, world, pos, random);
    }

    @Override
    public boolean hasRandomTicks(BlockState state) {
        return this.hasOxidationTicks(state);
    }

    @Override
    public OxidationLevel getDegradationLevel() {
        return this.oxidationLevel;
    }
}
