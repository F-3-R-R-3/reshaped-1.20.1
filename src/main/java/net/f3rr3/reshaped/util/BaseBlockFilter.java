package net.f3rr3.reshaped.util;

import net.minecraft.block.*;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BaseBlockFilter {
    public static final TagKey<Block> ALLOW_TAG = TagKey.of(RegistryKeys.BLOCK, new Identifier("reshaped", "base_block_allow"));
    public static final TagKey<Block> DENY_TAG = TagKey.of(RegistryKeys.BLOCK, new Identifier("reshaped", "base_block_deny"));

    private BaseBlockFilter() {
    }

    public static Set<Block> collectBaseCandidates() {
        Set<Block> sorted = new LinkedHashSet<>();
        Registries.BLOCK.stream()
                .filter(BaseBlockFilter::isBaseCandidate)
                .sorted(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()))
                .forEach(sorted::add);
        return sorted;
    }

    public static boolean isBaseCandidate(Block block) {
        if (block == null || block == Blocks.AIR) return false;
        Identifier id = Registries.BLOCK.getId(block);
        boolean isModded = !id.getNamespace().equals("minecraft") && !id.getNamespace().equals("reshaped");

        if (Registries.ITEM.get(id) == Items.AIR) {
            if (isModded)
                net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - No Item in registry", id);
            return false;
        }
        if (isIgnoredForMatrix(block)) {
            // if (isModded) net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Ignored Namespace/Path", id);
            return false;
        }

        BlockState state = block.getDefaultState();

        // 0. Tag-based overrides: highest priority
        if (state.isIn(DENY_TAG)) {
            if (isModded) net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Denied by Tag", id);
            return false;
        }
        if (state.isIn(ALLOW_TAG)) {
            if (isModded) net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Accepted {} - Allowed by Tag", id);
            return true;
        }

        if (block instanceof BlockEntityProvider) {
            if (isModded) net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Has BlockEntity", id);
            return false;
        }

        // 1. Interactive detection: check for overrides of key interactive/functional methods.
        if (hasFunctionalOverride(block)) {
            if (isModded)
                net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Functional override detected", id);
            return false;
        }

        // 2. Functional Property Scanning: exclude blocks with properties that imply state-based logic or redstone interaction.
        if (hasFunctionalProperty(state)) {
            if (isModded)
                net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Functional property detected", id);
            return false;
        }

        if (block instanceof PillarBlock) {
            if (isModded) net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Is PillarBlock", id);
            return false;
        }
        if (isFunctionalOrReactiveBlock(block)) {
            if (isModded)
                net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Functional/Reactive", id);
            return false;
        }
        if (isLikelyVariantType(block)) {
            if (isModded)
                net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Likely Variant Type", id);
            return false;
        }

        if (state.contains(Properties.AXIS)) {
            if (isModded)
                net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Has AXIS property", id);
            return false;
        }
        if (state.getRenderType() != BlockRenderType.MODEL) {
            if (isModded)
                net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Not MODEL render type", id);
            return false;
        }

        // Strict full block check: outline shape must be a perfect full cube.
        if (!state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).equals(VoxelShapes.fullCube())) {
            if (isModded)
                net.f3rr3.reshaped.Reshaped.LOGGER.info("BaseBlockFilter: Rejected {} - Not a perfect full cube", id);
            return false;
        }

        return true;
    }

    private static boolean hasFunctionalOverride(Block block) {
        try {
            Class<?> clazz = block.getClass();

            // Check for UI-opening method
            if (isOverridden(clazz, "createScreenHandlerFactory", BlockState.class, World.class, BlockPos.class))
                return true;

            // Check for right-click interaction (onUse)
            // Note: Many blocks override this just to return SUCCESS/PASS without doing much, 
            // but for base candidate filtering, it's safer to exclude.
            if (isOverridden(clazz, "onUse", BlockState.class, World.class, BlockPos.class, net.minecraft.entity.player.PlayerEntity.class, Hand.class, net.minecraft.util.hit.BlockHitResult.class))
                return true;

            // Check for comparator interaction
            if (isOverridden(clazz, "hasComparatorOutput", BlockState.class)) {
                // If it HAS comparator output, it is functional.
                // We use isOverridden to see if it actually returns a non-default value.
                // Most base blocks don't even have this declared.
                return true;
            }

        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean isOverridden(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method m = clazz.getMethod(methodName, parameterTypes);
        Class<?> dc = m.getDeclaringClass();
        return dc != Block.class && dc != AbstractBlock.class;
    }

    private static boolean hasFunctionalProperty(BlockState state) {
        return state.contains(Properties.POWERED)
                || state.contains(Properties.LIT)
                || state.contains(Properties.TRIGGERED)
                || state.contains(Properties.EXTENDED)
                || state.contains(Properties.OPEN)
                || state.contains(Properties.SIGNAL_FIRE)
                || state.contains(Properties.ENABLED)
                || state.contains(Properties.BITES)
                || state.contains(Properties.DELAY)
                || state.contains(Properties.EYE)
                || state.contains(Properties.FACING)
                || state.contains(Properties.HORIZONTAL_FACING);
    }

    private static boolean isLikelyVariantType(Block block) {
        return block instanceof SlabBlock
                || block instanceof StairsBlock
                || block instanceof FenceGateBlock
                || block instanceof WallBlock
                || block instanceof DoorBlock
                || block instanceof TrapdoorBlock
                || block instanceof PressurePlateBlock
                || block instanceof ButtonBlock
                || block instanceof CarpetBlock
                || block instanceof ConnectingBlock
                || block instanceof HorizontalConnectingBlock
                || block instanceof PlantBlock
                || block instanceof TallPlantBlock
                || block instanceof MultifaceGrowthBlock
                || block instanceof VineBlock
                || block instanceof CobwebBlock;
    }

    private static boolean isIgnoredForMatrix(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        String namespace = id.getNamespace();
        String path = id.getPath();
        String className = block.getClass().getName().toLowerCase();

        if ("reshaped".equals(namespace)) {
            // Generated reshaped variants must never become new base candidates,
            // otherwise they split into their own columns on rebuild.
            return true;
        }

        return namespace.contains("copycat")
                || path.contains("copycat")
                || className.contains("copycat");
    }

    private static boolean isFunctionalOrReactiveBlock(Block block) {
        if (block instanceof FallingBlock
                || block instanceof LeavesBlock
                || block instanceof PistonBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof ObserverBlock
                || block instanceof RedstoneOreBlock
                || block instanceof RedstoneLampBlock
                || block instanceof TargetBlock
                || block instanceof TntBlock
                || block instanceof SlimeBlock
                || block instanceof NoteBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof HoneyBlock) {
            return true;
        }

        // Conservative fallback for modded blocks that are function/reactive but do not extend known classes.
        Identifier id = Registries.BLOCK.getId(block);
        String path = id.getPath();
        return path.contains("piston")
                || path.contains("observer")
                || path.contains("redstone")
                || path.contains("respawn_anchor")
                || path.contains("target")
                || path.contains("tnt")
                || path.contains("slime")
                || path.contains("bracket")
                || path.contains("concrete_powder")
                || path.endsWith("_powder")
                || path.contains("leaves")
                || path.contains("falling");
    }

}

