package net.f3rr3.reshaped.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.registry.VariantRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.client.render.model.json.ModelVariant;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RuntimeResourceGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String getTemplateType(Block block, Identifier id) {
        String path = id.getPath();
        if (path.endsWith("_vertical_slab")) return "vertical_slab";
        if (path.endsWith("_vertical_stairs")) return "vertical_stairs";
        if (path.endsWith("_corner")) return "corner";
        if (path.endsWith("_step")) return "step";
        if (block instanceof StairsBlock) return "stairs";
        if (block instanceof SlabBlock) return "slab";
        return null;
    }

    public static String serializeState(BlockState state) {
        StringBuilder sb = new StringBuilder();
        List<net.minecraft.state.property.Property<?>> properties = new java.util.ArrayList<>(state.getProperties());
        properties.sort(java.util.Comparator.comparing(net.minecraft.state.property.Property::getName));
        for (int i = 0; i < properties.size(); i++) {
            net.minecraft.state.property.Property<?> property = properties.get(i);
            sb.append(property.getName()).append("=").append(state.get(property).toString().toLowerCase());
            if (i < properties.size() - 1) sb.append(",");
        }
        return sb.toString();
    }

    public static com.google.gson.JsonElement findMatchingVariant(com.google.gson.JsonObject variants, String stateString) {
        if (variants.has(stateString)) return variants.get(stateString);
        for (String key : variants.keySet()) {
            if (key.isEmpty() || matches(stateString, key)) return variants.get(key);
        }
        return null;
    }

    private static boolean matches(String state, String variant) {
        String[] variantParts = variant.split(",");
        for (String part : variantParts) {
            if (!state.contains(part)) return false;
        }
        return true;
    }

    public static ModelVariant parseVariant(com.google.gson.JsonElement elem) {
        if (elem.isJsonArray()) return parseVariant(elem.getAsJsonArray().get(0));
        JsonObject obj = elem.getAsJsonObject();
        String model = obj.get("model").getAsString();
        int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
        int y = obj.has("y") ? obj.get("y").getAsInt() : 0;
        boolean uvlock = obj.has("uvlock") && obj.get("uvlock").getAsBoolean();
        int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
        ModelRotation rotation = ModelRotation.get(x, y);
        return new ModelVariant(new Identifier(model), rotation.getRotation(), uvlock, weight);
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

        // Delegate to VariantRegistry for custom variants
        String baseBlockPath = blockPath;
        if (blockPath.contains("_corner_") && blockPath.length() >= 9) {
            int lastUnderscore = blockPath.lastIndexOf("_");
            if (blockPath.length() - lastUnderscore == 9) {
                baseBlockPath = blockPath.substring(0, lastUnderscore);
            }
        }

        baseBlockPath = baseBlockPath
                .replace("_up", "")
                .replace("_down", "")
                .replace("_plus_x", "")
                .replace("_minus_x", "")
                .replace("_plus_y", "")
                .replace("_minus_y", "")
                .replace("_north", "")
                .replace("_south", "")
                .replace("_east", "")
                .replace("_west", "");

        String variantJson = VariantRegistry.generateModelJson(blockPath, Registries.BLOCK.get(new Identifier(Reshaped.MOD_ID, baseBlockPath)));
        if (variantJson != null) return variantJson;

        // Slab and Stair fallbacks (Efficient lookup)
        Identifier blockId = new Identifier(Reshaped.MOD_ID, blockPath.replace("_top", "").replace("_inner", "").replace("_outer", "").replace("_2", "").replace("_3", ""));
        Block block = Registries.BLOCK.get(blockId);
        Block baseBlock = Reshaped.MATRIX != null ? Reshaped.MATRIX.getBaseBlock(block) : null;

        if (baseBlock != null) {
            Map<String, String> textures = getModelTextures(baseBlock);

            if (block instanceof net.f3rr3.reshaped.block.VerticalSlabBlock) {
                return generateModelFromTemplate("block/vertical_slab", textures);
            } else if (block instanceof net.f3rr3.reshaped.block.VerticalStairsBlock) {
                return generateModelFromTemplate("block/verical_stairs", textures);
            } else if (block instanceof net.f3rr3.reshaped.block.StepBlock) {
                if (blockPath.endsWith("_2")) {
                    return generateStepModel(2, textures);
                } else if (blockPath.endsWith("_3")) {
                    return generateStepModel(3, textures);
                }
                return generateModelFromTemplate("block/step", textures);
            } else if (block instanceof SlabBlock) {
                if (blockPath.endsWith("_top")) {
                    return generateSimpleModel("minecraft:block/slab_top", textures);
                }
                return generateSimpleModel("minecraft:block/slab", textures);
            } else if (block instanceof StairsBlock) {
                if (blockPath.endsWith("_inner")) {
                    return generateSimpleModel("minecraft:block/inner_stairs", textures);
                } else if (blockPath.endsWith("_outer")) {
                    return generateSimpleModel("minecraft:block/outer_stairs", textures);
                }
                return generateSimpleModel("minecraft:block/stairs", textures);
            }
        }
        return null;
    }

    public static String generateSimpleModel(String parent, Map<String, String> textures) {
        StringBuilder json = new StringBuilder();
        json.append("{\"parent\":\"").append(parent).append("\",");
        json.append("\"textures\":{");

        boolean first = true;
        for (Map.Entry<String, String> entry : textures.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }

        // Ensure standard textures are present if missing
        String all = textures.get("all");
        if (all != null) {
            String[] std = {"top", "bottom", "side", "particle"};
            for (String s : std) {
                if (!textures.containsKey(s)) {
                    json.append(",\"").append(s).append("\":\"").append(all).append("\"");
                }
            }
        }

        json.append("}}");
        return json.toString();
    }

    public static String generateModelFromTemplate(String templatePath, Map<String, String> textures) {
        JsonObject template = loadTemplateJson("models/" + templatePath + ".json");
        if (template == null) return null;

        if (template.has("textures")) {
            applyTextures(template.getAsJsonObject("textures"), textures);
        }

        return template.toString();
    }

    private static void applyTextures(JsonObject texObj, Map<String, String> textures) {
        for (Map.Entry<String, String> entry : textures.entrySet()) {
            texObj.addProperty(entry.getKey(), entry.getValue());
        }

        // Ensure standard textures are present if missing
        String all = textures.get("all");
        if (all != null) {
            if (!texObj.has("top")) texObj.addProperty("top", all);
            if (!texObj.has("bottom")) texObj.addProperty("bottom", all);
            if (!texObj.has("side")) texObj.addProperty("side", all);
        }
    }

    private static String generateStepModel(int count, Map<String, String> textures) {
        JsonObject template = loadTemplateJson("models/block/step.json");
        if (template == null) return null;

        if (template.has("textures")) {
            applyTextures(template.getAsJsonObject("textures"), textures);
        }

        if (template.has("elements")) {
            com.google.gson.JsonArray elements = template.getAsJsonArray("elements");
            JsonObject baseElement = elements.get(0).getAsJsonObject().deepCopy();

            if (count >= 2) {
                // Add second step (Top half)
                JsonObject step2 = baseElement.deepCopy();
                com.google.gson.JsonArray from = step2.getAsJsonArray("from");
                com.google.gson.JsonArray to = step2.getAsJsonArray("to");
                from.set(1, new com.google.gson.JsonPrimitive(8));
                to.set(1, new com.google.gson.JsonPrimitive(16));
                
                // Adjust cullfaces for internal faces if needed, but for simplicity we'll just add it
                elements.add(step2);
            }
            
            if (count >= 3) {
                // Add third step (Bottom half, opposite side)
                JsonObject step3 = baseElement.deepCopy();
                com.google.gson.JsonArray from = step3.getAsJsonArray("from");
                com.google.gson.JsonArray to = step3.getAsJsonArray("to");
                
                // If base is 8-16 on X, make this 0-8 on X
                double f0 = from.get(0).getAsDouble();
                if (f0 == 8) {
                    from.set(0, new com.google.gson.JsonPrimitive(0));
                    to.set(0, new com.google.gson.JsonPrimitive(8));
                } else {
                    from.set(0, new com.google.gson.JsonPrimitive(8));
                    to.set(0, new com.google.gson.JsonPrimitive(16));
                }
                elements.add(step3);
            }
        }

        return template.toString();
    }

    public static String loadTemplate(String path) {
        Identifier id = new Identifier(Reshaped.MOD_ID, "templates/" + path);
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
        if (resource.isPresent()) {
            try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream())) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            } catch (Exception e) {
                Reshaped.LOGGER.error("Failed to load template: " + id, e);
            }
        }
        return null;
    }

    public static JsonObject loadTemplateJson(String path) {
        String template = loadTemplate(path);
        if (template != null) {
            try {
                return JsonParser.parseString(template).getAsJsonObject();
            } catch (Exception e) {
                Reshaped.LOGGER.error("Failed to parse template JSON: " + path, e);
            }
        }
        return null;
    }

    public static String generateBlockStateJson(Block block, String templateName, Map<String, String> placeholders) {
        String template = loadTemplate("blockstates/" + templateName + ".json");
        if (template == null) return null;

        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
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
