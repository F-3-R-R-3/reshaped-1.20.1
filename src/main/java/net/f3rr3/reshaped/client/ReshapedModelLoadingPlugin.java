package net.f3rr3.reshaped.client;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.render.CornerBakedModel;
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
                }

                // Pre-register basic models
                context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path));
                if (block instanceof StairsBlock) {
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_inner"));
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_outer"));
                } else if (block instanceof SlabBlock) {
                    context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + path + "_top"));
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
            if (id.getNamespace().equals(Reshaped.MOD_ID) && id.getPath().endsWith("_corner") && !id.getPath().contains("_corner_")) {
                // Wrap the base corner blocks (the ones returned by registerBlockStateResolver)
                return new CornerBakedModel(model);
            }
            return model;
        });
    }
}
