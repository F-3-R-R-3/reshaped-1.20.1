package net.f3rr3.reshaped.util;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import java.util.*;

public class BlockMatrix {
    private final Map<Block, List<Block>> matrix = new LinkedHashMap<>();
    private final Map<Block, String> reasons = new HashMap<>();
    private final Set<Block> allBlocks = new HashSet<>();

    public void refresh() {
        // Sort the matrix entries by their base block ID path
        List<Map.Entry<Block, List<Block>>> entries = new ArrayList<>(matrix.entrySet());
        entries.sort(Comparator.comparing(e -> Registries.BLOCK.getId(e.getKey()).toString()));
        
        // Rebuild the linked map in order and sort variants
        matrix.clear();
        for (Map.Entry<Block, List<Block>> entry : entries) {
            List<Block> variants = entry.getValue();
            variants.sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).toString()));
            matrix.put(entry.getKey(), variants);
        }

        allBlocks.clear();
        for (Map.Entry<Block, List<Block>> entry : matrix.entrySet()) {
            allBlocks.add(entry.getKey());
            allBlocks.addAll(entry.getValue());
        }
    }

    public void addColumn(Block baseBlock, List<Block> variants) {
        matrix.put(baseBlock, new ArrayList<>(variants));
        refresh();
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
