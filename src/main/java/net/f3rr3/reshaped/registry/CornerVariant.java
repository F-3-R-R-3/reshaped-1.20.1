package net.f3rr3.reshaped.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Corner.CornerBlock;
import net.f3rr3.reshaped.client.RuntimeResourceGenerator;
import net.f3rr3.reshaped.matrix.BlockMatrix;
import net.f3rr3.reshaped.matrix.MatrixRebuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public class CornerVariant implements BlockVariantType {
    @Override
    public String getName() {
        return "corner";
    }

    @Override
    public void register(Block baseBlock, BlockMatrix matrix) {
        Identifier baseId = Registries.BLOCK.getId(baseBlock);
        String baseName = baseId.getPath().replace("_planks", "").replace("_block", "");
        String path = baseName + "_corner";
        Identifier id = new Identifier(Reshaped.MOD_ID, path);

        if (Registries.BLOCK.get(id) != Blocks.AIR) {
            Block existing = Registries.BLOCK.get(id);
            if (existing instanceof CornerBlock corner) {
                List<Block> variants = matrix.getMutableMatrix().get(baseBlock);
                if (variants != null && !variants.contains(corner)) {
                    variants.add(corner);
                }
            }
            return;
        }

        if (MatrixRebuilder.isRegistryFrozen()) {
            return;
        }

        CornerBlock corner = new CornerBlock(VariantSettingsFactory.create(baseBlock));

        Registry.register(Registries.BLOCK, id, corner);
        Registry.register(Registries.ITEM, id, new BlockItem(corner, new Item.Settings()));

        matrix.addVariant(baseBlock, corner, true);
        matrix.setReason(corner, "Dynamically registered Corner (1/8 block) for " + baseBlock.getName().getString());
    }

    @Override
    public String generateModelJson(String path, Block block) {
        String cleanPath = RuntimeResourceGenerator.stripRandomVariantSuffix(path);
        int randomIndex = RuntimeResourceGenerator.extractRandomVariantIndex(path);
        if (cleanPath.contains("_corner")) {
            Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
            if (baseBlock != null) {
                Map<String, String> textures = RuntimeResourceGenerator.getModelTextures(baseBlock, randomIndex);

                // Extract bits from path (e.g., _11010010)
                int bitIndex = cleanPath.lastIndexOf('_');
                if (bitIndex != -1 && cleanPath.length() - bitIndex == 9) {
                    String bits = cleanPath.substring(bitIndex + 1);

                    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
                    root.addProperty("parent", "minecraft:block/block");

                    com.google.gson.JsonObject texturesObj = new com.google.gson.JsonObject();
                    RuntimeResourceGenerator.applyTextures(texturesObj, textures);
                    root.add("textures", texturesObj);

                    com.google.gson.JsonArray elements = new com.google.gson.JsonArray();
                    com.google.gson.JsonArray overlayElements = new com.google.gson.JsonArray();
                    String[] segmentTemplates = {
                            "corner_down_nw", "corner_down_ne", "corner_down_sw", "corner_down_se",
                            "corner_up_nw", "corner_up_ne", "corner_up_sw", "corner_up_se"
                    };

                    boolean hasOverlay = textures.containsKey("side_overlay") || textures.containsKey("top_overlay") || textures.containsKey("bottom_overlay");

                    for (int i = 0; i < 8; i++) {
                        if (bits.charAt(i) == '1') {
                            com.google.gson.JsonObject segment = RuntimeResourceGenerator.loadTemplateJson("models/block/" + segmentTemplates[i] + ".json");
                            if (segment != null && segment.has("elements")) {
                                elements.addAll(segment.getAsJsonArray("elements"));
                                if (hasOverlay) {
                                    for (JsonElement elElem : segment.getAsJsonArray("elements")) {
                                        JsonObject el = elElem.getAsJsonObject();
                                        if (el.has("faces")) {
                                            JsonObject faces = el.getAsJsonObject("faces");
                                            JsonObject overlayFaces = new JsonObject();
                                            boolean elementHasOverlay = false;
                                            for (String faceName : faces.keySet()) {
                                                JsonObject face = faces.getAsJsonObject(faceName);
                                                String texRef = face.get("texture").getAsString();
                                                String overlayKey = switch (texRef) {
                                                    case "#side", "#wall" -> "#side_overlay";
                                                    case "#top", "#end" -> "#top_overlay";
                                                    case "#bottom" -> "#bottom_overlay";
                                                    default -> null;
                                                };

                                                if (overlayKey != null && (textures.containsKey(overlayKey.substring(1)) || (overlayKey.equals("#side_overlay") && textures.containsKey("overlay")))) {
                                                    JsonObject overlayFace = face.deepCopy();
                                                    overlayFace.addProperty("texture", overlayKey);
                                                    overlayFaces.add(faceName, overlayFace);
                                                    elementHasOverlay = true;
                                                }
                                            }
                                            if (elementHasOverlay) {
                                                JsonObject overlayEl = el.deepCopy();
                                                overlayEl.add("faces", overlayFaces);
                                                overlayElements.add(overlayEl);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    elements.addAll(overlayElements);
                    root.add("elements", elements);

                    RuntimeResourceGenerator.applyTints(root, textures);
                    return root.toString();
                } else if (cleanPath.endsWith("_corner")) {
                    // Item model default (just NW corner bitmask: 10000000)
                    return generateModelJson(cleanPath + "_10000000", block);
                }
            }
        }
        return null;
    }
}
