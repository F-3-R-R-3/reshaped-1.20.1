package net.f3rr3.reshaped.util;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

import java.util.*;

public class BlockMatrix {
    private final Map<Block, List<Block>> matrix = new LinkedHashMap<>();
    private final Map<Block, String> reasons = new HashMap<>();
    private final Set<Block> allBlocks = new HashSet<>();
    private final Map<Block, Block> variantToBase = new HashMap<>();
    private final Map<Block, List<Block>> columnByBlock = new HashMap<>();

    public void refresh() {
        // Skip if empty to avoid unnecessary work
        if (matrix.isEmpty()) {
            variantToBase.clear();
            allBlocks.clear();
            columnByBlock.clear();
            return;
        }

        // Sort the matrix entries by their base block ID path
        List<Map.Entry<Block, List<Block>>> entries = new ArrayList<>(matrix.entrySet());
        entries.sort(Comparator.comparing(e -> Registries.BLOCK.getId(e.getKey()).toString()));

        // Rebuild the linked map in order and sort variants
        matrix.clear();
        variantToBase.clear();
        allBlocks.clear();
        columnByBlock.clear();

        for (Map.Entry<Block, List<Block>> entry : entries) {
            Block base = entry.getKey();
            List<Block> variants = entry.getValue();
            variants.sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).toString()));

            matrix.put(base, variants);
            allBlocks.add(base);

            // Map variants back to base for fast lookup
            for (Block variant : variants) {
                variantToBase.put(variant, base);
                allBlocks.add(variant);
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
            columnByBlock.put(base, immutableColumn);
            for (Block variant : variants) {
                columnByBlock.put(variant, immutableColumn);
            }
        }
    }

    public Block getBaseBlock(Block variant) {
        return variantToBase.get(variant);
    }

    public void addVariant(Block baseBlock, Block variant, boolean shouldRefresh) {
        List<Block> existing = matrix.computeIfAbsent(baseBlock, k -> new ArrayList<>());
        if (!existing.contains(variant)) {
            existing.add(variant);
            if (shouldRefresh) refresh();
        }
    }

    public void addColumn(Block baseBlock, List<Block> variants, boolean shouldRefresh) {
        List<Block> existing = matrix.getOrDefault(baseBlock, new ArrayList<>());
        boolean added = false;
        for (Block v : variants) {
            if (!existing.contains(v)) {
                existing.add(v);
                added = true;
            }
        }
        if (added || !matrix.containsKey(baseBlock)) {
            matrix.put(baseBlock, existing);
            if (shouldRefresh) refresh();
        }
    }

    public void setReason(Block block, String reason) {
        reasons.put(block, reason);
    }

    public String getReason(Block block) {
        return reasons.getOrDefault(block, "No reason specified");
    }

    public boolean hasBlock(Block block) {
        return allBlocks.contains(block);
    }

    public List<Block> getColumn(Block block) {
        return columnByBlock.getOrDefault(block, Collections.emptyList());
    }

    public boolean areInSameColumn(Block first, Block second) {
        Block firstBase = resolveBase(first);
        if (firstBase == null) {
            return false;
        }
        return firstBase == resolveBase(second);
    }

    private Block resolveBase(Block block) {
        if (matrix.containsKey(block)) {
            return block;
        }
        return variantToBase.get(block);
    }

    public Map<Block, List<Block>> getMatrix() {
        return Collections.unmodifiableMap(matrix);
    }
}
