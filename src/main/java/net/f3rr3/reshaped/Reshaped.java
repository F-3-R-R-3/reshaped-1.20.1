package net.f3rr3.reshaped;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.BlockRegistryScanner;
import net.f3rr3.reshaped.command.MatrixCommand;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.f3rr3.reshaped.block.CornerBlock;
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

	@Override
	public void onInitialize() {
		LOGGER.info("Reshaping the world...");

		// Initialize matrix immediately
		MATRIX = new BlockMatrix();
		
		// Start reactive block scanning and registration
		BlockRegistryScanner.init(MATRIX);

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
								world.setBlockState(pos, state.with(property, false), 3);
								if (!player.isCreative()) {
									Block.dropStack(world, pos, new ItemStack(state.getBlock().asItem()));
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