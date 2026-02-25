package net.f3rr3.reshaped.util;

import net.minecraft.block.*;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.server.world.ServerWorld;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BaseBlockFilter {
    private static final int PLACEMENT_PROBE_Y_OFFSET = 80;

    private BaseBlockFilter() {
    }

    public static Set<Block> collectBaseCandidates(MinecraftServer server) {
        if (server == null) {
            return Set.of();
        }

        ServerWorld world = server.getOverworld();
        if (world == null) {
            return Set.of();
        }

        Set<Block> sorted = new LinkedHashSet<>();
        Registries.BLOCK.stream()
                .filter(block -> isBaseCandidate(block, world))
                .sorted(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()))
                .forEach(sorted::add);
        return sorted;
    }

    public static boolean isBaseCandidate(Block block, ServerWorld world) {
        if (block == null || block == Blocks.AIR) return false;
        if (block.asItem() == Items.AIR) return false;
        if (isIgnoredForMatrix(block)) return false;
        if (block instanceof BlockEntityProvider) return false;
        if (isFunctionalOrReactiveBlock(block)) return false;
        if (isLikelyVariantType(block)) return false;

        BlockState state = block.getDefaultState();
        if (state.getRenderType() != BlockRenderType.MODEL) return false;
        if (!state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) return false;
        return canBePlacedNormally(block, world);
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

    private static boolean isFunctionalOrReactiveBlock(Block block) {
        if (block instanceof FallingBlock
                || block instanceof LeavesBlock
                || block instanceof PistonBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof ObserverBlock
                || block instanceof RedstoneOreBlock
                || block instanceof RedstoneLampBlock
                || block instanceof TargetBlock
                || block instanceof TntBlock
                || block instanceof SlimeBlock) {
            return true;
        }

        // Conservative fallback for modded blocks that are function/reactive but do not extend known classes.
        Identifier id = Registries.BLOCK.getId(block);
        String path = id.getPath();
        return path.contains("piston")
                || path.contains("observer")
                || path.contains("redstone")
                || path.contains("respawn_anchor")
                || path.contains("target")
                || path.contains("tnt")
                || path.contains("slime")
                || path.contains("bracket")
                || path.contains("concrete_powder")
                || path.endsWith("_powder")
                || path.contains("leaves")
                || path.contains("falling");
    }

    private static boolean canBePlacedNormally(Block block, ServerWorld world) {
        BlockState state = block.getDefaultState();
        BlockPos probePos = world.getSpawnPos().up(PLACEMENT_PROBE_Y_OFFSET);
        return state.canPlaceAt(world, probePos);
    }
}

