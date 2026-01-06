package net.f3rr3.reshaped;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.BlockRegistryScanner;
import net.f3rr3.reshaped.registry.VerticalSlabRegistry;
import net.f3rr3.reshaped.registry.VariantCompleter;
import net.f3rr3.reshaped.command.MatrixCommand;

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

		// Scan registry and build matrix
		MATRIX = BlockRegistryScanner.scanAndBuildMatrix();
		
		// Prune columns with no variants (though Scanner currently handles this partially)
		MATRIX.removeStandaloneColumns();

		// Complete missing variants (slabs/stairs)
		VariantCompleter.completeMatrix(MATRIX);

		// Register vertical slabs for all blocks in the matrix
		VerticalSlabRegistry.registerVerticalSlabs(MATRIX);

		// FINAL REFRESH: Ensure all dynamically added blocks (slabs, stairs, vertical slabs) 
		// are added to the fast-lookup set for the overlay and tooltips.
		MATRIX.refresh();

		// Register commands
		MatrixCommand.register();

		LOGGER.info("Matrix built with " + MATRIX.getMatrix().size() + " columns!");
	}
}