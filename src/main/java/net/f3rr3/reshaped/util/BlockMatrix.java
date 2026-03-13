package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.Reshaped;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

import java.util.*;

public class BlockMatrix {
    // ── Mutable write-side state (only touched by MatrixRebuilder under REBUILDING lock) ──
    private final Map<Block, List<Block>> matrix = new LinkedHashMap<>();
    private final Map<Block, String> reasons = new HashMap<>();
    private final Map<Block, Block> mutableVariantToBase = new HashMap<>();

    // ── Immutable read-side snapshot, swapped atomically by refresh() ──
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    /**
     * An immutable snapshot of the matrix state.  All reader methods use this
     * so they never see a half-built map.
     */
    private record Snapshot(
            Map<Block, List<Block>> matrix,
            Set<Block> allBlocks,
            Map<Block, Block> variantToBase,
            Map<Block, List<Block>> columnByBlock,
            Map<Block, String> reasons
    ) {
        static final Snapshot EMPTY = new Snapshot(
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    /**
     * Rebuilds the read-side snapshot from the current mutable state.
     * Called at the end of bootstrap / rebuild while the REBUILDING lock is held.
     */
    public void refresh() {
        if (matrix.isEmpty()) {
            Reshaped.LOGGER.info("[BlockMatrix] refresh(): matrix is empty, setting EMPTY snapshot");
            snapshot = Snapshot.EMPTY;
            return;
        }

        Reshaped.LOGGER.info("[BlockMatrix] refresh(): building new snapshot from {} mutable bases", matrix.size());

        // Sort entries by base block ID
        List<Map.Entry<Block, List<Block>>> entries = new ArrayList<>(matrix.entrySet());
        entries.sort(Comparator.comparing(e -> Registries.BLOCK.getId(e.getKey()).toString()));

        // Build new maps
        Map<Block, List<Block>> newMatrix = new LinkedHashMap<>();
        Map<Block, Block> newVariantToBase = new HashMap<>();
        Set<Block> newAllBlocks = new HashSet<>();
        Map<Block, List<Block>> newColumnByBlock = new HashMap<>();
        Map<Block, String> newReasons = new HashMap<>(reasons);

        for (Map.Entry<Block, List<Block>> entry : entries) {
            Block base = entry.getKey();
            List<Block> variants = new ArrayList<>(entry.getValue());
            variants.sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).toString()));

            newMatrix.put(base, Collections.unmodifiableList(variants));
            newAllBlocks.add(base);

            for (Block variant : variants) {
                newVariantToBase.put(variant, base);
                mutableVariantToBase.put(variant, base);
                newAllBlocks.add(variant);
            }

            List<Block> column = new ArrayList<>(variants.size() + 1);
            column.add(base);
            column.addAll(variants);
            column.sort(
                    Comparator
                            .comparing((Block b) -> b.getClass().getSimpleName())
                            .thenComparing(b -> Registries.BLOCK.getId(b).toString())
            );
            List<Block> immutableColumn = Collections.unmodifiableList(column);
            newColumnByBlock.put(base, immutableColumn);
            for (Block variant : variants) {
                newColumnByBlock.put(variant, immutableColumn);
            }
        }

        // NOTE: We do NOT update the mutable `matrix` here. It must remain mutable
        // so that subsequent addVariant() calls during bootstrap/rebuild can still
        // append to the lists. Only the snapshot needs sorted/immutable copies.

        // Atomically swap the snapshot — readers immediately see the new state.
        snapshot = new Snapshot(
                Collections.unmodifiableMap(newMatrix),
                Collections.unmodifiableSet(newAllBlocks),
                Collections.unmodifiableMap(newVariantToBase),
                Collections.unmodifiableMap(newColumnByBlock),
                Collections.unmodifiableMap(newReasons)
        );
        Reshaped.LOGGER.info("[BlockMatrix] refresh(): snapshot swapped successfully. New allBlocks size: {}", newAllBlocks.size());
    }

    public void clear() {
        Reshaped.LOGGER.info("[BlockMatrix] clear() called");
        matrix.clear();
        reasons.clear();
        mutableVariantToBase.clear();
        snapshot = Snapshot.EMPTY;
    }

    // ── Write methods (called only under REBUILDING lock) ──

    public void addVariant(Block baseBlock, Block variant, boolean shouldRefresh) {
        List<Block> existing = matrix.computeIfAbsent(baseBlock, k -> new ArrayList<>());
        if (!existing.contains(variant)) {
            existing.add(variant);
            mutableVariantToBase.put(variant, baseBlock);
            Reshaped.LOGGER.debug("[BlockMatrix] addVariant: added {} to base {} (shouldRefresh={})", 
                    Registries.BLOCK.getId(variant), Registries.BLOCK.getId(baseBlock), shouldRefresh);
            if (shouldRefresh) refresh();
        }
    }

    public void addColumn(Block baseBlock, List<Block> variants, boolean shouldRefresh) {
        List<Block> existing = matrix.getOrDefault(baseBlock, new ArrayList<>());
        boolean added = false;
        for (Block v : variants) {
            if (!existing.contains(v)) {
                existing.add(v);
                mutableVariantToBase.put(v, baseBlock);
                added = true;
            }
        }
        if (added || !matrix.containsKey(baseBlock)) {
            matrix.put(baseBlock, existing);
            Reshaped.LOGGER.debug("[BlockMatrix] addColumn: added column for base {} with {} variants (shouldRefresh={})", 
                    Registries.BLOCK.getId(baseBlock), variants.size(), shouldRefresh);
            if (shouldRefresh) refresh();
        }
    }

    public void setReason(Block block, String reason) {
        reasons.put(block, reason);
    }

    // ── Read methods (all read from the volatile snapshot, with mutable fallback for write path) ──

    public Block getBaseBlock(Block variant) {
        Block base = snapshot.variantToBase().get(variant);
        if (base != null) return base;
        // Fallback for write-path: during bootstrap/rebuild the snapshot hasn't been
        // built yet, but addVariant() keeps the mutable map updated.
        return mutableVariantToBase.get(variant);
    }

    /**
     * Returns the internal mutable matrix map.
     * <b>Only for use during bootstrap/rebuild under the REBUILDING lock.</b>
     * External readers should use {@link #getMatrix()} which returns the snapshot.
     */
    public Map<Block, List<Block>> getMutableMatrix() {
        return matrix;
    }

    /**
     * Checks if a block exists in the mutable write-side state.
     * <b>Only for use during bootstrap/rebuild under the REBUILDING lock.</b>
     */
    public boolean hasBlockMutable(Block block) {
        return matrix.containsKey(block) || mutableVariantToBase.containsKey(block);
    }

    public String getReason(Block block) {
        return snapshot.reasons().getOrDefault(block, "No reason specified");
    }

    public boolean hasBlock(Block block) {
        return snapshot.allBlocks().contains(block);
    }

    public List<Block> getColumn(Block block) {
        return snapshot.columnByBlock().getOrDefault(block, Collections.emptyList());
    }

    public boolean areInSameColumn(Block first, Block second) {
        Block firstBase = resolveBase(first);
        if (firstBase == null) {
            return false;
        }
        return firstBase == resolveBase(second);
    }

    private Block resolveBase(Block block) {
        Snapshot s = snapshot;
        if (s.matrix().containsKey(block)) {
            return block;
        }
        return s.variantToBase().get(block);
    }

    public Map<Block, List<Block>> getMatrix() {
        return snapshot.matrix();
    }
}
