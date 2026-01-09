package net.f3rr3.reshaped.util;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import java.util.*;

public class BlockMatrix {
    private final Map<Block, List<Block>> matrix = new LinkedHashMap<>();
    private final Map<Block, String> reasons = new HashMap<>();
    private final Set<Block> allBlocks = new HashSet<>();
    private final Map<Block, Block> variantToBase = new HashMap<>();

    public void refresh() {
        // Skip if empty to avoid unnecessary work
        if (matrix.isEmpty()) return;

        // Sort the matrix entries by their base block ID path
        List<Map.Entry<Block, List<Block>> > entries = new ArrayList<>(matrix.entrySet());
        entries.sort(Comparator.comparing(e -> Registries.BLOCK.getId(e.getKey()).toString()));
        
        // Rebuild the linked map in order and sort variants
        matrix.clear();
        variantToBase.clear();
        allBlocks.clear();

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
        }
    }

    public Block getBaseBlock(Block variant) {
        return variantToBase.get(variant);
    }

    public void addColumn(Block baseBlock, List<Block> variants) {
        addColumn(baseBlock, variants, true);
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

    public void removeStandaloneColumns() {
        matrix.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        refresh();
    }

    public boolean hasBlock(Block block) {
        return allBlocks.contains(block);
    }

    public List<Block> getColumn(Block block) {
        for (Map.Entry<Block, List<Block>> entry : matrix.entrySet()) {
            if (entry.getKey().equals(block) || entry.getValue().contains(block)) {
                List<Block> column = new ArrayList<>();
                column.add(entry.getKey());
                column.addAll(entry.getValue());
                // Sort the final list including the base block
                column.sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).toString()));
                return column;
            }
        }
        return Collections.emptyList();
    }

    public Map<Block, List<Block>> getMatrix() {
        return Collections.unmodifiableMap(matrix);
    }
}
