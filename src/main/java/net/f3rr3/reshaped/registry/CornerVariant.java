package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.CornerBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class CornerVariant implements BlockVariantType {
    private static final Map<Block, CornerBlock> BASE_TO_CORNER = new HashMap<>();

    @Override
    public String getName() {
        return "corner";
    }

    @Override
    public boolean appliesTo(Block baseBlock) {
        return true;
    }

    @Override
    public void register(Block baseBlock, BlockMatrix matrix) {
        Identifier baseId = Registries.BLOCK.getId(baseBlock);
        String baseName = baseId.getPath().replace("_planks", "").replace("_block", "");
        String path = baseName + "_corner";
        Identifier id = new Identifier(Reshaped.MOD_ID, path);

        if (Registries.BLOCK.get(id) != Blocks.AIR) return;

        CornerBlock corner = new CornerBlock(AbstractBlock.Settings.copy(baseBlock));

        Registry.register(Registries.BLOCK, id, corner);
        Registry.register(Registries.ITEM, id, new BlockItem(corner, new Item.Settings()));

        matrix.addVariant(baseBlock, corner, true);
        matrix.setReason(corner, "Dynamically registered Corner (1/8 block) for " + baseBlock.getName().getString());
        BASE_TO_CORNER.put(baseBlock, corner);
    }

    @Override
    public String generateModelJson(String path, Block block) {
        if (path.contains("_corner")) {
            Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
            if (baseBlock != null) {
                Map<String, String> textures = net.f3rr3.reshaped.util.RuntimeResourceGenerator.getModelTextures(baseBlock);

                // Extract bits from path (e.g., _11010010)
                int bitIndex = path.lastIndexOf('_');
                if (bitIndex != -1 && path.length() - bitIndex == 9) {
                    String bits = path.substring(bitIndex + 1);
                    
                    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
                    root.addProperty("parent", "minecraft:block/block");
                    
                    com.google.gson.JsonObject texturesObj = new com.google.gson.JsonObject();
                    net.f3rr3.reshaped.util.RuntimeResourceGenerator.applyTextures(texturesObj, textures);
                    root.add("textures", texturesObj);
                    
                    com.google.gson.JsonArray elements = new com.google.gson.JsonArray();
                    String[] segmentTemplates = {
                        "corner_down_nw", "corner_down_ne", "corner_down_sw", "corner_down_se",
                        "corner_up_nw", "corner_up_ne", "corner_up_sw", "corner_up_se"
                    };

                    for (int i = 0; i < 8; i++) {
                        if (bits.charAt(i) == '1') {
                            com.google.gson.JsonObject segment = net.f3rr3.reshaped.util.RuntimeResourceGenerator.loadTemplateJson("models/block/" + segmentTemplates[i] + ".json");
                            if (segment != null && segment.has("elements")) {
                                elements.addAll(segment.getAsJsonArray("elements"));
                            }
                        }
                    }
                    root.add("elements", elements);

                    return root.toString();
                } else if (path.endsWith("_corner")) {
                    // Item model default (just NW corner bitmask: 10000000)
                    return generateModelJson(path + "_10000000", block);
                }
            }
        }
        return null;
    }
}
