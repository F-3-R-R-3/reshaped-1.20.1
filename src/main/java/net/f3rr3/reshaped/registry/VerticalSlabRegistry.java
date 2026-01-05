package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.VerticalSlabBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public class VerticalSlabRegistry {
    public static void registerVerticalSlabs(BlockMatrix matrix) {
        for (Map.Entry<Block, List<Block>> entry : matrix.getMatrix().entrySet()) {
            Block baseBlock = entry.getKey();
            Identifier baseId = Registries.BLOCK.getId(baseBlock);
            String path = baseId.getPath() + "_vertical_slab";
            Identifier id = new Identifier(Reshaped.MOD_ID, path);

            VerticalSlabBlock verticalSlab = new VerticalSlabBlock(AbstractBlock.Settings.copy(baseBlock));
            
            Registry.register(Registries.BLOCK, id, verticalSlab);
            Registry.register(Registries.ITEM, id, new BlockItem(verticalSlab, new Item.Settings()));
            
            entry.getValue().add(verticalSlab);
            Reshaped.LOGGER.info("Registered vertical slab for: " + baseId);
        }
    }
}
