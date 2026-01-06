package net.f3rr3.reshaped.client;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.util.RuntimeResourceGenerator;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelVariant;
import net.minecraft.client.render.model.json.WeightedUnbakedModel;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;

public class ReshapedModelLoadingPlugin implements ModelLoadingPlugin {

    @Override
    public void onInitializeModelLoader(Context context) {
        // Register block state resolvers for all reshaped blocks
        if (Reshaped.MATRIX != null) {
            for (List<Block> column : Reshaped.MATRIX.getMatrix().values()) {
                for (Block block : column) {
                    Identifier id = Registries.BLOCK.getId(block);
                    if (id.getNamespace().equals(Reshaped.MOD_ID)) {
                        context.registerBlockStateResolver(block, resolverContext -> {
                            for (BlockState state : block.getStateManager().getStates()) {
                                Identifier modelId = RuntimeResourceGenerator.getVariantModelId(state);
                                ModelRotation rotation = RuntimeResourceGenerator.getVariantRotation(state);
                                
                                // In 1.20.1 Yarn, ModelVariant is a record that expects AffineTransformation (getRotation())
                                ModelVariant variant = new ModelVariant(modelId, rotation.getRotation(), false, 1);
                                // registerBlockStateResolver requires an UnbakedModel. WeightedUnbakedModel implements UnbakedModel.
                                resolverContext.setModel(state, new WeightedUnbakedModel(Collections.singletonList(variant)));
                            }
                        });
                        
                        // Pre-register all possible geometry models
                        context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + id.getPath()));
                        context.addModels(new Identifier(Reshaped.MOD_ID, "block/" + id.getPath() + "_top"));
                        context.addModels(new Identifier(Reshaped.MOD_ID, "item/" + id.getPath()));
                    }
                }
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
    }
}
