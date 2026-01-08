package net.f3rr3.reshaped.util;

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
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        String path = id.getPath();

        if (path.endsWith("_vertical_slab")) {
            // Check for Double Slab property (reusing SlabType)
            if (state.get(Properties.SLAB_TYPE) == SlabType.DOUBLE) {
                // Return base block model - but handle blockstate redirection
                Block base = findBaseBlock(block);
                if (base != null) {
                    Identifier resolvedModelId = resolveBlockModelId(base);
                    if (resolvedModelId != null) {
                        return resolvedModelId;
                    }
                    // Fallback to direct path
                    Identifier baseId = Registries.BLOCK.getId(base);
                    return new Identifier(baseId.getNamespace(), "block/" + baseId.getPath());
                }
            }
            // Return direction-specific model to avoid UV rotation issues
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            String dirSuffix = "_" + facing.getName();
            return new Identifier(Reshaped.MOD_ID, "block/" + path + dirSuffix);
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
            // No rotation needed - we use direction-specific models
            return ModelRotation.X0_Y0;
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

            // Generate direction-specific vertical slab models
            if (blockPath.startsWith(basePath + "_vertical_slab")) {
                Map<String, String> textures = getModelTextures(baseBlock);
                
                String particle = textures.getOrDefault("particle", textureId);
                String all = textures.getOrDefault("all", textureId);
                
                // Texture resolution
                String up = textures.containsKey("up") ? textures.get("up") : 
                           (textures.containsKey("top") ? textures.get("top") : 
                           (textures.containsKey("end") ? textures.get("end") : all));
                           
                String down = textures.containsKey("down") ? textures.get("down") : 
                             (textures.containsKey("bottom") ? textures.get("bottom") : 
                             (textures.containsKey("end") ? textures.get("end") : all));
                             
                String side = textures.containsKey("side") ? textures.get("side") : all;
                
                // Determine which direction model is being requested
                String directionSuffix = blockPath.replace(basePath + "_vertical_slab", "");
                
                // Generate textures header (same for all directions)
                String texturesJson = "{\"parent\":\"minecraft:block/block\",\"textures\":{" +
                        "\"particle\":\"" + particle + "\"," +
                        "\"top\":\"" + up + "\"," +
                        "\"bottom\":\"" + down + "\"," +
                        "\"side\":\"" + side + "\"" +
                        "}";
                
                // Direction-specific geometry with pre-rotated elements
                // Top/bottom UVs must show the correct "slice" of the base texture
                // Note: Bottom face is viewed from below, so Z appears reversed
                if (directionSuffix.equals("_north") || directionSuffix.isEmpty()) {
                    // NORTH: occupies z=8 to z=16 (back half)
                    // Top shows back half [0,8,16,16], Bottom (viewed from below) shows front half [0,0,16,8]
                    return texturesJson + ",\"elements\":[{\"from\":[0,0,8],\"to\":[16,16,16],\"faces\":{" +
                        "\"down\":{\"uv\":[0,0,16,8],\"texture\":\"#bottom\",\"cullface\":\"down\"}," +
                        "\"up\":{\"uv\":[0,8,16,16],\"texture\":\"#top\",\"cullface\":\"up\"}," +
                        "\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}," +
                        "\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"south\"}," +
                        "\"west\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"west\"}," +
                        "\"east\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"east\"}" +
                        "}}]}";
                } else if (directionSuffix.equals("_south")) {
                    // SOUTH: occupies z=0 to z=8 (front half)
                    // Top shows front half [0,0,16,8], Bottom (viewed from below) shows back half [0,8,16,16]
                    return texturesJson + ",\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,8],\"faces\":{" +
                        "\"down\":{\"uv\":[0,8,16,16],\"texture\":\"#bottom\",\"cullface\":\"down\"}," +
                        "\"up\":{\"uv\":[0,0,16,8],\"texture\":\"#top\",\"cullface\":\"up\"}," +
                        "\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"north\"}," +
                        "\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}," +
                        "\"west\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"west\"}," +
                        "\"east\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"east\"}" +
                        "}}]}";
                } else if (directionSuffix.equals("_east")) {
                    // EAST: occupies x=0 to x=8 (west half of block)
                    return texturesJson + ",\"elements\":[{\"from\":[0,0,0],\"to\":[8,16,16],\"faces\":{" +
                        "\"down\":{\"uv\":[0,0,8,16],\"texture\":\"#bottom\",\"cullface\":\"down\"}," +
                        "\"up\":{\"uv\":[0,0,8,16],\"texture\":\"#top\",\"cullface\":\"up\"}," +
                        "\"north\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"north\"}," +
                        "\"south\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"south\"}," +
                        "\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"west\"}," +
                        "\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}" +
                        "}}]}";
                } else if (directionSuffix.equals("_west")) {
                    // WEST: occupies x=8 to x=16 (east half of block)
                    return texturesJson + ",\"elements\":[{\"from\":[8,0,0],\"to\":[16,16,16],\"faces\":{" +
                        "\"down\":{\"uv\":[8,0,16,16],\"texture\":\"#bottom\",\"cullface\":\"down\"}," +
                        "\"up\":{\"uv\":[8,0,16,16],\"texture\":\"#top\",\"cullface\":\"up\"}," +
                        "\"north\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"north\"}," +
                        "\"south\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"south\"}," +
                        "\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}," +
                        "\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"east\"}" +
                        "}}]}";
                }
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

    private static Map<String, String> getModelTextures(Block block) {
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
    private static Identifier resolveBlockModelId(Block block) {
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
                Reshaped.LOGGER.error("Failed to resolve model for block: " + blockId, e);
            }
        }
        
        return null;
    }
}
