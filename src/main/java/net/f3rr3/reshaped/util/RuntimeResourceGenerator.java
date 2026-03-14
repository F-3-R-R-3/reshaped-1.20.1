package net.f3rr3.reshaped.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Step.StepBlock;
import net.f3rr3.reshaped.block.VericalStairs.VerticalStairsBlock;
import net.f3rr3.reshaped.block.VerticalSlab.VerticalSlabBlock;
import net.f3rr3.reshaped.block.VerticalStep.VerticalStepBlock;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeResourceGenerator {

    /**
     * Cache for analyzed texture mappings. Prevents repeated analysis of the same base block
     * during a single resource reload session. Cache is cleared when resources are reloaded.
     * <p>
     * Key: Base block
     * Value: Map of texture keys (e.g., "top", "bottom", "side") to texture resource paths
     */
    private static final Map<String, Map<String, String>> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> TEMPLATE_TEXT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> TEMPLATE_JSON_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> GENERATED_MODEL_JSON_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, List<ModelCandidate>> MODEL_CANDIDATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Optional<Identifier>> RESOLVED_MODEL_ID_CACHE = new ConcurrentHashMap<>();

    static {
        new GsonBuilder().setPrettyPrinting().create();
    }

    public static void clearCaches() {
        TEXTURE_CACHE.clear();
        TEMPLATE_TEXT_CACHE.clear();
        TEMPLATE_JSON_CACHE.clear();
        GENERATED_MODEL_JSON_CACHE.clear();
        MODEL_CANDIDATE_CACHE.clear();
        RESOLVED_MODEL_ID_CACHE.clear();
    }

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

    public static List<ModelVariant> parseVariants(JsonElement elem) {
        if (elem == null) return Collections.emptyList();
        List<ModelVariant> variants = new ArrayList<>();
        if (elem.isJsonArray()) {
            for (JsonElement entry : elem.getAsJsonArray()) {
                if (entry.isJsonObject() && entry.getAsJsonObject().has("model")) {
                    variants.add(parseVariant(entry));
                }
            }
            return variants;
        }
        if (elem.isJsonObject() && elem.getAsJsonObject().has("model")) {
            variants.add(parseVariant(elem));
        }
        return variants;
    }

    public static String generateModelJson(String cleanPath) {
        if (Reshaped.MATRIX == null) return null;
        String path = normalizePath(cleanPath);

        String cached = GENERATED_MODEL_JSON_CACHE.get(path);
        if (cached != null) {
            return cached;
        }

        String generated = generateModelJsonUncached(path);
        if (generated != null) {
            GENERATED_MODEL_JSON_CACHE.put(path, generated);
        }
        return generated;
    }

    private static String generateModelJsonUncached(String path) {

        // Handle Item Models
        if (path.startsWith("item/")) {
            String itemPath = path.substring(5);
            return "{\"parent\":\"reshaped:block/" + itemPath + "\"}";
        }

        // Handle Block Models
        String blockPath = path;
        if (blockPath.startsWith("block/")) blockPath = blockPath.substring(6);
        if (blockPath.startsWith("mixed_") && !blockPath.equals("mixed_placeholder")) {
            return "{\"parent\":\"reshaped:block/mixed_placeholder\"}";
        }
        String variantRegistryPath = blockPath;
        int randomVariantIndex = extractRandomVariantIndex(blockPath);
        if (randomVariantIndex >= 0) {
            blockPath = stripRandomVariantSuffix(blockPath);
        }

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
                .replace("_nortsouth", "")
                .replace("_eastwest", "")
                .replaceAll("_\\d+$", "");
        // Use regex for mask to avoid partial matches
        baseBlockPath = baseBlockPath.replaceAll("_\\d{4}$", "");

        String variantJson = VariantRegistry.generateModelJson(variantRegistryPath, Registries.BLOCK.get(new Identifier(Reshaped.MOD_ID, baseBlockPath)));
        if (variantJson != null) return variantJson;

        // Slab and Stair fallbacks (Efficient lookup)
        Identifier blockId = new Identifier(Reshaped.MOD_ID, stripMaskSuffix(blockPath)
                .replace("_top", "")
                .replace("_inner", "")
                .replace("_outer", "")
                .replace("_2", "")
                .replace("_3", ""));
        Block block = Registries.BLOCK.get(blockId);
        Block baseBlock = Reshaped.MATRIX != null ? Reshaped.MATRIX.getBaseBlock(block) : null;

        if (baseBlock != null) {
            Map<String, String> textures = getModelTextures(baseBlock, randomVariantIndex);

            if (block instanceof VerticalSlabBlock) {
                return generateModelFromTemplate("block/vertical_slab", textures);
            } else if (block instanceof VerticalStairsBlock) {
                if (isTransparentBlock(baseBlock)) {
                    return generateModelFromTemplate("block/transparent/verical_stairs", textures);
                }
                return generateModelFromTemplate("block/verical_stairs", textures);
            } else if (block instanceof VerticalStepBlock) {
                String mask = extractMaskSuffix(blockPath);
                if (mask != null) {
                    boolean[] segments = parseMaskOrDefault(mask, true, false, false, false);
                    return generateVerticalStepModelForSegments(segments[0], segments[1], segments[2], segments[3], textures);
                }
                // If the path contains the suffix, it's a generated full model request
                if (blockPath.endsWith("_vertical_step")) {
                    return generateVerticalStepModelForSegments(true, true, true, true, textures);
                }
                // Fallback for item model/base path: single segment (NW)
                return generateVerticalStepModelForSegments(true, false, false, false, textures);
            } else if (block instanceof StepBlock) {
                // Check for segment mask suffix (e.g., "_1010")
                String mask = extractMaskSuffix(blockPath);
                StepBlock.StepAxis axis = extractStepAxis(blockPath);
                if (mask != null && axis != null) {
                    boolean[] segments = parseMaskOrDefault(mask, true, false, false, false);
                    return generateStepModelForSegments(axis, segments[0], segments[1], segments[2], segments[3], textures);
                }
                // If it's a generated axis-specific model request but no mask, it's the full block
                if (axis != null) {
                    return generateStepModelForSegments(axis, true, true, true, true, textures);
                }
                // Fallback for base item/block (single step icon)
                return generateStepModelForSegments(StepBlock.StepAxis.EAST_WEST, true, false, false, false, textures);
            } else if (block instanceof SlabBlock) {
                if (blockPath.endsWith("_top")) {
                    return generateSimpleModel("minecraft:block/slab_top", textures);
                }
                return generateSimpleModel("minecraft:block/slab", textures);
            } else if (block instanceof StairsBlock) {
                boolean useTransparentModel = isTransparentBlock(baseBlock);
                if (blockPath.endsWith("_inner")) {
                    return useTransparentModel
                            ? generateModelFromTemplate("block/transparent/inner_stairs", textures)
                            : generateSimpleModel("minecraft:block/inner_stairs", textures);
                } else if (blockPath.endsWith("_outer")) {
                    return useTransparentModel
                            ? generateModelFromTemplate("block/transparent/outer_stairs", textures)
                            : generateSimpleModel("minecraft:block/outer_stairs", textures);
                }
                return useTransparentModel
                        ? generateModelFromTemplate("block/transparent/stairs", textures)
                        : generateSimpleModel("minecraft:block/stairs", textures);
            }
        }
        return null;
    }

    public static String generateSimpleModel(String parent, Map<String, String> textures) {
        Identifier parentId = resolveModelIdentifier(parent);
        JsonObject root = loadModelHierarchy(parentId);
        if (root == null) {
            root = new JsonObject();
            root.addProperty("parent", parent);
        } else {
            root.remove("parent"); // We've flattened it
        }

        JsonObject texturesObj = root.has("textures") ? root.getAsJsonObject("textures") : new JsonObject();
        applyTextures(texturesObj, textures);
        root.add("textures", texturesObj);

        injectOverlays(root, textures);
        applyTints(root, textures);

        return root.toString();
    }

    public static String generateModelFromTemplate(String templatePath, Map<String, String> textures) {
        JsonObject template = loadTemplateJson("models/" + templatePath + ".json");
        if (template == null) return null;

        if (template.has("textures")) {
            applyTextures(template.getAsJsonObject("textures"), textures);
        }

        injectOverlays(template, textures);
        applyTints(template, textures);

        return template.toString();
    }

    private static void injectOverlays(JsonObject model, Map<String, String> textures) {
        if (!textures.containsKey("side_overlay") && !textures.containsKey("top_overlay") && !textures.containsKey("bottom_overlay")) {
            return;
        }

        if (!model.has("elements")) return;
        com.google.gson.JsonArray elements = model.getAsJsonArray("elements");
        com.google.gson.JsonArray overlayElements = new com.google.gson.JsonArray();

        for (com.google.gson.JsonElement elementElem : elements) {
            JsonObject element = elementElem.getAsJsonObject();
            if (!element.has("faces")) continue;

            JsonObject faces = element.getAsJsonObject("faces");
            JsonObject overlayFaces = new JsonObject();
            boolean hasOverlay = false;

            for (String faceName : faces.keySet()) {
                JsonObject face = faces.getAsJsonObject(faceName);
                if (!face.has("texture")) continue;
                String texRef = face.get("texture").getAsString();

                String overlayKey = switch (texRef) {
                    case "#side", "#wall" -> "#side_overlay";
                    case "#top", "#end" -> "#top_overlay";
                    case "#bottom" -> "#bottom_overlay";
                    default -> null;
                };

                if (overlayKey != null && textures.containsKey(overlayKey.substring(1))) {
                    JsonObject overlayFace = face.deepCopy();
                    overlayFace.addProperty("texture", overlayKey);
                    overlayFaces.add(faceName, overlayFace);
                    hasOverlay = true;
                }
            }

            if (hasOverlay) {
                JsonObject overlayElement = element.deepCopy();
                overlayElement.add("faces", overlayFaces);
                overlayElements.add(overlayElement);
            }
        }

        elements.addAll(overlayElements);
    }

    public static Identifier resolveModelIdentifier(String path) {
        if (path.contains(":")) {
            String[] parts = path.split(":");
            return new Identifier(parts[0], "models/" + parts[1] + ".json");
        }
        return new Identifier("minecraft", "models/" + path + ".json");
    }

    public static void applyTints(JsonObject model, Map<String, String> textures) {
        if (!model.has("elements")) return;
        com.google.gson.JsonArray elements = model.getAsJsonArray("elements");
        for (com.google.gson.JsonElement elementElem : elements) {
            JsonObject element = elementElem.getAsJsonObject();
            if (!element.has("faces")) continue;
            JsonObject faces = element.getAsJsonObject("faces");
            for (String faceName : faces.keySet()) {
                JsonObject face = faces.getAsJsonObject(faceName);
                if (!face.has("texture")) continue;
                String texRef = face.get("texture").getAsString();
                if (texRef.startsWith("#")) {
                    String key = texRef.substring(1);
                    String tintKey = switch (key) {
                        case "top", "end" -> "_tint_top";
                        case "bottom" -> "_tint_bottom";
                        case "side", "wall" -> "_tint_side";
                        case "top_overlay" -> "_tint_top_overlay";
                        case "bottom_overlay" -> "_tint_bottom_overlay";
                        case "side_overlay", "overlay" -> "_tint_side_overlay";
                        default -> null;
                    };

                    if (tintKey != null && textures.containsKey(tintKey)) {
                        try {
                            face.addProperty("tintindex", Integer.parseInt(textures.get(tintKey)));
                        } catch (NumberFormatException ignored) {
                        }
                    } else {
                        face.remove("tintindex");
                    }
                }
            }
        }
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
     * @param modelId  The model to analyze
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

            // Extract mappings from all elements to detect overlays
            Map<String, List<String>> faceToTextures = new HashMap<>();
            Map<String, List<Integer>> faceToTints = new HashMap<>();

            for (com.google.gson.JsonElement elementElem : elements) {
                JsonObject element = elementElem.getAsJsonObject();
                if (!element.has("faces")) continue;
                JsonObject faceObjs = element.getAsJsonObject("faces");

                for (String face : new String[]{"up", "down", "north", "south", "east", "west"}) {
                    if (faceObjs.has(face)) {
                        JsonObject faceObj = faceObjs.getAsJsonObject(face);
                        if (faceObj.has("texture")) {
                            faceToTextures.computeIfAbsent(face, k -> new ArrayList<>()).add(faceObj.get("texture").getAsString());
                        }
                        if (faceObj.has("tintindex")) {
                            faceToTints.computeIfAbsent(face, k -> new ArrayList<>()).add(faceObj.get("tintindex").getAsInt());
                        } else {
                            faceToTints.computeIfAbsent(face, k -> new ArrayList<>()).add(null);
                        }
                    }
                }
            }

            // Resolve and map to standard keys
            for (String face : faceToTextures.keySet()) {
                List<String> texRefs = faceToTextures.get(face);
                List<Integer> tints = faceToTints.get(face);

                for (int i = 0; i < texRefs.size(); i++) {
                    String textureRef = texRefs.get(i);
                    Integer tint = tints.size() > i ? tints.get(i) : null;

                    if (textureRef.startsWith("#")) {
                        String key = textureRef.substring(1);
                        if (textures.containsKey(key)) {
                            String texturePath = textures.get(key);
                            String suffix = (i == 0) ? "" : "_overlay";

                            if (face.equals("up")) {
                                textures.putIfAbsent("top" + suffix, texturePath);
                                if (tint != null) textures.put("_tint_top" + suffix, tint.toString());
                            } else if (face.equals("down")) {
                                textures.putIfAbsent("bottom" + suffix, texturePath);
                                if (tint != null) textures.put("_tint_bottom" + suffix, tint.toString());
                            } else {
                                textures.putIfAbsent("side" + suffix, texturePath);
                                if (tint != null) textures.put("_tint_side" + suffix, tint.toString());
                            }
                        }
                    }
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
            Identifier parentId = resolveModelIdentifier(parentPath);

            JsonObject parentModel = loadModelHierarchy(parentId);
            if (parentModel != null) {
                // Merge parent into current (parent properties first)
                for (Map.Entry<String, com.google.gson.JsonElement> entry : parentModel.entrySet()) {
                    merged.add(entry.getKey(), entry.getValue());
                }
            }
        }

        // Override/add current model properties
        for (Map.Entry<String, com.google.gson.JsonElement> entry : current.entrySet()) {
            if (entry.getKey().equals("textures") && merged.has("textures")) {
                JsonObject mergedTex = merged.getAsJsonObject("textures");
                JsonObject currentTex = entry.getValue().getAsJsonObject();
                for (Map.Entry<String, com.google.gson.JsonElement> texEntry : currentTex.entrySet()) {
                    mergedTex.add(texEntry.getKey(), texEntry.getValue());
                }
            } else {
                merged.add(entry.getKey(), entry.getValue());
            }
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
     * @param texObj   The JSON object to add texture properties to (modified in place)
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

    private static JsonObject createSegmentElement(double x1, double y1, double z1, double x2, double y2, double z2,
                                                   Map<String, String> cullfaces, Map<String, String> textures) {
        JsonObject element = new JsonObject();
        setFromTo(element, x1, y1, z1, x2, y2, z2);

        JsonObject faces = new JsonObject();
        element.add("faces", faces);

        addFace(faces, "north", "#side", cullfaces.get("north"), new double[]{16 - x2, 16 - y2, 16 - x1, 16 - y1}, textures.get("_tint_side"));
        addFace(faces, "south", "#side", cullfaces.get("south"), new double[]{x1, 16 - y2, x2, 16 - y1}, textures.get("_tint_side"));
        addFace(faces, "up", "#top", cullfaces.get("up"), new double[]{x1, z1, x2, z2}, textures.get("_tint_top"));
        addFace(faces, "down", "#bottom", cullfaces.get("down"), new double[]{x1, 16 - z2, x2, 16 - z1}, textures.get("_tint_bottom"));
        addFace(faces, "east", "#side", cullfaces.get("east"), new double[]{16 - z2, 16 - y2, 16 - z1, 16 - y1}, textures.get("_tint_side"));
        addFace(faces, "west", "#side", cullfaces.get("west"), new double[]{z1, 16 - y2, z2, 16 - y1}, textures.get("_tint_side"));

        return element;
    }

    private static JsonObject createOverlaySegmentElement(double x1, double y1, double z1, double x2, double y2, double z2,
                                                          Map<String, String> cullfaces, Map<String, String> textures) {
        JsonObject element = new JsonObject();
        setFromTo(element, x1, y1, z1, x2, y2, z2);

        JsonObject faces = new JsonObject();
        element.add("faces", faces);

        if (textures.containsKey("side_overlay")) {
            addFace(faces, "north", "#side_overlay", cullfaces.get("north"), new double[]{16 - x2, 16 - y2, 16 - x1, 16 - y1}, textures.get("_tint_side_overlay"));
            addFace(faces, "south", "#side_overlay", cullfaces.get("south"), new double[]{x1, 16 - y2, x2, 16 - y1}, textures.get("_tint_side_overlay"));
            addFace(faces, "east", "#side_overlay", cullfaces.get("east"), new double[]{16 - z2, 16 - y2, 16 - z1, 16 - y1}, textures.get("_tint_side_overlay"));
            addFace(faces, "west", "#side_overlay", cullfaces.get("west"), new double[]{z1, 16 - y2, z2, 16 - y1}, textures.get("_tint_side_overlay"));
        }
        if (textures.containsKey("top_overlay")) {
            addFace(faces, "up", "#top_overlay", cullfaces.get("up"), new double[]{x1, z1, x2, z2}, textures.get("_tint_top_overlay"));
        }
        if (textures.containsKey("bottom_overlay")) {
            addFace(faces, "down", "#bottom_overlay", cullfaces.get("down"), new double[]{x1, 16 - z2, x2, 16 - z1}, textures.get("_tint_bottom_overlay"));
        }

        return element;
    }

    /**
     * Generates a step model JSON for a specific combination of quadrants.
     */
    public static String generateStepModelForSegments(StepBlock.StepAxis axis, boolean downFront, boolean downBack, boolean upFront, boolean upBack, Map<String, String> textures) {
        return generateSegmentedModel("models/block/step.json", textures, elements -> {
            double xMinF = 0, xMaxF = 8, xMinB = 8, xMaxB = 16, zMinF = 0, zMaxF = 16, zMinB = 0, zMaxB = 16;
            Map<String, String> cullF = Map.of("west", "west", "down", "down", "up", "up", "north", "north", "south", "south");
            Map<String, String> cullB = Map.of("east", "east", "down", "down", "up", "up", "north", "north", "south", "south");

            if (axis == StepBlock.StepAxis.NORTH_SOUTH) {
                xMinF = 0;
                xMaxF = 16;
                xMinB = 0;
                zMinF = 0;
                zMaxF = 8;
                zMinB = 8;
                cullF = Map.of("north", "north", "down", "down", "up", "up", "west", "west", "east", "east");
                cullB = Map.of("south", "south", "down", "down", "up", "up", "west", "west", "east", "east");
            }

            if (downFront)
                elements.add(createSegmentElement(xMinF, 0, zMinF, xMaxF, 8, zMaxF, cullF, textures));
            if (downBack)
                elements.add(createSegmentElement(xMinB, 0, zMinB, xMaxB, 8, zMaxB, cullB, textures));
            if (upFront)
                elements.add(createSegmentElement(xMinF, 8, zMinF, xMaxF, 16, zMaxF, cullF, textures));
            if (upBack)
                elements.add(createSegmentElement(xMinB, 8, zMinB, xMaxB, 16, zMaxB, cullB, textures));

            // Overlay pass
            if (textures.containsKey("side_overlay") || textures.containsKey("top_overlay") || textures.containsKey("bottom_overlay")) {
                if (downFront)
                    elements.add(createOverlaySegmentElement(xMinF, 0, zMinF, xMaxF, 8, zMaxF, cullF, textures));
                if (downBack)
                    elements.add(createOverlaySegmentElement(xMinB, 0, zMinB, xMaxB, 8, zMaxB, cullB, textures));
                if (upFront)
                    elements.add(createOverlaySegmentElement(xMinF, 8, zMinF, xMaxF, 16, zMaxF, cullF, textures));
                if (upBack)
                    elements.add(createOverlaySegmentElement(xMinB, 8, zMinB, xMaxB, 16, zMaxB, cullB, textures));
            }
        });
    }

    /**
     * Generates a vertical step model JSON for a specific combination of quadrants.
     */
    public static String generateVerticalStepModelForSegments(boolean nw, boolean ne, boolean sw, boolean se, Map<String, String> textures) {
        return generateSegmentedModel("models/block/vertical_step.json", textures, elements -> {
            if (nw)
                elements.add(createSegmentElement(0, 0, 0, 8, 16, 8, Map.of("north", "north", "west", "west", "up", "up", "down", "down"), textures));
            if (ne)
                elements.add(createSegmentElement(8, 0, 0, 16, 16, 8, Map.of("north", "north", "east", "east", "up", "up", "down", "down"), textures));
            if (sw)
                elements.add(createSegmentElement(0, 0, 8, 8, 16, 16, Map.of("south", "south", "west", "west", "up", "up", "down", "down"), textures));
            if (se)
                elements.add(createSegmentElement(8, 0, 8, 16, 16, 16, Map.of("south", "south", "east", "east", "up", "up", "down", "down"), textures));

            // Overlay pass
            if (textures.containsKey("side_overlay") || textures.containsKey("top_overlay") || textures.containsKey("bottom_overlay")) {
                if (nw)
                    elements.add(createOverlaySegmentElement(0, 0, 0, 8, 16, 8, Map.of("north", "north", "west", "west", "up", "up", "down", "down"), textures));
                if (ne)
                    elements.add(createOverlaySegmentElement(8, 0, 0, 16, 16, 8, Map.of("north", "north", "east", "east", "up", "up", "down", "down"), textures));
                if (sw)
                    elements.add(createOverlaySegmentElement(0, 0, 8, 8, 16, 16, Map.of("south", "south", "west", "west", "up", "up", "down", "down"), textures));
                if (se)
                    elements.add(createOverlaySegmentElement(8, 0, 8, 16, 16, 16, Map.of("south", "south", "east", "east", "up", "up", "down", "down"), textures));
            }
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

    private static void addFace(JsonObject faces, String side, String texture, String cullface, double[] uv, String tintIndex) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", texture);
        if (tintIndex != null) {
            try {
                face.addProperty("tintindex", Integer.parseInt(tintIndex));
            } catch (NumberFormatException ignored) {
            }
        }
        if (cullface != null) face.addProperty("cullface", cullface);

        com.google.gson.JsonArray uvArray = new com.google.gson.JsonArray();
        for (double v : uv) uvArray.add(v);
        face.add("uv", uvArray);

        faces.add(side, face);
    }

    private static void setFromTo(JsonObject element, double x1, double y1, double z1, double x2, double y2, double z2) {
        com.google.gson.JsonArray from = new com.google.gson.JsonArray();
        from.add(x1);
        from.add(y1);
        from.add(z1);
        element.add("from", from);

        com.google.gson.JsonArray to = new com.google.gson.JsonArray();
        to.add(x2);
        to.add(y2);
        to.add(z2);
        element.add("to", to);
    }

    public static String loadTemplate(String path) {
        String cached = TEMPLATE_TEXT_CACHE.get(path);
        if (cached != null) {
            return cached;
        }

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
                String template = builder.toString();
                TEMPLATE_TEXT_CACHE.put(path, template);
                return template;
            } catch (Exception e) {
                Reshaped.LOGGER.error("Failed to load template: {}", id, e);
            }
        }
        return null;
    }

    public static JsonObject loadTemplateJson(String path) {
        JsonObject cached = TEMPLATE_JSON_CACHE.get(path);
        if (cached != null) {
            return cached.deepCopy();
        }

        String template = loadTemplate(path);
        if (template != null) {
            try {
                JsonObject parsed = JsonParser.parseString(template).getAsJsonObject();
                TEMPLATE_JSON_CACHE.put(path, parsed);
                return parsed.deepCopy();
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

    public static Map<String, String> getModelTextures(Block block, int preferredCandidateIndex) {
        Identifier blockId = Registries.BLOCK.getId(block);
        String cacheKey = blockId + "#" + preferredCandidateIndex;

        // Check cache first
        if (TEXTURE_CACHE.containsKey(cacheKey)) {
            return new HashMap<>(TEXTURE_CACHE.get(cacheKey));
        }

        Map<String, String> textures = new HashMap<>();

        List<ModelCandidate> candidates = resolveBlockModelCandidates(block);
        if (!candidates.isEmpty()) {
            int idx = preferredCandidateIndex >= 0 && preferredCandidateIndex < candidates.size() ? preferredCandidateIndex : 0;
            ModelCandidate candidate = candidates.get(idx);
            Identifier candidateModelJsonId = new Identifier(candidate.modelId().getNamespace(), "models/" + candidate.modelId().getPath() + ".json");
            if (loadTexturesFromModel(candidateModelJsonId, textures)) {
                analyzeFaceTextures(candidateModelJsonId, textures);
                TEXTURE_CACHE.put(cacheKey, new HashMap<>(textures));
                return textures;
            }
        }

        // 1. Try to load the block model directly first
        Identifier modelId = new Identifier(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
        if (loadTexturesFromModel(modelId, textures)) {
            // Perform dynamic analysis
            analyzeFaceTextures(modelId, textures);
            TEXTURE_CACHE.put(cacheKey, new HashMap<>(textures));
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

        TEXTURE_CACHE.put(cacheKey, new HashMap<>(textures));
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
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("_(\\d{4})$").matcher(path);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static StepBlock.StepAxis extractStepAxis(String path) {
        if (path.contains("_nortsouth")) return StepBlock.StepAxis.NORTH_SOUTH;
        if (path.contains("_eastwest")) return StepBlock.StepAxis.EAST_WEST;
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
        String stripped = path.replaceAll("_\\d{4}$", "");
        if (stripped.equals(path)) {
            stripped = path.replaceAll("_\\d+$", "");
        }
        return stripped
                .replace("_nortsouth", "")
                .replace("_eastwest", "");
    }

    public static boolean isTransparentBlock(Block block) {
        return !block.getDefaultState().isOpaque();
    }

    public static Block resolveBlockForPath(String path, Block block) {
        String basePath = stripMaskSuffix(path);
        if (basePath.equals(path)) {
            return block;
        }
        Identifier blockId = new Identifier(Reshaped.MOD_ID, basePath);
        return Registries.BLOCK.get(blockId);
    }

    /**
     * Resolves the actual model ID for a block, handling block state redirection.
     * Returns null if direct model exists or if resolution fails.
     */
    public static Identifier resolveBlockModelId(Block block) {
        List<ModelCandidate> candidates = resolveBlockModelCandidates(block);
        if (!candidates.isEmpty()) {
            return candidates.get(0).modelId();
        }

        Identifier blockId = Registries.BLOCK.getId(block);
        Optional<Identifier> cached = RESOLVED_MODEL_ID_CACHE.get(blockId);
        if (cached != null) {
            return cached.orElse(null);
        }

        Identifier resolved = resolveBlockModelIdUncached(blockId);
        RESOLVED_MODEL_ID_CACHE.put(blockId, Optional.ofNullable(resolved));
        return resolved;
    }

    private static Identifier resolveBlockModelIdUncached(Identifier blockId) {

        Identifier directModelId = new Identifier(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
        Optional<Resource> directResource = MinecraftClient.getInstance().getResourceManager().getResource(directModelId);
        if (directResource.isPresent()) {
            return new Identifier(blockId.getNamespace(), "block/" + blockId.getPath());
        }

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

    public static List<ModelCandidate> resolveBlockModelCandidates(Block block) {
        Identifier blockId = Registries.BLOCK.getId(block);
        List<ModelCandidate> cached = MODEL_CANDIDATE_CACHE.get(blockId);
        if (cached != null) {
            return cached;
        }

        List<ModelCandidate> resolved = resolveBlockModelCandidatesUncached(blockId);
        List<ModelCandidate> immutable = resolved.isEmpty() ? Collections.emptyList() : List.copyOf(resolved);
        MODEL_CANDIDATE_CACHE.put(blockId, immutable);
        return immutable;
    }

    private static List<ModelCandidate> resolveBlockModelCandidatesUncached(Identifier blockId) {
        Identifier directModelId = new Identifier(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json");
        Optional<Resource> directResource = MinecraftClient.getInstance().getResourceManager().getResource(directModelId);
        if (directResource.isPresent()) {
            return List.of(new ModelCandidate(new Identifier(blockId.getNamespace(), "block/" + blockId.getPath()), 0, 0, false, 1));
        }

        Identifier blockStateId = new Identifier(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        Optional<Resource> bsResource = MinecraftClient.getInstance().getResourceManager().getResource(blockStateId);
        if (bsResource.isPresent()) {
            try (InputStreamReader reader = new InputStreamReader(bsResource.get().getInputStream())) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                if (json.has("variants")) {
                    JsonObject variants = json.getAsJsonObject("variants");
                    if (variants.has("")) {
                        return parseModelCandidatesElement(variants.get(""));
                    }
                    if (!variants.keySet().isEmpty()) {
                        return parseModelCandidatesElement(variants.get(variants.keySet().iterator().next()));
                    }
                }

                if (json.has("multipart")) {
                    JsonElement multipartElem = json.get("multipart");
                    if (multipartElem.isJsonArray()) {
                        for (JsonElement part : multipartElem.getAsJsonArray()) {
                            if (part.isJsonObject() && part.getAsJsonObject().has("apply")) {
                                List<ModelCandidate> parsed = parseModelCandidatesElement(part.getAsJsonObject().get("apply"));
                                if (!parsed.isEmpty()) {
                                    return parsed;
                                }
                            }
                        }
                    } else if (multipartElem.isJsonObject()) {
                        JsonObject partObj = multipartElem.getAsJsonObject();
                        if (partObj.has("apply")) {
                            return parseModelCandidatesElement(partObj.get("apply"));
                        }
                    }
                }
            } catch (Exception e) {
                Reshaped.LOGGER.error("Failed to resolve model candidates for block: {}", blockId, e);
            }
        }

        return Collections.emptyList();
    }

    private static List<ModelCandidate> parseModelCandidatesElement(JsonElement elem) {
        if (elem == null) return Collections.emptyList();

        List<ModelCandidate> result = new ArrayList<>();
        if (elem.isJsonArray()) {
            for (JsonElement variantElem : elem.getAsJsonArray()) {
                ModelCandidate candidate = parseModelCandidateObject(variantElem);
                if (candidate != null) result.add(candidate);
            }
            return result;
        }

        ModelCandidate candidate = parseModelCandidateObject(elem);
        if (candidate != null) {
            result.add(candidate);
        }
        return result;
    }

    private static ModelCandidate parseModelCandidateObject(JsonElement elem) {
        if (elem == null || !elem.isJsonObject()) return null;
        JsonObject obj = elem.getAsJsonObject();
        if (!obj.has("model")) return null;

        String model = obj.get("model").getAsString();
        int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
        int y = obj.has("y") ? obj.get("y").getAsInt() : 0;
        boolean uvlock = obj.has("uvlock") && obj.get("uvlock").getAsBoolean();
        int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
        return new ModelCandidate(new Identifier(model), x, y, uvlock, weight);
    }

    public static int extractRandomVariantIndex(String path) {
        int marker = path.lastIndexOf("_rnd");
        if (marker < 0) return -1;
        String suffix = path.substring(marker + 4);
        if (suffix.matches("\\d+")) {
            return Integer.parseInt(suffix);
        }
        return -1;
    }

    public static String stripRandomVariantSuffix(String path) {
        int marker = path.lastIndexOf("_rnd");
        if (marker < 0) return path;
        String suffix = path.substring(marker + 4);
        if (suffix.matches("\\d+")) {
            return path.substring(0, marker);
        }
        return path;
    }

    public record ModelCandidate(Identifier modelId, int x, int y, boolean uvlock, int weight) {
    }
}
