package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.registry.VariantCompleter;
import net.f3rr3.reshaped.registry.VariantRegistry;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class MatrixRebuilder {
    private static final AtomicBoolean REBUILDING = new AtomicBoolean(false);
    private static final AtomicBoolean PENDING_REBUILD = new AtomicBoolean(false);
    private static final AtomicInteger BOOTSTRAP_ADD_CALLS = new AtomicInteger(0);
    private static final AtomicInteger BOOTSTRAP_ADD_SKIPPED_BUSY = new AtomicInteger(0);
    private static final AtomicInteger BOOTSTRAP_ADD_SKIPPED_NOT_BASE = new AtomicInteger(0);
    private static final AtomicInteger BOOTSTRAP_ADD_SKIPPED_ALREADY_KNOWN = new AtomicInteger(0);
    private static final AtomicInteger BOOTSTRAP_ADD_PROCESSED = new AtomicInteger(0);
    private static final AtomicBoolean HAS_BOOTSTRAPPED = new AtomicBoolean(false);
    private static volatile MinecraftServer PENDING_REBUILD_SERVER;
    /**
     * When true, the {@code RegistryEntryAddedCallback} should be ignored.
     * This is set during bootstrap/rebuild while we ourselves dynamically
     * register variant blocks (slabs, stairs, corners, etc.), preventing
     * re-entrant calls back into the matrix builder.
     */
    private static volatile boolean suppressCallback = false;

    private MatrixRebuilder() {
    }

    /**
     * Returns true when the callback should be suppressed. Called by
     * {@link BlockRegistryScanner} to avoid re-entrant processing.
     */
    public static boolean isSuppressed() {
        return suppressCallback;
    }

    /**
     * Attempts to bootstrap the matrix.
     *
     * @param forceFullScan If true, performs a full scan even if previously bootstrapped.
     *                      Used during late-sync phases (Model Loading, Server Starting).
     */
    public static void bootstrap(BlockMatrix matrix, boolean forceFullScan) {
        if (forceFullScan || HAS_BOOTSTRAPPED.compareAndSet(false, true)) {
            if (forceFullScan) {
                Reshaped.LOGGER.info("[MatrixRebuilder] Performing MANDATORY late-sync bootstrap...");
            } else {
                Reshaped.LOGGER.info("[MatrixRebuilder] Performing initial early bootstrap...");
            }
            doBootstrap(matrix);
            HAS_BOOTSTRAPPED.set(true);
        } else {
            Reshaped.LOGGER.debug("[MatrixRebuilder] bootstrap() ignored - already bootstrapped.");
        }
    }

    private static void doBootstrap(BlockMatrix matrix) {
        if (matrix == null) return;

        Reshaped.LOGGER.info("[MatrixRebuilder] doBootstrap() called on thread: {}", Thread.currentThread().getName());

        if (!REBUILDING.compareAndSet(false, true)) {
            Reshaped.LOGGER.warn("[MatrixRebuilder] doBootstrap() SKIPPED — REBUILDING already true!");
            return;
        }

        try {
            matrix.clear();

            Set<Block> baseCandidates = BaseBlockFilter.collectBaseCandidates();
            Reshaped.LOGGER.info("[MatrixRebuilder] bootstrap: collected {} base candidates", baseCandidates.size());

            for (Block base : baseCandidates) {
                matrix.addColumn(base, List.of(), false);
                matrix.setReason(base, "Base block selected by state-based filter");
            }

            Reshaped.LOGGER.info("[MatrixRebuilder] bootstrap: mutable matrix has {} entries before variant registration",
                    matrix.getMutableMatrix().size());

            // Suppress the RegistryEntryAddedCallback while we register our own variant blocks.
            Reshaped.LOGGER.info("[MatrixRebuilder] bootstrap: setting suppressCallback = true");
            suppressCallback = true;
            try {
                List<Block> bases = new ArrayList<>(matrix.getMutableMatrix().keySet());
                Reshaped.LOGGER.info("[MatrixRebuilder] bootstrap: registering variants for {} bases", bases.size());

                int completed = 0;
                boolean frozen = isRegistryFrozen();
                if (frozen) {
                    Reshaped.LOGGER.info("[MatrixRebuilder] Registry is frozen. Skipping dynamic registration pass, will only index existing variants.");
                }

                for (Block base : bases) {
                    try {
                        VariantCompleter.completeVariant(base, matrix);
                        VariantRegistry.registerAll(base, matrix);
                        completed++;
                    } catch (Exception e) {
                        Reshaped.LOGGER.warn("[MatrixRebuilder] bootstrap: variant processing FAILED for base {} ({})",
                                Registries.BLOCK.getId(base), e.getMessage(), e);
                    }
                }
                Reshaped.LOGGER.info("[MatrixRebuilder] bootstrap: completed variant registration for {}/{} bases", completed, bases.size());
            } finally {
                suppressCallback = false;
                Reshaped.LOGGER.info("[MatrixRebuilder] bootstrap: set suppressCallback = false");
            }

            Reshaped.LOGGER.info("[MatrixRebuilder] bootstrap: mutable matrix has {} entries before refresh()",
                    matrix.getMutableMatrix().size());

            // Log a summary of all bases and their variant counts
            for (Map.Entry<Block, List<Block>> entry : matrix.getMutableMatrix().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    Reshaped.LOGGER.debug("[MatrixRebuilder] bootstrap: {} -> {} variants",
                            Registries.BLOCK.getId(entry.getKey()), entry.getValue().size());
                }
            }

            matrix.refresh();

            Reshaped.LOGGER.info(
                    "[MatrixRebuilder] Bootstrapped block matrix: {} bases, {} columns (snapshot matrix size: {})",
                    baseCandidates.size(),
                    matrix.getMutableMatrix().size(),
                    matrix.getMatrix().size()
            );
        } catch (Exception e) {
            Reshaped.LOGGER.error("[MatrixRebuilder] Failed to bootstrap matrix", e);
        } finally {
            REBUILDING.set(false);
            Reshaped.LOGGER.info("[MatrixRebuilder] bootstrap: set REBUILDING = false");
        }
    }

    public static void rebuild(BlockMatrix matrix, MinecraftServer server) {
        if (matrix == null || server == null) return;

        Reshaped.LOGGER.info("[MatrixRebuilder] rebuild() called on thread: {}", Thread.currentThread().getName());

        if (!REBUILDING.compareAndSet(false, true)) {
            Reshaped.LOGGER.warn("[MatrixRebuilder] rebuild() DEFERRED — REBUILDING already true, queuing pending rebuild");
            PENDING_REBUILD_SERVER = server;
            PENDING_REBUILD.set(true);
            return;
        }

        try {
            matrix.clear();

            Set<Block> baseCandidates = BaseBlockFilter.collectBaseCandidates();
            Reshaped.LOGGER.info("[MatrixRebuilder] rebuild: collected {} base candidates", baseCandidates.size());

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

            Reshaped.LOGGER.info("[MatrixRebuilder] rebuild: {} recipe associations added", associationCount);

            // Suppress the RegistryEntryAddedCallback while we register our own variant blocks.
            Reshaped.LOGGER.info("[MatrixRebuilder] rebuild: setting suppressCallback = true");
            suppressCallback = true;
            try {
                List<Block> bases = new ArrayList<>(matrix.getMutableMatrix().keySet());
                Reshaped.LOGGER.info("[MatrixRebuilder] rebuild: registering variants for {} bases", bases.size());

                int completed = 0;
                boolean frozen = isRegistryFrozen();
                if (frozen) {
                    Reshaped.LOGGER.info("[MatrixRebuilder] Registry is frozen. Skipping dynamic registration pass in rebuild().");
                }

                for (Block base : bases) {
                    try {
                        VariantCompleter.completeVariant(base, matrix);
                        VariantRegistry.registerAll(base, matrix);
                        completed++;
                    } catch (Exception e) {
                        Reshaped.LOGGER.warn("[MatrixRebuilder] rebuild: variant processing FAILED for base {} ({})",
                                Registries.BLOCK.getId(base), e.getMessage(), e);
                    }
                }
                Reshaped.LOGGER.info("[MatrixRebuilder] rebuild: completed variant registration for {}/{} bases", completed, bases.size());
            } finally {
                suppressCallback = false;
                Reshaped.LOGGER.info("[MatrixRebuilder] rebuild: set suppressCallback = false");
            }

            matrix.refresh();
            Reshaped.LOGGER.info(
                    "[MatrixRebuilder] Rebuilt block matrix: {} bases, {} recipe associations, {} columns (snapshot: {})",
                    baseCandidates.size(),
                    associationCount,
                    matrix.getMutableMatrix().size(),
                    matrix.getMatrix().size()
            );

            // Log incremental bootstrap stats
            Reshaped.LOGGER.info(
                    "[MatrixRebuilder] bootstrapAddedBlock stats since last rebuild: calls={}, skippedBusy={}, skippedNotBase={}, skippedAlreadyKnown={}, processed={}",
                    BOOTSTRAP_ADD_CALLS.get(),
                    BOOTSTRAP_ADD_SKIPPED_BUSY.get(),
                    BOOTSTRAP_ADD_SKIPPED_NOT_BASE.get(),
                    BOOTSTRAP_ADD_SKIPPED_ALREADY_KNOWN.get(),
                    BOOTSTRAP_ADD_PROCESSED.get()
            );
        } catch (Exception e) {
            Reshaped.LOGGER.error("[MatrixRebuilder] Failed to rebuild matrix", e);
        } finally {
            REBUILDING.set(false);
        }

        if (PENDING_REBUILD.getAndSet(false)) {
            MinecraftServer pendingServer = PENDING_REBUILD_SERVER;
            PENDING_REBUILD_SERVER = null;
            if (pendingServer != null) {
                Reshaped.LOGGER.info("[MatrixRebuilder] Processing pending rebuild");
                rebuild(matrix, pendingServer);
            }
        }
    }

    /**
     * Processes a single block registered by another mod during startup.
     */
    public static void bootstrapAddedBlock(BlockMatrix matrix, Block candidate) {
        BOOTSTRAP_ADD_CALLS.incrementAndGet();

        if (matrix == null || candidate == null) return;

        String candidateId = Registries.BLOCK.getId(candidate).toString();

        if (!REBUILDING.compareAndSet(false, true)) {
            BOOTSTRAP_ADD_SKIPPED_BUSY.incrementAndGet();
            Reshaped.LOGGER.warn("[MatrixRebuilder] bootstrapAddedBlock SKIPPED (REBUILDING busy): {} on thread {}",
                    candidateId, Thread.currentThread().getName());
            return;
        }

        try {
            if (!BaseBlockFilter.isBaseCandidate(candidate)) {
                BOOTSTRAP_ADD_SKIPPED_NOT_BASE.incrementAndGet();
                Reshaped.LOGGER.debug("[MatrixRebuilder] bootstrapAddedBlock: {} is not a base candidate, skipping", candidateId);
                return;
            }

            if (matrix.getMutableMatrix().containsKey(candidate) || matrix.hasBlockMutable(candidate)) {
                BOOTSTRAP_ADD_SKIPPED_ALREADY_KNOWN.incrementAndGet();
                Reshaped.LOGGER.debug("[MatrixRebuilder] bootstrapAddedBlock: {} already in matrix, skipping", candidateId);
                return;
            }

            BOOTSTRAP_ADD_PROCESSED.incrementAndGet();
            Reshaped.LOGGER.info("[MatrixRebuilder] bootstrapAddedBlock: PROCESSING new base candidate {} on thread {}",
                    candidateId, Thread.currentThread().getName());

            matrix.addColumn(candidate, List.of(), false);
            matrix.setReason(candidate, "Base block selected by state-based filter");

            suppressCallback = true;
            try {
                VariantCompleter.completeVariant(candidate, matrix);
                VariantRegistry.registerAll(candidate, matrix);
            } finally {
                suppressCallback = false;
            }

            int variantCount = matrix.getMutableMatrix().getOrDefault(candidate, List.of()).size();
            Reshaped.LOGGER.info("[MatrixRebuilder] bootstrapAddedBlock: registered {} variants for {}",
                    variantCount, candidateId);

            matrix.refresh();
        } catch (Exception e) {
            Reshaped.LOGGER.warn("[MatrixRebuilder] Failed to add bootstrap candidate {}", candidateId, e);
        } finally {
            REBUILDING.set(false);
        }
    }

    public static boolean isRegistryFrozen() {
        try {
            // In Fabric 1.20.1, SimpleRegistry (base of Registries.BLOCK) has a private boolean 'frozen' field.
            // Some versions might use different field names depending on mapping. 
            // We use reflection as a fallback, but the most reliable indicator in Fabric-land 
            // is catching the Exception OR using the Registry's lifecycle state if available.
            java.lang.reflect.Field frozenField = net.minecraft.registry.SimpleRegistry.class.getDeclaredField("frozen");
            frozenField.setAccessible(true);
            return (boolean) frozenField.get(Registries.BLOCK);
        } catch (Exception e) {
            // Fallback: This is not ideal but prevents a hard crash if reflection fails.
            // If we can't tell, we assume it might be writable until it fails.
            return false;
        }
    }
}
