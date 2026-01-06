package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.Reshaped;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Map;

public class RuntimeResourceGenerator {

    public static Identifier getVariantModelId(BlockState state) {
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        String path = id.getPath();

        if (path.endsWith("_vertical_slab")) {
            return new Identifier(Reshaped.MOD_ID, "block/" + path);
        }

        if (block instanceof SlabBlock) {
            SlabType type = state.get(Properties.SLAB_TYPE);
            if (type == SlabType.DOUBLE) {
                Block base = findBaseBlock(block);
                if (base != null) {
                    Identifier baseId = Registries.BLOCK.getId(base);
                    return new Identifier(baseId.getNamespace(), "block/" + baseId.getPath());
                }
            } else if (type == SlabType.TOP) {
                return new Identifier(Reshaped.MOD_ID, "block/" + path + "_top");
            }
            return new Identifier(Reshaped.MOD_ID, "block/" + path);
        }

        if (block instanceof StairsBlock) {
            return new Identifier(Reshaped.MOD_ID, "block/" + path);
        }

        return new Identifier(Reshaped.MOD_ID, "block/" + path);
    }

    public static ModelRotation getVariantRotation(BlockState state) {
        Block block = state.getBlock();
        String path = Registries.BLOCK.getId(block).getPath();

        if (path.endsWith("_vertical_slab")) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            return switch (facing) {
                case SOUTH -> ModelRotation.X0_Y180;
                case WEST -> ModelRotation.X0_Y270;
                case EAST -> ModelRotation.X0_Y90;
                default -> ModelRotation.X0_Y0;
            };
        }

        if (block instanceof SlabBlock) {
            return ModelRotation.X0_Y0;
        }

        if (block instanceof StairsBlock) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BlockHalf half = state.get(StairsBlock.HALF);

            if (half == BlockHalf.TOP) {
                return switch (facing) {
                    case SOUTH -> ModelRotation.X180_Y180;
                    case WEST -> ModelRotation.X180_Y270;
                    case NORTH -> ModelRotation.X180_Y0;
                    default -> ModelRotation.X180_Y90; // EAST
                };
            } else {
                return switch (facing) {
                    case SOUTH -> ModelRotation.X0_Y180;
                    case WEST -> ModelRotation.X0_Y270;
                    case NORTH -> ModelRotation.X0_Y0;
                    default -> ModelRotation.X0_Y90; // EAST
                };
            }
        }

        return ModelRotation.X0_Y0;
    }

    public static String generateModelJson(String cleanPath) {
        if (Reshaped.MATRIX == null) return null;

        // Normalize path
        String path = cleanPath;
        if (path.startsWith("models/")) path = path.substring(7);
        if (path.endsWith(".json")) path = path.substring(0, path.length() - 5);

        // Handle Item Models
        if (path.startsWith("item/")) {
            String itemPath = path.substring(5);
            return "{\"parent\":\"reshaped:block/" + itemPath + "\"}";
        }

        // Handle Block Models
        String blockPath = path;
        if (blockPath.startsWith("block/")) blockPath = blockPath.substring(6);

        for (Map.Entry<Block, List<Block>> entry : Reshaped.MATRIX.getMatrix().entrySet()) {
            Block baseBlock = entry.getKey();
            Identifier baseId = Registries.BLOCK.getId(baseBlock);
            String basePath = baseId.getPath();
            String namespace = baseId.getNamespace();
            String textureId = namespace + ":block/" + basePath;

            if (blockPath.equals(basePath + "_vertical_slab")) {
                return "{\"parent\":\"minecraft:block/block\",\"textures\":{\"all\":\"" + textureId + "\",\"particle\":\"" + textureId + "\"},\"elements\":[{\"from\":[0,0,8],\"to\":[16,16,16],\"faces\":{\"down\":{\"uv\":[0,8,16,16],\"texture\":\"#all\",\"cullface\":\"down\"},\"up\":{\"uv\":[0,0,16,8],\"texture\":\"#all\",\"cullface\":\"up\"},\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#all\",\"cullface\":\"south\"},\"west\":{\"uv\":[8,0,16,16],\"texture\":\"#all\",\"cullface\":\"west\"},\"east\":{\"uv\":[0,0,8,16],\"texture\":\"#all\",\"cullface\":\"east\"}}}]}";
            } else if (blockPath.equals(basePath + "_slab_top")) {
                return "{\"parent\":\"minecraft:block/slab_top\",\"textures\":{\"bottom\":\"" + textureId + "\",\"top\":\"" + textureId + "\",\"side\":\"" + textureId + "\"}}";
            } else if (blockPath.equals(basePath + "_slab")) {
                return "{\"parent\":\"minecraft:block/slab\",\"textures\":{\"bottom\":\"" + textureId + "\",\"top\":\"" + textureId + "\",\"side\":\"" + textureId + "\"}}";
            } else if (blockPath.equals(basePath + "_stairs")) {
                return "{\"parent\":\"minecraft:block/stairs\",\"textures\":{\"bottom\":\"" + textureId + "\",\"top\":\"" + textureId + "\",\"side\":\"" + textureId + "\"}}";
            }
        }
        return null;
    }

    public static String normalizePath(String path) {
        String normalized = path;
        if (normalized.startsWith("models/")) normalized = normalized.substring(7);
        if (normalized.endsWith(".json")) normalized = normalized.substring(0, normalized.length() - 5);
        return normalized;
    }

    private static Block findBaseBlock(Block variant) {
        if (Reshaped.MATRIX == null) return null;
        for (Map.Entry<Block, List<Block>> entry : Reshaped.MATRIX.getMatrix().entrySet()) {
            if (entry.getValue().contains(variant)) return entry.getKey();
        }
        return null;
    }
}
