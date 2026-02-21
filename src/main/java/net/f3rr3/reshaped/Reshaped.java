package net.f3rr3.reshaped;

import net.f3rr3.reshaped.block.Corner.CornerBlockEntity;
import net.f3rr3.reshaped.block.Corner.MixedCornerBlock;
import net.f3rr3.reshaped.block.Slab.MixedSlabBlock;
import net.f3rr3.reshaped.block.Slab.SlabBlockEntity;
import net.f3rr3.reshaped.block.Step.MixedStepBlock;
import net.f3rr3.reshaped.block.Step.StepBlockEntity;
import net.f3rr3.reshaped.block.VerticalSlab.MixedVerticalSlabBlock;
import net.f3rr3.reshaped.block.VerticalSlab.VerticalSlabBlockEntity;
import net.f3rr3.reshaped.block.VerticalStep.MixedVerticalStepBlock;
import net.f3rr3.reshaped.block.VerticalStep.VerticalStepBlockEntity;
import net.f3rr3.reshaped.command.MatrixCommand;
import net.f3rr3.reshaped.config.server.ServerConfig;
import net.f3rr3.reshaped.interaction.BlockInteractionService;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.BlockRegistryScanner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reshaped implements ModInitializer {
    public static final String MOD_ID = "reshaped";

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final MixedCornerBlock MIXED_CORNER = new MixedCornerBlock(Block.Settings.create().strength(2.0f).nonOpaque());
    public static final MixedVerticalStepBlock MIXED_VERTICAL_STEP = new MixedVerticalStepBlock(Block.Settings.create().strength(2.0f).nonOpaque());
    public static final MixedStepBlock MIXED_STEP = new MixedStepBlock(Block.Settings.create().strength(2.0f).nonOpaque());
    public static final MixedVerticalSlabBlock MIXED_VERTICAL_SLAB = new MixedVerticalSlabBlock(Block.Settings.create().strength(2.0f).nonOpaque());
    public static final MixedSlabBlock MIXED_SLAB = new MixedSlabBlock(Block.Settings.create().strength(2.0f).nonOpaque());

    public static BlockMatrix MATRIX;
    public static BlockEntityType<CornerBlockEntity> CORNER_BLOCK_ENTITY;
    public static BlockEntityType<VerticalStepBlockEntity> VERTICAL_STEP_BLOCK_ENTITY;
    public static BlockEntityType<StepBlockEntity> STEP_BLOCK_ENTITY;
    public static BlockEntityType<VerticalSlabBlockEntity> VERTICAL_SLAB_BLOCK_ENTITY;
    public static BlockEntityType<SlabBlockEntity> SLAB_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        LOGGER.info("Reshaping the world...");
        ServerConfig.load();

        // Initialize matrix immediately
        MATRIX = new BlockMatrix();

        // Start reactive block scanning and registration
        BlockRegistryScanner.init(MATRIX);

        // Register Mixed Blocks
        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "mixed_corner"), MIXED_CORNER);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "mixed_corner"), new net.minecraft.item.BlockItem(MIXED_CORNER, new net.minecraft.item.Item.Settings()));

        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "mixed_vertical_step"), MIXED_VERTICAL_STEP);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "mixed_vertical_step"), new net.minecraft.item.BlockItem(MIXED_VERTICAL_STEP, new net.minecraft.item.Item.Settings()));

        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "mixed_step"), MIXED_STEP);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "mixed_step"), new net.minecraft.item.BlockItem(MIXED_STEP, new net.minecraft.item.Item.Settings()));

        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "mixed_vertical_slab"), MIXED_VERTICAL_SLAB);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "mixed_vertical_slab"), new net.minecraft.item.BlockItem(MIXED_VERTICAL_SLAB, new net.minecraft.item.Item.Settings()));

        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "mixed_slab"), MIXED_SLAB);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "mixed_slab"), new net.minecraft.item.BlockItem(MIXED_SLAB, new net.minecraft.item.Item.Settings()));

        // Block Entity Types
        CORNER_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "corner_block_entity"),
                FabricBlockEntityTypeBuilder.create(CornerBlockEntity::new, MIXED_CORNER).build(null)
        );
        CornerBlockEntity.TYPE = CORNER_BLOCK_ENTITY;

        VERTICAL_STEP_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "vertical_step_block_entity"),
                FabricBlockEntityTypeBuilder.create(VerticalStepBlockEntity::new, MIXED_VERTICAL_STEP).build(null)
        );
        VerticalStepBlockEntity.TYPE = VERTICAL_STEP_BLOCK_ENTITY;

        STEP_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "step_block_entity"),
                FabricBlockEntityTypeBuilder.create(StepBlockEntity::new, MIXED_STEP).build(null)
        );
        StepBlockEntity.TYPE = STEP_BLOCK_ENTITY;

        VERTICAL_SLAB_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "vertical_slab_block_entity"),
                FabricBlockEntityTypeBuilder.create(VerticalSlabBlockEntity::new, MIXED_VERTICAL_SLAB).build(null)
        );
        VerticalSlabBlockEntity.TYPE = VERTICAL_SLAB_BLOCK_ENTITY;

        SLAB_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "slab_block_entity"),
                FabricBlockEntityTypeBuilder.create(SlabBlockEntity::new, MIXED_SLAB).build(null)
        );
        SlabBlockEntity.TYPE = SLAB_BLOCK_ENTITY;

        // Register commands
        if (ServerConfig.get().enableMatrixCommand) {
            MatrixCommand.register();
        } else {
            LOGGER.info("Skipping /reshaped_matrix command registration (disabled in server config).");
        }

        // Register network receivers
        NetworkHandler.registerServerReceivers();

        BlockInteractionService.register();

        LOGGER.info("Reshaping complete - Block matrix is now reactive.");
    }
}

