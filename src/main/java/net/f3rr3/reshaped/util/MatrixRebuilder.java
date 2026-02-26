package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.registry.VariantCompleter;
import net.f3rr3.reshaped.registry.VariantRegistry;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MatrixRebuilder {
    private static final AtomicBoolean REBUILDING = new AtomicBoolean(false);
    private static final Set<Block> PENDING_BOOTSTRAP_ADDS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean PENDING_REBUILD = new AtomicBoolean(false);
    private static volatile MinecraftServer PENDING_REBUILD_SERVER;

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
                    VariantCompleter.completeVariant(base, matrix);
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

        drainPendingBootstrapAdds(matrix);
    }

    public static void rebuild(BlockMatrix matrix, MinecraftServer server) {
        if (matrix == null || server == null) return;
        if (!REBUILDING.compareAndSet(false, true)) {
            PENDING_REBUILD_SERVER = server;
            PENDING_REBUILD.set(true);
            return;
        }

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
                    VariantCompleter.completeVariant(base, matrix);
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

        drainPendingBootstrapAdds(matrix);

        if (PENDING_REBUILD.getAndSet(false)) {
            MinecraftServer pendingServer = PENDING_REBUILD_SERVER;
            PENDING_REBUILD_SERVER = null;
            if (pendingServer != null) {
                rebuild(matrix, pendingServer);
            }
        }
    }

    public static void bootstrapAddedBlock(BlockMatrix matrix, Block candidate) {
        if (matrix == null || candidate == null) return;
        if (!REBUILDING.compareAndSet(false, true)) {
            PENDING_BOOTSTRAP_ADDS.add(candidate);
            return;
        }

        try {
            addBootstrapCandidate(matrix, candidate);
            matrix.refresh();
        } catch (Exception e) {
            Reshaped.LOGGER.warn("Failed to add bootstrap candidate {}", candidate, e);
        } finally {
            REBUILDING.set(false);
        }

        drainPendingBootstrapAdds(matrix);
    }

    private static void addBootstrapCandidate(BlockMatrix matrix, Block candidate) {
        if (!BaseBlockFilter.isBaseCandidate(candidate)) {
            return;
        }
        if (matrix.getMatrix().containsKey(candidate) || matrix.hasBlock(candidate)) {
            return;
        }

        matrix.addColumn(candidate, List.of(), false);
        matrix.setReason(candidate, "Base block selected by state-based filter");
        VariantCompleter.completeVariant(candidate, matrix);
        VariantRegistry.registerAll(candidate, matrix);
    }

    private static void drainPendingBootstrapAdds(BlockMatrix matrix) {
        if (matrix == null) return;

        while (true) {
            if (PENDING_BOOTSTRAP_ADDS.isEmpty()) return;
            if (!REBUILDING.compareAndSet(false, true)) return;

            try {
                List<Block> pending = new ArrayList<>(PENDING_BOOTSTRAP_ADDS);
                pending.forEach(PENDING_BOOTSTRAP_ADDS::remove);
                pending.sort(Comparator.comparing(a -> Registries.BLOCK.getId(a).toString()));

                boolean changed = false;
                for (Block block : pending) {
                    int before = matrix.getMatrix().size();
                    addBootstrapCandidate(matrix, block);
                    if (matrix.getMatrix().size() != before) {
                        changed = true;
                    }
                }

                if (changed) {
                    matrix.refresh();
                }
            } catch (Exception e) {
                Reshaped.LOGGER.warn("Failed while draining pending bootstrap candidates", e);
            } finally {
                REBUILDING.set(false);
            }
        }
    }
}
