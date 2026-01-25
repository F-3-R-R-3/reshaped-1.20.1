package net.f3rr3.reshaped;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.BlockRegistryScanner;
import net.f3rr3.reshaped.command.MatrixCommand;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.f3rr3.reshaped.block.CornerBlock;
import net.f3rr3.reshaped.block.entity.CornerBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class Reshaped implements ModInitializer {
	public static final String MOD_ID = "reshaped";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static BlockMatrix MATRIX;
	public static BlockEntityType<CornerBlockEntity> CORNER_BLOCK_ENTITY;

	@Override
	public void onInitialize() {
		LOGGER.info("Reshaping the world...");

		// Initialize matrix immediately
		MATRIX = new BlockMatrix();
		
		// Start reactive block scanning and registration
		BlockRegistryScanner.init(MATRIX);

		// Corner block entity registration will be finalized after scanning
		// But we register the type here. We will use a late-bind approach for blocks.
		CORNER_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			new Identifier(MOD_ID, "corner_block_entity"),
			FabricBlockEntityTypeBuilder.create(CornerBlockEntity::new).build(null)
		);
		CornerBlockEntity.TYPE = CORNER_BLOCK_ENTITY;

		// Register commands
		MatrixCommand.register();

		// Register network receivers
		NetworkHandler.registerServerReceivers();

		// Handle corner block partial mining
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (state.getBlock() instanceof CornerBlock corner) {
				if (world.isClient) return true;

				// Raycast to find which specific corner the player is looking at
				double reach = 5.0; 
				HitResult hitResult = player.raycast(reach, 1.0F, false);

				if (hitResult.getType() == HitResult.Type.BLOCK) {
					BlockHitResult blockHitResult = (BlockHitResult) hitResult;
					if (blockHitResult.getBlockPos().equals(pos)) {
						double hitX = blockHitResult.getPos().x - (double)pos.getX();
						double hitY = blockHitResult.getPos().y - (double)pos.getY();
						double hitZ = blockHitResult.getPos().z - (double)pos.getZ();

						BooleanProperty property = corner.getPropertyFromHit(hitX, hitY, hitZ, blockHitResult.getSide(), false);
						if (property != null && state.get(property)) {
							int count = 0;
							BooleanProperty[] allProps = {CornerBlock.DOWN_NW, CornerBlock.DOWN_NE, CornerBlock.DOWN_SW, CornerBlock.DOWN_SE, 
														 CornerBlock.UP_NW, CornerBlock.UP_NE, CornerBlock.UP_SW, CornerBlock.UP_SE};
							for (BooleanProperty p : allProps) {
								if (state.get(p)) count++;
							}

							if (count > 1) {
								Identifier materialId = null;
								BlockEntity be = world.getBlockEntity(pos);
								if (be instanceof CornerBlockEntity cbe) {
									BooleanProperty[] quadrants = {CornerBlock.DOWN_NW, CornerBlock.DOWN_NE, CornerBlock.DOWN_SW, CornerBlock.DOWN_SE, 
																 CornerBlock.UP_NW, CornerBlock.UP_NE, CornerBlock.UP_SW, CornerBlock.UP_SE};
									for (int i = 0; i < 8; i++) {
										if (quadrants[i] == property) {
											materialId = cbe.getCornerMaterial(i);
											cbe.setCornerMaterial(i, null); // Clear it
											break;
										}
									}
								}

								world.setBlockState(pos, state.with(property, false), 3);
								if (!player.isCreative()) {
									Block dropBlock = materialId != null ? Registries.BLOCK.get(materialId) : state.getBlock();
									Block.dropStack(world, pos, new ItemStack(dropBlock.asItem()));
								}
								return false; // Cancel the full block break
							}
						}
					}
				}
			}
			return true;
		});

		LOGGER.info("Reshaping complete - Block matrix is now reactive.");
	}
}