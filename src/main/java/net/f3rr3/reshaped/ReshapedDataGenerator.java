package net.f3rr3.reshaped;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class ReshapedDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		// Datagen is no longer strictly required as resources are now generated at runtime.
		// Pack pack = fabricDataGenerator.createPack();
		// pack.addProvider(ReshapedModelProvider::new);
		// pack.addProvider(ReshapedLanguageProvider::new);
	}
}
