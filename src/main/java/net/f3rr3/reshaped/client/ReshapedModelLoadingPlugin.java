package net.f3rr3.reshaped.client;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.render.CompositeBakedModel;
import net.f3rr3.reshaped.util.RuntimeResourceGenerator;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelVariant;
import net.minecraft.client.render.model.json.WeightedUnbakedModel;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ReshapedModelLoadingPlugin implements ModelLoadingPlugin {

    @Override
    public void onInitializeModelLoader(Context context) {
        // Register block state resolvers for all reshaped blocks currently in registry
        for (Block block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            if (id.getNamespace().equals(Reshaped.MOD_ID)) {
                String path = id.getPath();

                // Custom resolver for StepBlock - handles dynamic segment models and rotation
                if (block instanceof net.f3rr3.reshaped.block.StepBlock) {
                    context.registerBlockStateResolver(block, resolverContext -> {
                        for (BlockState state : block.getStateManager().getStates()) {
                            // Construct bitmask: DF DB UF UB
                            int bitmask = 0;
                            if (state.get(net.f3rr3.reshaped.block.StepBlock.DOWN_FRONT)) bitmask |= 8;
                            if (state.get(net.f3rr3.reshaped.block.StepBlock.DOWN_BACK)) bitmask |= 4;
                            if (state.get(net.f3rr3.reshaped.block.StepBlock.UP_FRONT)) bitmask |= 2;
                            if (state.get(net.f3rr3.reshaped.block.StepBlock.UP_BACK)) bitmask |= 1;

                            Identifier modelId;
                            // Optimization: If full block, use base block model
                            if (bitmask == 15) {
                                Block base = Reshaped.MATRIX != null ? Reshaped.MATRIX.getBaseBlock(block) : null;
                                if (base != null) {
                                    Identifier baseId = Registries.BLOCK.getId(base);
                                    modelId = new Identifier(baseId.getNamespace(), "block/" + baseId.getPath());
                                } else {
                                    modelId = new Identifier(Reshaped.MOD_ID, "block/" + path);
                                }
                            } else {
                                String mask = String.format("%04d", Integer.parseInt(Integer.toBinaryString(bitmask)));
                                modelId = new Identifier(Reshaped.MOD_ID, "block/" + path + "_" + mask);
                            }

                            // Determine rotation based on axis
                            net.f3rr3.reshaped.block.StepBlock.StepAxis axis = state.get(net.f3rr3.reshaped.block.StepBlock.AXIS);
                            int yRotation = switch (axis) {
                                case NORTH_SOUTH -> 270; // North is Front
                                case EAST_WEST -> 180;   // West is Front
                            };

                            net.minecraft.client.render.model.ModelRotation rotation = net.minecraft.client.render.model.ModelRotation.get(0, yRotation);
                            ModelVariant variant = new ModelVariant(modelId, rotation.getRotation(), true, 1);
                            resolverContext.setModel(state, new WeightedUnbakedModel(Collections.singletonList(variant)));
                        }
                    });
                    
                    // Pre-register all possible segment combinations
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path));
                    for (int i = 0; i < 16; i++) {
                        String mask = String.format("%04d", Integer.parseInt(Integer.toBinaryString(i)));
                        context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_" + mask));
                    }
                    context.addModels(new Identifier(Reshaped.MOD_ID, "item/" + path));
                    continue; // Skip generic handling
                }

                // Custom resolver for VerticalStepBlock - handles absolute quadrant models
                if (block instanceof net.f3rr3.reshaped.block.VerticalStepBlock) {
                    context.registerBlockStateResolver(block, resolverContext -> {
                        for (BlockState state : block.getStateManager().getStates()) {
                            // Construct bitmask: NW NE SW SE
                            int bitmask = 0;
                            if (state.get(net.f3rr3.reshaped.block.VerticalStepBlock.NORTH_WEST)) bitmask |= 8;
                            if (state.get(net.f3rr3.reshaped.block.VerticalStepBlock.NORTH_EAST)) bitmask |= 4;
                            if (state.get(net.f3rr3.reshaped.block.VerticalStepBlock.SOUTH_WEST)) bitmask |= 2;
                            if (state.get(net.f3rr3.reshaped.block.VerticalStepBlock.SOUTH_EAST)) bitmask |= 1;

                            Identifier modelId;
                            if (bitmask == 15) {
                                // Full block
                                Block base = Reshaped.MATRIX != null ? Reshaped.MATRIX.getBaseBlock(block) : null;
                                if (base != null) {
                                    Identifier baseId = Registries.BLOCK.getId(base);
                                    modelId = new Identifier(baseId.getNamespace(), "block/" + baseId.getPath());
                                } else {
                                    modelId = new Identifier(Reshaped.MOD_ID, "block/" + path);
                                }
                            } else {
                                String mask = String.format("%04d", Integer.parseInt(Integer.toBinaryString(bitmask)));
                                modelId = new Identifier(Reshaped.MOD_ID, "block/" + path + "_" + mask);
                            }

                            // No rotation needed for vertical step segments as they are absolute
                            ModelVariant variant = new ModelVariant(modelId, net.minecraft.client.render.model.ModelRotation.X0_Y0.getRotation(), true, 1);
                            resolverContext.setModel(state, new WeightedUnbakedModel(Collections.singletonList(variant)));
                        }
                    });

                    // Pre-register all possible segment combinations
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path));
                    for (int i = 0; i < 16; i++) {
                        String mask = String.format("%04d", Integer.parseInt(Integer.toBinaryString(i)));
                        context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_" + mask));
                    }
                    context.addModels(new Identifier(Reshaped.MOD_ID, "item/" + path));
                    continue; // Skip generic handling
                }

                String templateType = RuntimeResourceGenerator.getTemplateType(block, id);

                if (templateType != null) {
                    final String finalTemplateType = templateType;
                    context.registerBlockStateResolver(block, resolverContext -> {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("path", path);

                        Block base = Reshaped.MATRIX != null ? Reshaped.MATRIX.getBaseBlock(block) : null;
                        if (base != null) {
                            Identifier baseId = Registries.BLOCK.getId(base);
                            placeholders.put("base_model", baseId.getNamespace() + ":block/" + baseId.getPath());
                        }

                        String blockstateJson = RuntimeResourceGenerator.generateBlockStateJson(block, finalTemplateType, placeholders);
                        if (blockstateJson != null) {
                            try {
                                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(blockstateJson).getAsJsonObject();
                                if (json.has("variants")) {
                                    com.google.gson.JsonObject variants = json.getAsJsonObject("variants");
                                    for (BlockState state : block.getStateManager().getStates()) {
                                        String stateString = RuntimeResourceGenerator.serializeState(state);
                                        com.google.gson.JsonElement variantElem = RuntimeResourceGenerator.findMatchingVariant(variants, stateString);
                                        if (variantElem != null) {
                                            ModelVariant variant = RuntimeResourceGenerator.parseVariant(variantElem);
                                            resolverContext.setModel(state, new WeightedUnbakedModel(Collections.singletonList(variant)));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Reshaped.LOGGER.error("Failed to apply blockstate template for " + id, e);
                            }
                        }
                    });
                } else if (path.startsWith("mixed_")) {
                    // Manual resolver for Mixed Blocks - just map to empty/air model since CompositeBakedModel handles it
                    context.registerBlockStateResolver(block, resolverContext -> resolverContext.setModel(block.getDefaultState(), new WeightedUnbakedModel(Collections.singletonList(new ModelVariant(new Identifier("minecraft:block/air"), null, false, 1)))));
                }

                // Pre-register basic models
                context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path));
                if (block instanceof StairsBlock) {
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_inner"));
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_outer"));
                } else if (block instanceof SlabBlock) {
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_top"));
                }
                
                // Add models for Vertical Slabs components
                if (path.endsWith("_vertical_slab")) {
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_north"));
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_south"));
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_east"));
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_west"));
                }

                if (path.endsWith("_corner")) {
                    for (int i = 0; i < 256; i++) {
                        String mask = String.format("%8s", Integer.toBinaryString(i)).replace(' ', '0');
                        context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_" + mask));
                    }
                }
                context.addModels(new Identifier(Reshaped.MOD_ID, "item/" + path));
            }
        }

        // Register a model resolver for everything else (geometry models, items)
        context.resolveModel().register(resolverContext -> {
            Identifier id = resolverContext.id();
            if (id.getNamespace().equals(Reshaped.MOD_ID)) {
                String normalizedPath = RuntimeResourceGenerator.normalizePath(id.getPath());
                String json = RuntimeResourceGenerator.generateModelJson(normalizedPath);
                if (json != null) {
                    try {
                        return JsonUnbakedModel.deserialize(json);
                    } catch (Exception e) {
                        Reshaped.LOGGER.error("Failed to deserialize dynamic model: " + id, e);
                    }
                }
            }
            return null;
        });

        // Wrap corner block models with our custom composite model
        context.resolveModel().register(resolverContext -> {
            Identifier id = resolverContext.id();
            if (id.getNamespace().equals(Reshaped.MOD_ID) && id.getPath().startsWith("block/") && id.getPath().contains("_corner_")) {
                // This captures the dynamically generated bitmask models
                // They don't need wrapping, they are used AS segments in the composite.
                return null;
            }
            return null;
        });

        context.modifyModelAfterBake().register((model, context1) -> {
            Identifier id = context1.id();
            if (id.getNamespace().equals(Reshaped.MOD_ID)) {
                // Apply Composite Model wrapper to base blocks that support mixing
                if (id.getPath().endsWith("_corner") && !id.getPath().contains("_corner_")) {
                     return new CompositeBakedModel(model);
                }
                if (id.getPath().endsWith("_vertical_step") && !id.getPath().contains("_vertical_step_")) {
                     return new CompositeBakedModel(model);
                }
                if (id.getPath().endsWith("_step") && !id.getPath().contains("_step_")) {
                     return new CompositeBakedModel(model);
                }
                // Vertical Slabs and Slabs might need explicit wrapping if they are base types?
                // Or Mixed blocks only?
                // Mixed blocks (mixed_corner, etc) use custom resolvers.
                // WE ALSO NEED TO WRAP THE MIXED BLOCKS models (which are placeholders)
                if (id.getPath().startsWith("mixed_")) {
                    return new CompositeBakedModel(model);
                }
            }
            return model;
        });
    }
}
