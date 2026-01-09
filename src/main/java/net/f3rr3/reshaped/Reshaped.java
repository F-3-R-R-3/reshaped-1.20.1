package net.f3rr3.reshaped;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.BlockRegistryScanner;
import net.f3rr3.reshaped.registry.VerticalSlabRegistry;
import net.f3rr3.reshaped.registry.VariantCompleter;
import net.f3rr3.reshaped.command.MatrixCommand;
import net.f3rr3.reshaped.network.NetworkHandler;

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

		LOGGER.info("Reshaping complete - Block matrix is now reactive.");
	}
}