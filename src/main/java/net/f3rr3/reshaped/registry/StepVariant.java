package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Step.OxidizableStepBlock;
import net.f3rr3.reshaped.block.Step.StepBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.RuntimeResourceGenerator;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.Oxidizable;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StepVariant implements BlockVariantType {
    private static final Map<Block, StepBlock> BASE_TO_STEP = new HashMap<>();

    @Override
    public String getName() {
        return "step";
    }

    @Override
    public void register(Block baseBlock, BlockMatrix matrix) {
        Identifier baseId = Registries.BLOCK.getId(baseBlock);
        String baseName = baseId.getPath().replace("_planks", "").replace("_block", "");
        String path = baseName + "_step";
        Identifier id = new Identifier(Reshaped.MOD_ID, path);

        Block existingAtId = Registries.BLOCK.get(id);
        if (existingAtId != Blocks.AIR) {
            Block existingBase = matrix.getBaseBlock(existingAtId);
            if (existingBase != null && existingBase != baseBlock) {
                path = baseId.getNamespace() + "_" + baseId.getPath() + "_step";
                id = new Identifier(Reshaped.MOD_ID, path);
            }
        }

        if (Registries.BLOCK.get(id) != Blocks.AIR) {
            Block existing = Registries.BLOCK.get(id);
            if (existing instanceof StepBlock step) {
                BASE_TO_STEP.putIfAbsent(baseBlock, step);
                List<Block> variants = matrix.getMatrix().get(baseBlock);
                if (variants != null && !variants.contains(step)) {
                    variants.add(step);
                }
            }
            return;
        }

        StepBlock step;
        AbstractBlock.Settings settings = VariantSettingsFactory.create(baseBlock);

        if (baseBlock instanceof Oxidizable oxidizable) {
            step = new OxidizableStepBlock(oxidizable.getDegradationLevel(), settings);
        } else {
            step = new StepBlock(settings);
        }

        Registry.register(Registries.BLOCK, id, step);
        Registry.register(Registries.ITEM, id, new BlockItem(step, new Item.Settings()));

        matrix.addVariant(baseBlock, step, true);
        matrix.setReason(step, "Dynamically registered Step Block for " + baseBlock.getName().getString());
        BASE_TO_STEP.put(baseBlock, step);
        Reshaped.LOGGER.debug("Registered step for: {}", baseId);

        FlammableBlockRegistry flammableRegistry = FlammableBlockRegistry.getDefaultInstance();
        FlammableBlockRegistry.Entry flammableEntry = flammableRegistry.get(baseBlock);
        if (flammableEntry != null) {
            flammableRegistry.add(step, flammableEntry.getBurnChance(), flammableEntry.getSpreadChance());
        }

        linkRelations(baseBlock, step);
        for (Map.Entry<Block, StepBlock> entry : BASE_TO_STEP.entrySet()) {
            if (entry.getKey() != baseBlock) {
                linkRelations(entry.getKey(), entry.getValue());
            }
        }
    }

    private void linkRelations(Block base, StepBlock step) {
        Optional<Block> nextOxidation = Oxidizable.getIncreasedOxidationBlock(base);
        if (nextOxidation.isPresent() && BASE_TO_STEP.containsKey(nextOxidation.get())) {
            StepBlock nextStep = BASE_TO_STEP.get(nextOxidation.get());
            OxidizableBlocksRegistry.registerOxidizableBlockPair(step, nextStep);
        }

        try {
            Map<Block, Block> unwaxedToWaxed = net.f3rr3.reshaped.mixin.HoneycombItemAccessor.getUnwaxedToWaxedSupplier().get();
            Block waxedBase = unwaxedToWaxed.get(base);
            if (waxedBase != null && BASE_TO_STEP.containsKey(waxedBase)) {
                StepBlock waxedStep = BASE_TO_STEP.get(waxedBase);
                OxidizableBlocksRegistry.registerWaxableBlockPair(step, waxedStep);
            }

            for (Map.Entry<Block, Block> entry : unwaxedToWaxed.entrySet()) {
                if (entry.getValue() == base && BASE_TO_STEP.containsKey(entry.getKey())) {
                    StepBlock unwaxedStep = BASE_TO_STEP.get(entry.getKey());
                    OxidizableBlocksRegistry.registerWaxableBlockPair(unwaxedStep, step);
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Map<Block, Block> strippedBlocks = net.f3rr3.reshaped.mixin.AxeItemAccessor.getStrippedBlocks();
            Block strippedBase = strippedBlocks.get(base);
            if (strippedBase != null && BASE_TO_STEP.containsKey(strippedBase)) {
                StepBlock strippedStep = BASE_TO_STEP.get(strippedBase);
                StrippableBlockRegistry.register(step, strippedStep);
            }

            for (Map.Entry<Block, Block> entry : strippedBlocks.entrySet()) {
                if (entry.getValue() == base && BASE_TO_STEP.containsKey(entry.getKey())) {
                    StepBlock unstrippedStep = BASE_TO_STEP.get(entry.getKey());
                    StrippableBlockRegistry.register(unstrippedStep, step);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public String generateModelJson(String path, Block block) {
        String cleanPath = RuntimeResourceGenerator.stripRandomVariantSuffix(path);
        int randomIndex = RuntimeResourceGenerator.extractRandomVariantIndex(path);
        if (cleanPath.contains("_step") && !cleanPath.contains("_vertical_step")) {
            // Check for segment mask (e.g. "_1010")
            // Default to single segment (1000 - Down Front) if no mask found
            String mask = RuntimeResourceGenerator.extractMaskSuffix(cleanPath);
            boolean[] segments = RuntimeResourceGenerator.parseMaskOrDefault(mask, true, false, false, false);

            Block targetBlock = RuntimeResourceGenerator.resolveBlockForPath(cleanPath, block);
            Block baseBlock = Reshaped.MATRIX.getBaseBlock(targetBlock);
            if (baseBlock != null) {
                Map<String, String> textures = RuntimeResourceGenerator.getModelTextures(baseBlock, randomIndex);
                return RuntimeResourceGenerator.generateStepModelForSegments(
                        segments[0],
                        segments[1],
                        segments[2],
                        segments[3],
                        textures
                );
            }
        }
        return null;
    }
}
