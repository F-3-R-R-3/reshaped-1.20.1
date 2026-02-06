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
    
    /**
     * Cache for analyzed texture mappings. Prevents repeated analysis of the same base block
     * during a single resource reload session. Cache is cleared when resources are reloaded.
     * <p>
     * Key: Base block
     * Value: Map of texture keys (e.g., "top", "bottom", "side") to texture resource paths
     */
    private static final Map<Block, Map<String, String>> TEXTURE_CACHE = new HashMap<>();

    public static String getTemplateType(Block block, Identifier id) {
        String path = id.getPath();
        if (path.endsWith("_vertical_slab")) return "vertical_slab";
        if (path.endsWith("_vertical_stairs")) return "vertical_stairs";
        if (path.endsWith("_corner")) return "corner";
        if (path.endsWith("_vertical_step")) return "vertical_step";
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
        String path = normalizePath(cleanPath);

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
                .replace("_west", "")
                .replaceAll("_\\d{4}$", "");

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
                return generateModelFromTemplate("block/vertical_stairs", textures);
            } else if (block instanceof net.f3rr3.reshaped.block.VerticalStepBlock) {
                String mask = extractMaskSuffix(blockPath);
                if (mask != null) {
                    boolean[] segments = parseMaskOrDefault(mask, true, false, false, false);
                    return generateVerticalStepModelForSegments(segments[0], segments[1], segments[2], segments[3], textures);
                }
                // Fallback for item model: full block or single segment?
                // StepBlock uses single segment (true, false, false, false).
                // Let's stick with single segment (NW) for the item model as well.
                return generateVerticalStepModelForSegments(true, false, false, false, textures);
            } else if (block instanceof net.f3rr3.reshaped.block.StepBlock) {
                // Check for segment mask suffix (e.g., "_1010")
                String mask = extractMaskSuffix(blockPath);
                if (mask != null) {
                    boolean[] segments = parseMaskOrDefault(mask, true, false, false, false);
                    return generateStepModelForSegments(segments[0], segments[1], segments[2], segments[3], textures);
                }
                // Fallback for base item/block (full block or single step)
                // If it's the item or just the block name, usually we want the full block or a default state
                // But for "step" block, default model is usually the single step (down front)
                // Let's assume default is DOWN_FRONT if no mask
                return generateStepModelForSegments(true, false, false, false, textures);
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

    /**
     * Generates a simple block model JSON by delegating to a parent model and applying textures.
     * Used for dynamically created slabs and stairs that follow vanilla model structures.
     * 
     * @param parent The parent model identifier (e.g., "minecraft:block/stairs")
     * @param textures The texture mappings to apply
     * @return JSON string representing the complete model
     */
    public static String generateSimpleModel(String parent, Map<String, String> textures) {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("parent", parent);
        
        com.google.gson.JsonObject texturesObj = new com.google.gson.JsonObject();
        applyTextures(texturesObj, textures);
        root.add("textures", texturesObj);
        
        return root.toString();
    }

    public static String generateModelFromTemplate(String templatePath, Map<String, String> textures) {
        JsonObject template = loadTemplateJson("models/" + templatePath + ".json");
        if (template == null) return null;

        if (template.has("textures")) {
            applyTextures(template.getAsJsonObject("textures"), textures);
        }

        return template.toString();
    }

    /**
     * Analyzes a block model's element faces to automatically determine texture mappings.
     * This enables support for blocks using non-standard texture keys (e.g., cube_column models
     * that use "end" for top/bottom and "side" for horizontal faces).
     * 
     * <p>Algorithm:
     * <ol>
     *   <li>Loads the complete model hierarchy (including all parent models)</li>
     *   <li>Examines the first model element's face definitions</li>
     *   <li>Maps each face direction (up, down, north, etc.) to its texture reference</li>
     *   <li>Resolves texture references (e.g., #end, #side) to actual texture paths</li>
     *   <li>Translates to standard template keys: up→top, down→bottom, sides→side</li>
     * </ol>
     * 
     * @param modelId The model to analyze
     * @param textures The texture map to populate with standard keys. Modified in place.
     */
    private static void analyzeFaceTextures(Identifier modelId, Map<String, String> textures) {
        try {
            JsonObject fullModel = loadModelHierarchy(modelId);
            if (fullModel == null || !fullModel.has("elements")) {
                return;
            }

            com.google.gson.JsonArray elements = fullModel.getAsJsonArray("elements");
            if (elements.isEmpty()) {
                return;
            }

            // Analyze the first element to determine face mappings
            JsonObject firstElement = elements.get(0).getAsJsonObject();
            if (!firstElement.has("faces")) {
                return;
            }

            JsonObject faces = firstElement.getAsJsonObject("faces");
            Map<String, String> faceToTexture = new HashMap<>();

            // Extract face -> texture key mappings (e.g., "up" -> "#end")
            for (String face : new String[]{"up", "down", "north", "south", "east", "west"}) {
                if (faces.has(face)) {
                    JsonObject faceObj = faces.getAsJsonObject(face);
                    if (faceObj.has("texture")) {
                        String textureRef = faceObj.get("texture").getAsString();
                        faceToTexture.put(face, textureRef);
                    }
                }
            }

            // Resolve texture references (e.g., #end -> minecraft:block/quartz_block_top)
            Map<String, String> resolvedTextures = new HashMap<>();
            for (Map.Entry<String, String> entry : faceToTexture.entrySet()) {
                String textureRef = entry.getValue();
                if (textureRef.startsWith("#")) {
                    String key = textureRef.substring(1);
                    if (textures.containsKey(key)) {
                        resolvedTextures.put(entry.getKey(), textures.get(key));
                    }
                }
            }

            // Map to standard keys for our templates
            if (resolvedTextures.containsKey("up")) {
                textures.putIfAbsent("top", resolvedTextures.get("up"));
            }
            if (resolvedTextures.containsKey("down")) {
                textures.putIfAbsent("bottom", resolvedTextures.get("down"));
            }

            // Use any horizontal face as 'side' if not already set
            for (String face : new String[]{"north", "south", "east", "west"}) {
                if (resolvedTextures.containsKey(face)) {
                    textures.putIfAbsent("side", resolvedTextures.get(face));
                    break;
                }
            }

            // Ensure particle is set
            if (!textures.containsKey("particle")) {
                if (textures.containsKey("side")) {
                    textures.put("particle", textures.get("side"));
                } else if (textures.containsKey("all")) {
                    textures.put("particle", textures.get("all"));
                }
            }

        } catch (Exception e) {
            Reshaped.LOGGER.warn("Failed to analyze face textures for model: {}", modelId, e);
        }
    }

    /**
     * Recursively loads a block model and all its parent models, merging them into a single structure.
     * This is necessary because model properties like "elements" and "textures" can be defined
     * in parent models and inherited by child models.
     * 
     * <p>Example: quartz_block extends cube_column, which extends cube, which extends block.
     * This method resolves the entire chain.
     * 
     * @param modelId The model identifier to load
     * @return Merged JSON object containing all properties from the model hierarchy, or null if not found
     */
    private static JsonObject loadModelHierarchy(Identifier modelId) {
        JsonObject merged = new JsonObject();
        JsonObject current = loadModelJson(modelId);
        
        if (current == null) {
            return null;
        }

        // Recursively load parent models
        if (current.has("parent")) {
            String parentPath = current.get("parent").getAsString();
            Identifier parentId;
            
            if (parentPath.contains(":")) {
                String[] parts = parentPath.split(":");
                parentId = new Identifier(parts[0], "models/" + parts[1] + ".json");
            } else {
                parentId = new Identifier("minecraft", "models/" + parentPath + ".json");
            }
            
            JsonObject parentModel = loadModelHierarchy(parentId);
            if (parentModel != null) {
                // Merge parent into current (parent properties first, then override with current)
                for (Map.Entry<String, com.google.gson.JsonElement> entry : parentModel.entrySet()) {
                    merged.add(entry.getKey(), entry.getValue());
                }
            }
        }

        // Override/add current model properties
        for (Map.Entry<String, com.google.gson.JsonElement> entry : current.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }

        return merged;
    }

    /**
     * Loads a single model JSON file from the resource manager.
     * 
     * @param modelId The model identifier
     * @return The parsed JSON object, or null if the model doesn't exist or fails to parse
     */
    private static JsonObject loadModelJson(Identifier modelId) {
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(modelId);
        if (resource.isPresent()) {
            try (InputStreamReader reader = new InputStreamReader(resource.get().getInputStream())) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                // Silent fail for missing models
            }
        }
        return null;
    }

    /**
     * Applies texture mappings to a model's textures object, with comprehensive fallback logic.
     * Handles various texture naming conventions from vanilla and modded blocks.
     * 
     * <p>Fallback hierarchy:
     * <ol>
     *   <li>Applies all textures from the map directly</li>
     *   <li>Maps "end" (cube_column) to "top" and "bottom" if missing</li>
     *   <li>Uses "all" (simple cubes) as fallback for missing textures</li>
     *   <li>Uses "side" as additional fallback for "top"/"bottom"</li>
     *   <li>Ensures "particle" is always set for proper break animations</li>
     * </ol>
     * 
     * @param texObj The JSON object to add texture properties to (modified in place)
     * @param textures The source texture mappings from the base block
     */
    public static void applyTextures(JsonObject texObj, Map<String, String> textures) {

        for (Map.Entry<String, String> entry : textures.entrySet()) {
            texObj.addProperty(entry.getKey(), entry.getValue());
        }

        // Handle cube_column models and other non-standard patterns
        // Map 'end' to top/bottom if those aren't already set
        String end = textures.get("end");
        if (end != null) {
            if (!texObj.has("top")) texObj.addProperty("top", end);
            if (!texObj.has("bottom")) texObj.addProperty("bottom", end);
        }

        // Ensure standard textures are present using 'all' as fallback
        String all = textures.get("all");
        if (all != null) {
            if (!texObj.has("top")) texObj.addProperty("top", all);
            if (!texObj.has("bottom")) texObj.addProperty("bottom", all);
            if (!texObj.has("side")) texObj.addProperty("side", all);
        }

        // Additional fallback: use 'side' for missing top/bottom if available
        String side = textures.get("side");
        if (side != null) {
            if (!texObj.has("top")) texObj.addProperty("top", side);
            if (!texObj.has("bottom")) texObj.addProperty("bottom", side);
        }

        // Ensure particle is set
        if (!texObj.has("particle")) {
            if (texObj.has("side")) {
                texObj.addProperty("particle", texObj.get("side").getAsString());
            } else if (texObj.has("top")) {
                texObj.addProperty("particle", texObj.get("top").getAsString());
            } else if (texObj.has("all")) {
                texObj.addProperty("particle", texObj.get("all").getAsString());
            }
        }
    }

    /**
     * Internal helper to create a cuboid element for a model with appropriate UVs and cullfaces.
     */
    private static JsonObject createSegmentElement(double x1, double y1, double z1, double x2, double y2, double z2,
                                                  Map<String, String> cullfaces) {
        JsonObject element = new JsonObject();
        setFromTo(element, x1, y1, z1, x2, y2, z2);
        
        JsonObject faces = new JsonObject();
        element.add("faces", faces);
        
        // standard UV formulas:
        // North: [16-x2, 16-y2, 16-x1, 16-y1]
        // South: [x1, 16-y2, x2, 16-y1]
        // Up: [x1, z1, x2, z2]
        // Down: [x1, 16-z2, x2, 16-z1]
        // East: [16-z2, 16-y2, 16-z1, 16-y1]
        // West: [z1, 16-y2, z2, 16-y1]
        
        addFace(faces, "north", "#side", cullfaces.get("north"), new double[]{16 - x2, 16 - y2, 16 - x1, 16 - y1});
        addFace(faces, "south", "#side", cullfaces.get("south"), new double[]{x1, 16 - y2, x2, 16 - y1});
        addFace(faces, "up", "#top", cullfaces.get("up"), new double[]{x1, z1, x2, z2});
        addFace(faces, "down", "#bottom", cullfaces.get("down"), new double[]{x1, 16 - z2, x2, 16 - z1});
        addFace(faces, "east", "#side", cullfaces.get("east"), new double[]{16 - z2, 16 - y2, 16 - z1, 16 - y1});
        addFace(faces, "west", "#side", cullfaces.get("west"), new double[]{z1, 16 - y2, z2, 16 - y1});
        
        return element;
    }

    /**
     * Generates a step model JSON for a specific combination of quadrants.
     */
    public static String generateStepModelForSegments(boolean downFront, boolean downBack, boolean upFront, boolean upBack, Map<String, String> textures) {
        return generateSegmentedModel("models/block/step.json", textures, elements -> {
            if (downFront) elements.add(createSegmentElement(8, 0, 0, 16, 8, 16, Map.of("north", "north", "south", "south", "east", "east")));
            if (downBack) elements.add(createSegmentElement(0, 0, 0, 8, 8, 16, Map.of("north", "north", "south", "south", "west", "west")));
            if (upFront) elements.add(createSegmentElement(8, 8, 0, 16, 16, 16, Map.of("north", "north", "south", "south", "up", "up", "east", "east")));
            if (upBack) elements.add(createSegmentElement(0, 8, 0, 8, 16, 16, Map.of("north", "north", "south", "south", "up", "up", "west", "west")));
        });
    }

    /**
     * Generates a vertical step model JSON for a specific combination of quadrants.
     */
    public static String generateVerticalStepModelForSegments(boolean nw, boolean ne, boolean sw, boolean se, Map<String, String> textures) {
        return generateSegmentedModel("models/block/vertical_step.json", textures, elements -> {
            if (nw) elements.add(createSegmentElement(0, 0, 0, 8, 16, 8, Map.of("north", "north", "west", "west", "up", "up", "down", "down")));
            if (ne) elements.add(createSegmentElement(8, 0, 0, 16, 16, 8, Map.of("north", "north", "east", "east", "up", "up", "down", "down")));
            if (sw) elements.add(createSegmentElement(0, 0, 8, 8, 16, 16, Map.of("south", "south", "west", "west", "up", "up", "down", "down")));
            if (se) elements.add(createSegmentElement(8, 0, 8, 16, 16, 16, Map.of("south", "south", "east", "east", "up", "up", "down", "down")));
        });
    }

    private static String generateSegmentedModel(String templatePath, Map<String, String> textures, java.util.function.Consumer<com.google.gson.JsonArray> elementBuilder) {
        JsonObject template = loadTemplateJson(templatePath);
        if (template == null) return null;

        if (template.has("textures")) {
            applyTextures(template.getAsJsonObject("textures"), textures);
        }

        com.google.gson.JsonArray elements = new com.google.gson.JsonArray();
        template.add("elements", elements);
        elementBuilder.accept(elements);

        return template.toString();
    }

    private static void addFace(JsonObject faces, String side, String texture, String cullface, double[] uv) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", texture);
        if (cullface != null) face.addProperty("cullface", cullface);
        
        com.google.gson.JsonArray uvArray = new com.google.gson.JsonArray();
        for (double v : uv) uvArray.add(v);
        face.add("uv", uvArray);
        
        faces.add(side, face);
    }

    private static void setFromTo(JsonObject element, double x1, double y1, double z1, double x2, double y2, double z2) {
        com.google.gson.JsonArray from = new com.google.gson.JsonArray();
        from.add(x1); from.add(y1); from.add(z1);
        element.add("from", from);
        
        com.google.gson.JsonArray to = new com.google.gson.JsonArray();
        to.add(x2); to.add(y2); to.add(z2);
        element.add("to", to);
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
                Reshaped.LOGGER.error("Failed to load template: {}", id, e);
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
                Reshaped.LOGGER.error("Failed to parse template JSON: {}", path, e);
            }
        }
        return null;
    }

    public static String generateBlockStateJson(String templateName, Map<String, String> placeholders) {
        String template = loadTemplate("blockstates/" + templateName + ".json");
        if (template == null) return null;

        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    public static Map<String, String> getModelTextures(Block block) {
        // Check cache first
        if (TEXTURE_CACHE.containsKey(block)) {
            return new HashMap<>(TEXTURE_CACHE.get(block));
        }

        Map<String, String> textures = new HashMap<>();
        Identifier blockId = Registries.BLOCK.getId(block);

        // 1. Try to load the block model directly first
        Identifier modelId = new Identifier(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
        if (loadTexturesFromModel(modelId, textures)) {
            // Perform dynamic analysis
            analyzeFaceTextures(modelId, textures);
            TEXTURE_CACHE.put(block, new HashMap<>(textures));
            return textures;
        }

        // 2. If valid model not found, try to check block state for model redirection
        // (Common for waxed blocks or aliased blocks)
        Identifier blockStateId = new Identifier(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        Optional<Resource> blockStateResource = MinecraftClient.getInstance().getResourceManager().getResource(blockStateId);

        if (blockStateResource.isPresent()) {
            try (InputStreamReader reader = new InputStreamReader(blockStateResource.get().getInputStream())) {
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
                    analyzeFaceTextures(redirectedModelId, textures);
                }

            } catch (Exception e) {
                Reshaped.LOGGER.error("Failed to read block state for block: {}", blockId, e);
            }
        }

        TEXTURE_CACHE.put(block, new HashMap<>(textures));
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
                Reshaped.LOGGER.error("Failed to read model: {}", modelId, e);
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

    public static String extractMaskSuffix(String path) {
        if (path.matches(".*_\\d{4}$")) {
            return path.substring(path.length() - 4);
        }
        return null;
    }

    public static boolean[] parseMaskOrDefault(String mask, boolean first, boolean second, boolean third, boolean fourth) {
        if (mask == null || mask.length() != 4) {
            return new boolean[]{first, second, third, fourth};
        }
        return new boolean[]{
                mask.charAt(0) == '1',
                mask.charAt(1) == '1',
                mask.charAt(2) == '1',
                mask.charAt(3) == '1'
        };
    }

    public static String stripMaskSuffix(String path) {
        if (path.matches(".*_\\d{4}$")) {
            return path.substring(0, path.length() - 5);
        }
        return path;
    }

    public static Block resolveBlockForPath(String path, Block block) {
        String basePath = stripMaskSuffix(path);
        if (basePath.equals(path)) {
            return block;
        }
        Identifier blockId = new Identifier(Reshaped.MOD_ID, basePath);
        return Registries.BLOCK.get(blockId);
    }

    private static Block findBaseBlock(Block variant) {
        if (Reshaped.MATRIX == null) return null;
        for (Map.Entry<Block, List<Block>> entry : Reshaped.MATRIX.getMatrix().entrySet()) {
            if (entry.getValue().contains(variant)) return entry.getKey();
        }
        return null;
    }

    /**
     * Resolves the actual model ID for a block, handling block state redirection.
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

        // Check block state for model redirection
        Identifier blockStateId = new Identifier(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        Optional<Resource> bsResource = MinecraftClient.getInstance().getResourceManager().getResource(blockStateId);

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
