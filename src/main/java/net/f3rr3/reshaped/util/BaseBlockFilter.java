package net.f3rr3.reshaped.util;

import net.minecraft.block.*;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BaseBlockFilter {
    private BaseBlockFilter() {
    }

    public static Set<Block> collectBaseCandidates() {
        Set<Block> sorted = new LinkedHashSet<>();
        Registries.BLOCK.stream()
                .filter(BaseBlockFilter::isBaseCandidate)
                .sorted(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()))
                .forEach(sorted::add);
        return sorted;
    }

    public static boolean isBaseCandidate(Block block) {
        if (block == null || block == Blocks.AIR) return false;
        if (block.asItem() == Items.AIR) return false;
        if (isIgnoredForMatrix(block)) return false;
        if (block instanceof BlockEntityProvider) return false;
        if (isLikelyVariantType(block)) return false;

        BlockState state = block.getDefaultState();
        if (state.getRenderType() != BlockRenderType.MODEL) return false;
        return state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    private static boolean isLikelyVariantType(Block block) {
        return block instanceof SlabBlock
                || block instanceof StairsBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof WallBlock
                || block instanceof PaneBlock
                || block instanceof DoorBlock
                || block instanceof TrapdoorBlock
                || block instanceof PressurePlateBlock
                || block instanceof ButtonBlock
                || block instanceof CarpetBlock;
    }

    private static boolean isIgnoredForMatrix(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        String namespace = id.getNamespace();
        String path = id.getPath();
        String className = block.getClass().getName().toLowerCase();

        return namespace.contains("copycat")
                || path.contains("copycat")
                || className.contains("copycat");
    }
}

