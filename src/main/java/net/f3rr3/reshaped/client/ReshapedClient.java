package net.f3rr3.reshaped.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.gui.ConfigScreen.ModConfig;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class ReshapedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelLoadingPlugin.register(new ReshapedModelLoadingPlugin());
        ModKeybindings.register();
        NetworkHandler.registerClientSenders();
        ClientTickHandler.register();
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            ModConfig config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
            if (stack.getItem() instanceof BlockItem blockItem && config.enableDevMode) {
                Block block = blockItem.getBlock();
                if (Reshaped.MATRIX != null) {
                    List<Block> column = Reshaped.MATRIX.getColumn(block);
                    if (!column.isEmpty()) {
                        String reason = Reshaped.MATRIX.getReason(block);
                        lines.add(Text.literal("Grouping Reason: ").formatted(Formatting.GOLD)
                                .append(Text.literal(reason).formatted(Formatting.YELLOW)));

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
