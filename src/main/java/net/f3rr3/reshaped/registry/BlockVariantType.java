package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.util.Identifier;

/**
 * Represents a type of block that can be automatically generated for a base block.
 * e.g., Vertical Slab, Vertical Stairs, 1/8 Block, etc.
 */
public interface BlockVariantType {
    /**
     * Unique identifier for this variant type (e.g., "vertical_slab").
     */
    String getName();

    /**
     * Checks if this variant type can be applied to the given base block.
     */
    boolean appliesTo(Block baseBlock);

    /**
     * Registers the variant for the given base block if it hasn't been registered yet.
     */
    void register(Block baseBlock, BlockMatrix matrix);

    /**
     * Returns the model identifier for a specific block state of this variant.
     * Returns null if this variant doesn't handle the state.
     */
    Identifier getModelId(BlockState state);

    /**
     * Returns the model rotation for a specific block state of this variant.
     * Returns null if this variant doesn't handle the state.
     */
    ModelRotation getRotation(BlockState state);

    /**
     * Generates the model JSON content for a specific path.
     * Returns null if this variant doesn't handle the path.
     */
    String generateModelJson(String path, Block block);
}
