package net.f3rr3.reshaped.util;

import net.minecraft.block.Block;
import java.util.*;

public class BlockMatrix {
    private final Map<Block, List<Block>> matrix = new LinkedHashMap<>();

    public void addColumn(Block baseBlock, List<Block> variants) {
        matrix.put(baseBlock, new ArrayList<>(variants));
    }

    public void removeStandaloneColumns() {
        matrix.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public List<Block> getColumn(Block block) {
        for (Map.Entry<Block, List<Block>> entry : matrix.entrySet()) {
            if (entry.getKey().equals(block) || entry.getValue().contains(block)) {
                List<Block> column = new ArrayList<>();
                column.add(entry.getKey());
                column.addAll(entry.getValue());
                return column;
            }
        }
        return Collections.emptyList();
    }

    public Map<Block, List<Block>> getMatrix() {
        return Collections.unmodifiableMap(matrix);
    }
}
