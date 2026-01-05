package net.f3rr3.reshaped.client;

import net.f3rr3.reshaped.Reshaped;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class ReshapedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (Reshaped.MATRIX != null) {
                    List<Block> column = Reshaped.MATRIX.getColumn(block);
                    if (!column.isEmpty()) {
                        lines.add(Text.literal("Bundled with:").formatted(Formatting.GRAY));
                        for (Block bundledBlock : column) {
                            if (bundledBlock != block) {
                                lines.add(Text.literal(" - ")
                                    .append(bundledBlock.getName())
                                    .formatted(Formatting.DARK_GRAY));
                            }
                        }
                    }
                }
            }
        });
    }
}
