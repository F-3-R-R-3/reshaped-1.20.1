package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.registry.VariantRegistry;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.Block;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.property.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MatrixRebuilder {
    private static final AtomicBoolean REBUILDING = new AtomicBoolean(false);

    private MatrixRebuilder() {
    }

    public static void bootstrap(BlockMatrix matrix) {
        if (matrix == null) return;
        if (!REBUILDING.compareAndSet(false, true)) return;

        try {
            matrix.clear();

            Set<Block> baseCandidates = BaseBlockFilter.collectBaseCandidates();
            for (Block base : baseCandidates) {
                if (!isRenderablePlaceableBlock(base)) continue;
                matrix.addColumn(base, List.of(), false);
                matrix.setReason(base, "Base block selected by state-based filter");
            }

            List<Block> bases = new ArrayList<>(matrix.getMatrix().keySet());
            for (Block base : bases) {
                if (!canGenerateVariantsForBase(base)) {
                    continue;
                }
                try {
                    VariantRegistry.registerAll(base, matrix);
                } catch (Exception e) {
                    Reshaped.LOGGER.warn("Variant registration failed for base {}", base, e);
                }
            }

            matrix.refresh();
            Reshaped.LOGGER.info(
                    "Bootstrapped block matrix: {} bases, {} columns",
                    baseCandidates.size(),
                    matrix.getMatrix().size()
            );
        } catch (Exception e) {
            Reshaped.LOGGER.error("Failed to bootstrap matrix", e);
        } finally {
            REBUILDING.set(false);
        }
    }

    public static void rebuild(BlockMatrix matrix, MinecraftServer server) {
        if (matrix == null || server == null) return;
        if (!REBUILDING.compareAndSet(false, true)) return;

        try {
            matrix.clear();

            Set<Block> baseCandidates = BaseBlockFilter.collectBaseCandidates();
            for (Block base : baseCandidates) {
                if (!isRenderablePlaceableBlock(base)) continue;
                matrix.addColumn(base, List.of(), false);
                matrix.setReason(base, "Base block selected by state-based filter");
            }

            Map<Block, RecipeAssociationService.Association> associations =
                    RecipeAssociationService.buildAssociations(server, baseCandidates);

            int associationCount = 0;
            for (Map.Entry<Block, RecipeAssociationService.Association> entry : associations.entrySet()) {
                Block variant = entry.getKey();
                RecipeAssociationService.Association association = entry.getValue();
                Block base = association.base();

                if (!baseCandidates.contains(base) || variant == base) {
                    continue;
                }
                if (!isRenderablePlaceableBlock(base) || !isRenderablePlaceableBlock(variant)) {
                    continue;
                }

                matrix.addVariant(base, variant, false);
                matrix.setReason(variant, association.reason());
                associationCount++;
            }

            List<Block> bases = new ArrayList<>(matrix.getMatrix().keySet());
            for (Block base : bases) {
                if (!canGenerateVariantsForBase(base)) {
                    continue;
                }
                try {
                    VariantRegistry.registerAll(base, matrix);
                } catch (Exception e) {
                    Reshaped.LOGGER.warn("Variant registration failed for base {}", base, e);
                }
            }

            matrix.refresh();
            Reshaped.LOGGER.info(
                    "Rebuilt block matrix: {} bases, {} recipe associations, {} columns",
                    baseCandidates.size(),
                    associationCount,
                    matrix.getMatrix().size()
            );
        } catch (Exception e) {
            Reshaped.LOGGER.error("Failed to rebuild matrix", e);
        } finally {
            REBUILDING.set(false);
        }
    }

    private static boolean isRenderablePlaceableBlock(Block block) {
        if (block == null) return false;
        if (block.asItem() == Items.AIR) return false;
        try {
            BlockState state = block.getDefaultState();
            return state.getRenderType() == BlockRenderType.MODEL;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean canGenerateVariantsForBase(Block base) {
        if (!isRenderablePlaceableBlock(base)) return false;
        if (base instanceof PillarBlock) return false;
        try {
            BlockState state = base.getDefaultState();
            return !state.contains(Properties.AXIS) && !state.contains(Properties.HORIZONTAL_AXIS);
        } catch (Throwable t) {
            return false;
        }
    }
}
