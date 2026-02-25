package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.Reshaped;
import net.minecraft.block.*;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BaseBlockFilter {
    private BaseBlockFilter() {
    }

    public static Set<Block> collectBaseCandidates() {
        List<Block> candidates = new ArrayList<>();

        for (Block block : Registries.BLOCK) {
            try {
                if (isBaseCandidate(block)) {
                    candidates.add(block);
                }
            } catch (Throwable t) {
                Reshaped.LOGGER.debug("Skipping block {} during base candidate scan", Registries.BLOCK.getId(block), t);
            }
        }

        candidates.sort(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()));
        return new LinkedHashSet<>(candidates);
    }

    public static boolean isBaseCandidate(Block block) {
        if (block == null || block == Blocks.AIR) return false;
        if (block.asItem() == Items.AIR) return false;
        if (isIgnoredForMatrix(block)) return false;
        if (block instanceof BlockEntityProvider) return false;
        if (isLikelyVariantType(block)) return false;
        if (block instanceof PillarBlock) return false;

        try {
            BlockState state = block.getDefaultState();
            if (state.getRenderType() != BlockRenderType.MODEL) return false;
            if (state.contains(Properties.AXIS) || state.contains(Properties.HORIZONTAL_AXIS)) return false;
            return state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        } catch (Throwable t) {
            return false;
        }
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
