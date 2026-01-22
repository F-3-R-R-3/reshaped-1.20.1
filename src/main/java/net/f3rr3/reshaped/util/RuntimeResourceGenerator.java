package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.registry.VariantRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.f3rr3.reshaped.Reshaped;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RuntimeResourceGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Identifier getVariantModelId(BlockState state) {
        Identifier variantId = VariantRegistry.getModelId(state);
        if (variantId != null) return variantId;

        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        String path = id.getPath();

        if (block instanceof SlabBlock) {
            SlabType type = state.get(Properties.SLAB_TYPE);
            if (type == SlabType.DOUBLE) {
                Block base = Reshaped.MATRIX != null ? Reshaped.MATRIX.getBaseBlock(block) : null;
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
            try {
                // Get the SHAPE property to determine the correct model
                Object shapeObj = state.get(StairsBlock.SHAPE);
                if (shapeObj != null) {
                    String shape = shapeObj.toString().toLowerCase();
                    // Map shapes to model suffixes: straight (base), inner_left/inner_right (inner), outer_left/outer_right (outer)
                    if (shape.contains("inner")) {
                        return new Identifier(Reshaped.MOD_ID, "block/" + path + "_inner");
                    } else if (shape.contains("outer")) {
                        return new Identifier(Reshaped.MOD_ID, "block/" + path + "_outer");
                    }
                }
            } catch (Exception e) {
                Reshaped.LOGGER.warn("Failed to get SHAPE for stairs: " + path, e);
            }
            // Default to straight model
            return new Identifier(Reshaped.MOD_ID, "block/" + path);
        }

        return new Identifier(Reshaped.MOD_ID, "block/" + path);
    }

    public static ModelRotation getVariantRotation(BlockState state) {
        ModelRotation variantRotation = VariantRegistry.getRotation(state);
        if (variantRotation != null) return variantRotation;

        Block block = state.getBlock();

        if (block instanceof SlabBlock) {
            return ModelRotation.X0_Y0;
        }

        if (block instanceof StairsBlock) {
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            BlockHalf half = state.get(StairsBlock.HALF);
            Object shapeObj = state.get(StairsBlock.SHAPE);
            String shape = shapeObj != null ? shapeObj.toString().toLowerCase() : "straight";
            
            // For inner_left and outer_left, add an extra 90 degree rotation anticlockwise
            int extraYRotation = (shape.contains("inner_left") || shape.contains("outer_left")) ? 270 : 0;

            if (half == BlockHalf.TOP) {
                return switch (facing) {
                    case EAST -> applyExtraRotation(ModelRotation.X180_Y0, extraYRotation);
                    case SOUTH -> applyExtraRotation(ModelRotation.X180_Y90, extraYRotation);
                    case WEST -> applyExtraRotation(ModelRotation.X180_Y180, extraYRotation);
                    case NORTH -> applyExtraRotation(ModelRotation.X180_Y270, extraYRotation);
                    default -> ModelRotation.X180_Y0;
                };
            } else {
                return switch (facing) {
                    case EAST -> applyExtraRotation(ModelRotation.X0_Y0, extraYRotation);
                    case SOUTH -> applyExtraRotation(ModelRotation.X0_Y90, extraYRotation);
                    case WEST -> applyExtraRotation(ModelRotation.X0_Y180, extraYRotation);
                    case NORTH -> applyExtraRotation(ModelRotation.X0_Y270, extraYRotation);
                    default -> ModelRotation.X0_Y0;
                };
            }
        }

        return ModelRotation.X0_Y0;
    }

    private static ModelRotation applyExtraRotation(ModelRotation baseRotation, int extraYRotation) {
        if (extraYRotation == 0) {
            return baseRotation;
        }
        // Combine the base rotation with extra Y rotation (270 degrees = anticlockwise 90)
        return switch (baseRotation) {
            case X0_Y0 -> ModelRotation.X0_Y270;
            case X0_Y90 -> ModelRotation.X0_Y0;
            case X0_Y180 -> ModelRotation.X0_Y90;
            case X0_Y270 -> ModelRotation.X0_Y180;
            case X180_Y0 -> ModelRotation.X180_Y270;
            case X180_Y90 -> ModelRotation.X180_Y0;
            case X180_Y180 -> ModelRotation.X180_Y90;
            case X180_Y270 -> ModelRotation.X180_Y180;
            default -> baseRotation;
        };
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
            // If it's a vertical stair, the item should use one of the orientation models
            if (itemPath.endsWith("_vertical_stairs")) {
                return "{\"parent\":\"reshaped:block/" + itemPath + "_minus_x_minus_y\"}";
            }
            return "{\"parent\":\"reshaped:block/" + itemPath + "\"}";
        }

        // Handle Block Models
        String blockPath = path;
        if (blockPath.startsWith("block/")) blockPath = blockPath.substring(6);

        // Delegate to VariantRegistry for custom variants
        String baseBlockPath = blockPath
            .replace("_plus_x_plus_y", "")
            .replace("_minus_x_plus_y", "")
            .replace("_plus_x_minus_y", "")
            .replace("_minus_x_minus_y", "")
            .replace("_north", "")
            .replace("_south", "")
            .replace("_east", "")
            .replace("_west", "");
            
        String variantJson = VariantRegistry.generateModelJson(blockPath, Registries.BLOCK.get(new Identifier(Reshaped.MOD_ID, baseBlockPath)));
        if (variantJson != null) return variantJson;

        // Slab and Stair fallbacks (Efficient lookup)
        Identifier blockId = new Identifier(Reshaped.MOD_ID, blockPath.replace("_top", "").replace("_inner", "").replace("_outer", ""));
        Block block = Registries.BLOCK.get(blockId);
        Block baseBlock = Reshaped.MATRIX != null ? Reshaped.MATRIX.getBaseBlock(block) : null;

        if (baseBlock != null) {
            Identifier baseId = Registries.BLOCK.getId(baseBlock);
            String textureId = baseId.getNamespace() + ":block/" + baseId.getPath();

            if (blockPath.endsWith("_slab_top")) {
                return "{\"parent\":\"minecraft:block/slab_top\",\"textures\":{\"bottom\":\"" + textureId + "\",\"top\":\"" + textureId + "\",\"side\":\"" + textureId + "\"}}";
            } else if (blockPath.endsWith("_slab")) {
                return "{\"parent\":\"minecraft:block/slab\",\"textures\":{\"bottom\":\"" + textureId + "\",\"top\":\"" + textureId + "\",\"side\":\"" + textureId + "\"}}";
            } else if (blockPath.endsWith("_stairs_inner")) {
                return "{\"parent\":\"minecraft:block/inner_stairs\",\"textures\":{\"bottom\":\"" + textureId + "\",\"top\":\"" + textureId + "\",\"side\":\"" + textureId + "\"}}";
            } else if (blockPath.endsWith("_stairs_outer")) {
                return "{\"parent\":\"minecraft:block/outer_stairs\",\"textures\":{\"bottom\":\"" + textureId + "\",\"top\":\"" + textureId + "\",\"side\":\"" + textureId + "\"}}";
            } else if (blockPath.endsWith("_stairs")) {
                return "{\"parent\":\"minecraft:block/stairs\",\"textures\":{\"bottom\":\"" + textureId + "\",\"top\":\"" + textureId + "\",\"side\":\"" + textureId + "\"}}";
            }
        }
        return null;
    }

    public static Map<String, String> getModelTextures(Block block) {
        Map<String, String> textures = new HashMap<>();
        Identifier blockId = Registries.BLOCK.getId(block);
        
        // 1. Try to load the block model directly first
        Identifier modelId = new Identifier(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
        if (loadTexturesFromModel(modelId, textures)) {
            return textures;
        }

        // 2. If valid model not found, try to check blockstate for model redirection
        // (Common for waxed blocks or aliased blocks)
        Identifier blockstateId = new Identifier(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        Optional<Resource> bsResource = MinecraftClient.getInstance().getResourceManager().getResource(blockstateId);
        
        if (bsResource.isPresent()) {
             try (InputStreamReader reader = new InputStreamReader(bsResource.get().getInputStream())) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String variantModel = null;

                // Simple check: "variants" -> "" -> "model"
                if (json.has("variants")) {
                    JsonObject variants = json.getAsJsonObject("variants");
                    if (variants.has("")) {
                        JsonObject variant = variants.get("").isJsonObject() ? variants.getAsJsonObject("") : null;
                        if (variant != null && variant.has("model")) {
                            variantModel = variant.get("model").getAsString();
                        }
                    } else {
                         // Fallback: take the first key
                         String firstKey = variants.keySet().iterator().next();
                         if (variants.get(firstKey).isJsonObject()) {
                             JsonObject variant = variants.getAsJsonObject(firstKey);
                             if (variant.has("model")) {
                                 variantModel = variant.get("model").getAsString();
                             }
                         }
                    }
                }
                
                if (variantModel != null) {
                    Identifier redirectedModelId = new Identifier(
                        variantModel.contains(":") ? variantModel.split(":")[0] : "minecraft",
                        "models/" + (variantModel.contains(":") ? variantModel.split(":")[1] : variantModel) + ".json"
                    );
                    loadTexturesFromModel(redirectedModelId, textures);
                }

             } catch (Exception e) {
                 Reshaped.LOGGER.error("Failed to read blockstate for block: " + blockId, e);
             }
        }
        
        return textures;
    }

    private static boolean loadTexturesFromModel(Identifier modelId, Map<String, String> textures) {
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(modelId);
        if (resource.isPresent()) {
            try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream())) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("textures")) {
                    JsonObject textureJson = json.getAsJsonObject("textures");
                    textureJson.entrySet().forEach(entry -> textures.put(entry.getKey(), entry.getValue().getAsString()));
                    return true;
                }
            } catch (Exception e) {
                Reshaped.LOGGER.error("Failed to read model: " + modelId, e);
            }
        }
        return false;
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

    /**
     * Resolves the actual model ID for a block, handling blockstate redirection.
     * Returns null if direct model exists or if resolution fails.
     */
    public static Identifier resolveBlockModelId(Block block) {
        Identifier blockId = Registries.BLOCK.getId(block);
        
        // First check if direct model exists
        Identifier directModelId = new Identifier(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
        Optional<Resource> directResource = MinecraftClient.getInstance().getResourceManager().getResource(directModelId);
        if (directResource.isPresent()) {
            // Direct model exists, return standard path
            return new Identifier(blockId.getNamespace(), "block/" + blockId.getPath());
        }
        
        // Check blockstate for model redirection
        Identifier blockstateId = new Identifier(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        Optional<Resource> bsResource = MinecraftClient.getInstance().getResourceManager().getResource(blockstateId);
        
        if (bsResource.isPresent()) {
            try (InputStreamReader reader = new InputStreamReader(bsResource.get().getInputStream())) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String variantModel = null;

                if (json.has("variants")) {
                    JsonObject variants = json.getAsJsonObject("variants");
                    if (variants.has("")) {
                        JsonObject variant = variants.get("").isJsonObject() ? variants.getAsJsonObject("") : null;
                        if (variant != null && variant.has("model")) {
                            variantModel = variant.get("model").getAsString();
                        }
                    } else if (!variants.keySet().isEmpty()) {
                        String firstKey = variants.keySet().iterator().next();
                        if (variants.get(firstKey).isJsonObject()) {
                            JsonObject variant = variants.getAsJsonObject(firstKey);
                            if (variant.has("model")) {
                                variantModel = variant.get("model").getAsString();
                            }
                        }
                    }
                }
                
                if (variantModel != null) {
                    // Parse the model path (e.g., "minecraft:block/copper_block")
                    String namespace = variantModel.contains(":") ? variantModel.split(":")[0] : "minecraft";
                    String modelPath = variantModel.contains(":") ? variantModel.split(":")[1] : variantModel;
                    return new Identifier(namespace, modelPath);
                }
            } catch (Exception e) {
                Reshaped.LOGGER.error("Failed to resolve model for block: {}", blockId, e);
            }
        }
        
        return null;
    }
}
