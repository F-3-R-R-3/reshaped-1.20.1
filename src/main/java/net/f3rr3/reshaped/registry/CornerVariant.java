package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.CornerBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.model.ModelRotation;
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
    public Identifier getModelId(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CornerBlock) {
            Identifier id = Registries.BLOCK.getId(block);
            String stateSuffix = "_" + (state.get(CornerBlock.DOWN_NW) ? "1" : "0") +
                    (state.get(CornerBlock.DOWN_NE) ? "1" : "0") +
                    (state.get(CornerBlock.DOWN_SW) ? "1" : "0") +
                    (state.get(CornerBlock.DOWN_SE) ? "1" : "0") +
                    (state.get(CornerBlock.UP_NW) ? "1" : "0") +
                    (state.get(CornerBlock.UP_NE) ? "1" : "0") +
                    (state.get(CornerBlock.UP_SW) ? "1" : "0") +
                    (state.get(CornerBlock.UP_SE) ? "1" : "0");
            return new Identifier(Reshaped.MOD_ID, "block/" + id.getPath() + stateSuffix);
        }
        return null;
    }

    @Override
    public ModelRotation getRotation(BlockState state) {
        if (state.getBlock() instanceof CornerBlock) return ModelRotation.X0_Y0;
        return null;
    }

    @Override
    public String generateModelJson(String path, Block block) {
        if (path.contains("_corner")) {
            Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
            if (baseBlock != null) {
                Identifier baseId = Registries.BLOCK.getId(baseBlock);
                String textureId = baseId.getNamespace() + ":block/" + baseId.getPath();

                // Extract bits from path (e.g., _11010010)
                int bitIndex = path.lastIndexOf('_');
                if (bitIndex != -1 && path.length() - bitIndex == 9) {
                    String bits = path.substring(bitIndex + 1);
                    StringBuilder elements = new StringBuilder();

                    for (int i = 0; i < 8; i++) {
                        if (bits.charAt(i) == '1') {
                            if (elements.length() > 0) elements.append(",");

                            int yMin = (i >= 4) ? 8 : 0;
                            int yMax = yMin + 8;

                            // Bits sequence: DOWN_NW, DOWN_NE, DOWN_SW, DOWN_SE, UP_NW, UP_NE, UP_SW, UP_SE
                            int cornerIdx = i % 4;
                            int xMin = (cornerIdx == 1 || cornerIdx == 3) ? 8 : 0;
                            int xMax = xMin + 8;
                            int zMin = (cornerIdx == 2 || cornerIdx == 3) ? 8 : 0;
                            int zMax = zMin + 8;

                            // Correct UV sub-mapping
                            String northUv = "[" + (16 - xMax) + "," + (16 - yMax) + "," + (16 - xMin) + "," + (16 - yMin) + "]";
                            String southUv = "[" + xMin + "," + (16 - yMax) + "," + xMax + "," + (16 - yMin) + "]";
                            String westUv = "[" + zMin + "," + (16 - yMax) + "," + zMax + "," + (16 - yMin) + "]";
                            String eastUv = "[" + (16 - zMax) + "," + (16 - yMax) + "," + (16 - zMin) + "," + (16 - yMin) + "]";
                            String upUv = "[" + xMin + "," + zMin + "," + xMax + "," + zMax + "]";
                            String downUv = "[" + xMin + "," + (16 - zMax) + "," + xMax + "," + (16 - zMin) + "]";

                            elements.append("{\"from\":[" + xMin + "," + yMin + "," + zMin + "],\"to\":[" + xMax + "," + yMax + "," + zMax + "],")
                                    .append("\"faces\":{")
                                    .append("\"north\":{\"uv\":" + northUv + ",\"texture\":\"#side\"},")
                                    .append("\"south\":{\"uv\":" + southUv + ",\"texture\":\"#side\"},")
                                    .append("\"west\":{\"uv\":" + westUv + ",\"texture\":\"#side\"},")
                                    .append("\"east\":{\"uv\":" + eastUv + ",\"texture\":\"#side\"},")
                                    .append("\"up\":{\"uv\":" + upUv + ",\"texture\":\"#side\"},")
                                    .append("\"down\":{\"uv\":" + downUv + ",\"texture\":\"#side\"}")
                                    .append("}}");
                        }
                    }

                    return "{\"parent\":\"minecraft:block/block\",\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[0,0,0],\"scale\":[0.625,0.625,0.625]},\"ground\":{\"rotation\":[0,0,0],\"translation\":[0,3,0],\"scale\":[0.25,0.25,0.25]},\"fixed\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[0.5,0.5,0.5]},\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.375,0.375,0.375]},\"firstperson_righthand\":{\"rotation\":[0,135,0],\"translation\":[0,0,0],\"scale\":[0.4,0.4,0.4]},\"firstperson_lefthand\":{\"rotation\":[0,135,0],\"translation\":[0,0,0],\"scale\":[0.4,0.4,0.4]}},\"textures\":{\"side\":\"" + textureId + "\",\"particle\":\"" + textureId + "\"},\"elements\":[" + elements + "]}";
                } else if (path.endsWith("_corner")) {
                    // Item model default (just NW corner bitmask: 10000000)
                    return generateModelJson(path + "_10000000", block);
                }
            }
        }
        return null;
    }
}
