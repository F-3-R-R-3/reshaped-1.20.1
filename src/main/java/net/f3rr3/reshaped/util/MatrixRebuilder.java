package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.registry.VariantRegistry;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;

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
                matrix.addColumn(base, List.of(), false);
                matrix.setReason(base, "Base block selected by state-based filter");
            }

            List<Block> bases = new ArrayList<>(matrix.getMatrix().keySet());
            for (Block base : bases) {
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

                matrix.addVariant(base, variant, false);
                matrix.setReason(variant, association.reason());
                associationCount++;
            }

            List<Block> bases = new ArrayList<>(matrix.getMatrix().keySet());
            for (Block base : bases) {
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
}
