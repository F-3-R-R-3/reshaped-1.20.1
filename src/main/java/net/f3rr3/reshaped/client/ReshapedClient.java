package net.f3rr3.reshaped.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.gui.CircleTexture;
import net.f3rr3.reshaped.config.client.ModConfig;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.item.BlockItem;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReshapedClient implements ClientModInitializer {
    private static Set<Integer> getMatrixGroupSizes() {
        Set<Integer> sizes = new HashSet<>();
        if (Reshaped.MATRIX == null) return sizes;

        for (Map.Entry<Block, List<Block>> entry : Reshaped.MATRIX.getMatrix().entrySet()) {
            Block base = entry.getKey();
            List<Block> column = Reshaped.MATRIX.getColumn(base);
            if (!column.isEmpty()) {
                sizes.add(column.size());
            } else {
                // Fallback if column mapping is not available for any reason.
                sizes.add(entry.getValue().size() + 1);
            }
        }
        return sizes;
    }

    public static void refreshVariantRenderLayers() {
        if (Reshaped.MATRIX == null) return;

        // Mixed blocks can contain any material (including translucent ones like glass),
        // so they must render in a non-solid layer.
        BlockRenderLayerMap.INSTANCE.putBlock(Reshaped.MIXED_CORNER, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(Reshaped.MIXED_VERTICAL_STEP, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(Reshaped.MIXED_STEP, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(Reshaped.MIXED_VERTICAL_SLAB, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(Reshaped.MIXED_SLAB, RenderLayer.getTranslucent());

        for (Map.Entry<Block, List<Block>> entry : Reshaped.MATRIX.getMatrix().entrySet()) {
            Block baseBlock = entry.getKey();
            RenderLayer baseLayer = RenderLayers.getBlockLayer(baseBlock.getDefaultState());

            for (Block variant : entry.getValue()) {
                BlockRenderLayerMap.INSTANCE.putBlock(variant, baseLayer);
            }
        }
    }

    @Override
    public void onInitializeClient() {
        NetworkHandler.registerClientReceivers();
        ModelLoadingPlugin.register(new ReshapedModelLoadingPlugin());
        ModKeybindings.register();
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

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            private static final Identifier ID = new Identifier(Reshaped.MOD_ID, "circle_texture_reload");

            @Override
            public Identifier getFabricId() {
                return ID;
            }

            @Override
            public void reload(ResourceManager manager) {
                CircleTexture.clearCache();
                RuntimeResourceGenerator.clearCaches();

                ModConfig config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
                CircleTexture.prewarmRadialSlices(
                        getMatrixGroupSizes(),
                        config.radial.ImageResolution,
                        config.radial.ColorUnselectedSlice,
                        config.radial.ColorSelectedSlice
                );
            }
        });

        refreshVariantRenderLayers();
        registerVariantColorProviders();
    }

    private void registerVariantColorProviders() {
        if (Reshaped.MATRIX == null) return;

        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> {
            if (world != null && pos != null) {
                Block baseBlock = Reshaped.MATRIX.getBaseBlock(state.getBlock());
                if (baseBlock != null) {
                    return net.minecraft.client.MinecraftClient.getInstance().getBlockColors().getColor(baseBlock.getDefaultState(), world, pos, tintIndex);
                }
            }
            return -1;
        }, Reshaped.MATRIX.getMatrix().values().stream().flatMap(java.util.Collection::stream).toArray(Block[]::new));

        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof net.minecraft.item.BlockItem blockItem) {
                Block baseBlock = Reshaped.MATRIX.getBaseBlock(blockItem.getBlock());
                if (baseBlock != null) {
                    try {
                        java.lang.reflect.Field field = net.minecraft.client.MinecraftClient.class.getDeclaredField("itemColors");
                        field.setAccessible(true);
                        return ((net.minecraft.client.color.item.ItemColors) field.get(net.minecraft.client.MinecraftClient.getInstance())).getColor(new net.minecraft.item.ItemStack(baseBlock), tintIndex);
                    } catch (Exception e) {
                        return -1;
                    }
                }
            }
            return -1;
        }, Reshaped.MATRIX.getMatrix().values().stream().flatMap(java.util.Collection::stream).toArray(Block[]::new));
    }
}
