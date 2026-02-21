package net.f3rr3.reshaped.interaction;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Corner.CornerBlock;
import net.f3rr3.reshaped.block.Corner.CornerBlockEntity;
import net.f3rr3.reshaped.block.Corner.MixedCornerBlock;
import net.f3rr3.reshaped.block.Slab.MixedSlabBlock;
import net.f3rr3.reshaped.block.Slab.SlabBlockEntity;
import net.f3rr3.reshaped.block.Step.MixedStepBlock;
import net.f3rr3.reshaped.block.Step.StepBlock;
import net.f3rr3.reshaped.block.Step.StepBlockEntity;
import net.f3rr3.reshaped.block.VericalStairs.VerticalStairsBlock;
import net.f3rr3.reshaped.block.VerticalSlab.MixedVerticalSlabBlock;
import net.f3rr3.reshaped.block.VerticalSlab.VerticalSlabBlock;
import net.f3rr3.reshaped.block.VerticalSlab.VerticalSlabBlockEntity;
import net.f3rr3.reshaped.block.VerticalStep.MixedVerticalStepBlock;
import net.f3rr3.reshaped.block.VerticalStep.VerticalStepBlock;
import net.f3rr3.reshaped.block.VerticalStep.VerticalStepBlockEntity;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

public final class BlockInteractionService {
    private BlockInteractionService() {
    }

    private static void consumeItem(net.minecraft.entity.player.PlayerEntity player, ItemStack stack, Block itemBlock, net.minecraft.world.World world, BlockPos pos) {
        if (!player.isCreative()) {
            stack.decrement(1);
        }
        net.minecraft.sound.BlockSoundGroup sound = itemBlock.getSoundGroup(itemBlock.getDefaultState());
        world.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sound.SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    private static Vec3d getLocalHit(BlockHitResult hitResult, BlockPos pos) {
        return hitResult.getPos().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockState applyProperties(BlockState source, BlockState target, BooleanProperty[] properties) {
        for (BooleanProperty property : properties) {
            if (source.get(property)) {
                target = target.with(property, true);
            }
        }
        return target;
    }

    private static MaterialAnalysis analyzeMaterials(BlockState state, BooleanProperty[] properties, Identifier[] materials) {
        Identifier commonMaterial = null;
        boolean isMixed = false;
        for (int i = 0; i < properties.length; i++) {
            if (state.get(properties[i])) {
                Identifier mat = materials[i];
                if (commonMaterial == null) {
                    commonMaterial = mat;
                } else if (mat != null && !mat.equals(commonMaterial)) {
                    isMixed = true;
                }
            }
        }

        return new MaterialAnalysis(commonMaterial, isMixed);
    }

    private static Identifier materialForProperty(BooleanProperty property, BooleanProperty[] properties, Identifier[] materials) {
        for (int i = 0; i < properties.length; i++) {
            if (properties[i] == property) {
                return materials[i];
            }
        }
        return null;
    }

    private static BooleanProperty[] getSegmentProperties(Block block) {
        if (block instanceof CornerBlock || block instanceof MixedCornerBlock) {
            return net.f3rr3.reshaped.util.BlockSegmentUtils.CORNER_PROPERTIES;
        }
        if (block instanceof StepBlock || block instanceof MixedStepBlock) {
            return net.f3rr3.reshaped.util.BlockSegmentUtils.STEP_PROPERTIES;
        }
        if (block instanceof VerticalStepBlock || block instanceof MixedVerticalStepBlock) {
            return net.f3rr3.reshaped.util.BlockSegmentUtils.VERTICAL_STEP_PROPERTIES;
        }
        if (block instanceof MixedSlabBlock) {
            return new BooleanProperty[]{MixedSlabBlock.BOTTOM, MixedSlabBlock.TOP};
        }
        return new BooleanProperty[]{MixedVerticalSlabBlock.NEGATIVE, MixedVerticalSlabBlock.POSITIVE};
    }

    private static boolean canMixSlab(BlockHitResult hitResult, BlockPos pos, SlabType type) {
        boolean isTop = (hitResult.getPos().y - (double) pos.getY()) > 0.5;
        if (hitResult.getSide() == net.minecraft.util.math.Direction.UP) {
            isTop = false;
        }

        if (type == SlabType.BOTTOM) {
            return hitResult.getSide() == net.minecraft.util.math.Direction.UP
                    || (hitResult.getSide().getAxis().isHorizontal() && isTop);
        }
        return hitResult.getSide() == net.minecraft.util.math.Direction.DOWN
                || (hitResult.getSide().getAxis().isHorizontal() && !isTop);
    }

    private static void syncChunkToWatchers(net.minecraft.world.World world, BlockPos pos) {
        if (world.getServer() == null) {
            return;
        }

        WorldChunk chunk = world.getWorldChunk(pos);
        if (chunk == null) {
            return;
        }

        ChunkDataS2CPacket packet = new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null);
        ((ServerChunkManager) world.getChunkManager()).threadedAnvilChunkStorage
                .getPlayersWatchingChunk(chunk.getPos(), false)
                .forEach(serverPlayer -> serverPlayer.networkHandler.sendPacket(packet));
    }


    public static void register() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getStackInHand(hand);
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.getBlock() instanceof CornerBlock cornerBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof CornerBlock itemBlock) {
                // Check if materials differ (only then do we need special transition)
                if (state.getBlock() != itemBlock) {
                    // Check if quadrant is empty
                    Vec3d localHit = getLocalHit(hitResult, pos);

                    BooleanProperty property = cornerBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hitResult.getSide(), true);

                    if (property != null && !state.get(property)) {
                        if (!world.isClient) {
                            net.minecraft.block.BlockState mixedState = Reshaped.MIXED_CORNER.getDefaultState();
                            BooleanProperty[] allProps = net.f3rr3.reshaped.util.BlockSegmentUtils.CORNER_PROPERTIES;
                            mixedState = applyProperties(state, mixedState, allProps);
                            mixedState = mixedState.with(property, true);
                            mixedState = mixedState.with(CornerBlock.WATERLOGGED, state.get(CornerBlock.WATERLOGGED));
                            world.setBlockState(pos, mixedState, 3);

                            BlockEntity be = world.getBlockEntity(pos);
                            if (be instanceof CornerBlockEntity cbe) {
                                Identifier oldMaterial = Registries.BLOCK.getId(state.getBlock());
                                Identifier newMaterial = Registries.BLOCK.getId(itemBlock);

                                for (int i = 0; i < 8; i++) {
                                    if (state.get(allProps[i])) cbe.setCornerMaterial(i, oldMaterial);
                                }
                                for (int i = 0; i < 8; i++) {
                                    if (allProps[i] == property) {
                                        cbe.setCornerMaterial(i, newMaterial);
                                        break;
                                    }
                                }
                            }
                            consumeItem(player, stack, itemBlock, world, pos);
                        }
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                }
            } else if (state.getBlock() instanceof VerticalStepBlock vsBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof VerticalStepBlock itemBlock) {
                if (state.getBlock() != itemBlock) {
                    Vec3d localHit = getLocalHit(hitResult, pos);

                    BooleanProperty property = vsBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hitResult.getSide(), true);
                    if (property != null && !state.get(property)) {
                        if (!world.isClient) {
                            // Transition VerticalStep -> MixedVerticalStep
                            BlockState mixedState = Reshaped.MIXED_VERTICAL_STEP.getDefaultState();
                            BooleanProperty[] allProps = net.f3rr3.reshaped.util.BlockSegmentUtils.VERTICAL_STEP_PROPERTIES;
                            mixedState = applyProperties(state, mixedState, allProps);
                            mixedState = mixedState.with(property, true);
                            mixedState = mixedState.with(VerticalStepBlock.WATERLOGGED, state.get(VerticalStepBlock.WATERLOGGED));

                            world.setBlockState(pos, mixedState, 3);

                            BlockEntity be = world.getBlockEntity(pos);
                            if (be instanceof VerticalStepBlockEntity verticalStepBlockEntity) {
                                Identifier oldMaterial = Registries.BLOCK.getId(state.getBlock());
                                Identifier newMaterial = Registries.BLOCK.getId(itemBlock);

                                for (int i = 0; i < 4; i++)
                                    if (state.get(allProps[i])) verticalStepBlockEntity.setMaterial(i, oldMaterial);
                                for (int i = 0; i < 4; i++)
                                    if (allProps[i] == property) verticalStepBlockEntity.setMaterial(i, newMaterial);
                            }
                            consumeItem(player, stack, itemBlock, world, pos);
                        }
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                }
            } else if (state.getBlock() instanceof StepBlock stepBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof StepBlock itemBlock) {
                if (state.getBlock() != itemBlock) {
                    Vec3d localHit = getLocalHit(hitResult, pos);

                    BooleanProperty property = stepBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hitResult.getSide(), true, state);
                    if (property != null && !state.get(property)) {
                        if (!world.isClient) {
                            BlockState mixedState = Reshaped.MIXED_STEP.getDefaultState().with(StepBlock.AXIS, state.get(StepBlock.AXIS));
                            BooleanProperty[] allProps = net.f3rr3.reshaped.util.BlockSegmentUtils.STEP_PROPERTIES;
                            mixedState = applyProperties(state, mixedState, allProps);
                            mixedState = mixedState.with(property, true);
                            mixedState = mixedState.with(StepBlock.WATERLOGGED, state.get(StepBlock.WATERLOGGED));

                            world.setBlockState(pos, mixedState, 3);

                            BlockEntity be = world.getBlockEntity(pos);
                            if (be instanceof StepBlockEntity sbe) {
                                Identifier oldMaterial = Registries.BLOCK.getId(state.getBlock());
                                Identifier newMaterial = Registries.BLOCK.getId(itemBlock);

                                for (int i = 0; i < 4; i++) if (state.get(allProps[i])) sbe.setMaterial(i, oldMaterial);
                                for (int i = 0; i < 4; i++)
                                    if (allProps[i] == property) sbe.setMaterial(i, newMaterial);
                            }
                            consumeItem(player, stack, itemBlock, world, pos);
                        }
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                }
            } else if (state.getBlock() instanceof VerticalSlabBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof VerticalSlabBlock itemBlock) {
                if (state.getBlock() != itemBlock) {
                    if (!state.get(VerticalSlabBlock.TYPE).equals(SlabType.DOUBLE)) {
                        // Correct mixing check: Only allow if we hit the "inside" face of the slab or the open space
                        boolean success = false;
                        if (state.get(VerticalSlabBlock.FACING) == net.minecraft.util.math.Direction.NORTH) {
                            if (hitResult.getSide() == net.minecraft.util.math.Direction.NORTH || (hitResult.getPos().z - pos.getZ() > 0.5))
                                success = true;
                        } else if (state.get(VerticalSlabBlock.FACING) == net.minecraft.util.math.Direction.SOUTH) {
                            if (hitResult.getSide() == net.minecraft.util.math.Direction.SOUTH || (hitResult.getPos().z - pos.getZ() < 0.5))
                                success = true;
                        } else if (state.get(VerticalSlabBlock.FACING) == net.minecraft.util.math.Direction.WEST) {
                            if (hitResult.getSide() == net.minecraft.util.math.Direction.WEST || (hitResult.getPos().x - pos.getX() > 0.5))
                                success = true;
                        } else if (state.get(VerticalSlabBlock.FACING) == net.minecraft.util.math.Direction.EAST) {
                            if (hitResult.getSide() == net.minecraft.util.math.Direction.EAST || (hitResult.getPos().x - pos.getX() < 0.5))
                                success = true;
                        }

                        if (success) {
                            if (!world.isClient) {
                                // Convert to Mixed Vertical Slab
                                net.minecraft.util.math.Direction facing = state.get(VerticalSlabBlock.FACING);
                                net.minecraft.util.math.Direction.Axis axis = facing.getAxis();

                                BlockState mixedState = Reshaped.MIXED_VERTICAL_SLAB.getDefaultState().with(MixedVerticalSlabBlock.AXIS, axis);

                                BooleanProperty existingProp = MixedVerticalSlabBlock.getPropertyForDirection(facing, axis);
                                BooleanProperty newProp = (existingProp == MixedVerticalSlabBlock.NEGATIVE) ? MixedVerticalSlabBlock.POSITIVE : MixedVerticalSlabBlock.NEGATIVE;

                                mixedState = mixedState.with(existingProp, true).with(newProp, true);
                                mixedState = mixedState.with(VerticalSlabBlock.WATERLOGGED, state.get(VerticalSlabBlock.WATERLOGGED));

                                world.setBlockState(pos, mixedState, 3);

                                BlockEntity be = world.getBlockEntity(pos);
                                if (be instanceof VerticalSlabBlockEntity verticalSlabBlockEntity) {
                                    Identifier oldMaterial = Registries.BLOCK.getId(state.getBlock());
                                    Identifier newMaterial = Registries.BLOCK.getId(itemBlock);

                                    int existingIndex = (existingProp == MixedVerticalSlabBlock.NEGATIVE) ? 0 : 1;
                                    int newIndex = (newProp == MixedVerticalSlabBlock.NEGATIVE) ? 0 : 1;

                                    verticalSlabBlockEntity.setMaterial(existingIndex, oldMaterial);
                                    verticalSlabBlockEntity.setMaterial(newIndex, newMaterial);
                                }
                                consumeItem(player, stack, itemBlock, world, pos);
                            }
                            return net.minecraft.util.ActionResult.SUCCESS;
                        }
                    }
                }
            } else if (state.getBlock() instanceof SlabBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock itemBlock) {
                if (state.getBlock() != itemBlock) { // Only mix different slabs
                    SlabType type = state.get(Properties.SLAB_TYPE);
                    if (type != SlabType.DOUBLE) {
                        if (canMixSlab(hitResult, pos, type)) {
                            if (!world.isClient) {
                                BlockState mixedState = Reshaped.MIXED_SLAB.getDefaultState()
                                        .with(MixedSlabBlock.BOTTOM, true)
                                        .with(MixedSlabBlock.TOP, true)
                                        .with(Properties.WATERLOGGED, state.get(Properties.WATERLOGGED));

                                world.setBlockState(pos, mixedState, 3);

                                BlockEntity be = world.getBlockEntity(pos);
                                if (be instanceof SlabBlockEntity sbe) {
                                    Identifier oldMaterial = Registries.BLOCK.getId(state.getBlock());
                                    Identifier newMaterial = Registries.BLOCK.getId(itemBlock);

                                    if (type == SlabType.BOTTOM) {
                                        sbe.setMaterial(0, oldMaterial);
                                        sbe.setMaterial(1, newMaterial);
                                    } else {
                                        sbe.setMaterial(1, oldMaterial);
                                        sbe.setMaterial(0, newMaterial);
                                    }
                                }
                                consumeItem(player, stack, itemBlock, world, pos);
                            }
                            return net.minecraft.util.ActionResult.SUCCESS;
                        }
                    }
                }
            } else if (state.getBlock() instanceof MixedCornerBlock mixedBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof CornerBlock itemBlock) {
                // Check if we are adding a segment to an already Mixed Corner Block
                Vec3d localHit = getLocalHit(hitResult, pos);

                BooleanProperty property = mixedBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hitResult.getSide(), true);

                if (property != null && !state.get(property)) {
                    if (!world.isClient) {
                        // Update state
                        BlockState newState = state.with(property, true);
                        world.setBlockState(pos, newState, 3);

                        // Update BE
                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof CornerBlockEntity cbe) {
                            Identifier newMaterial = Registries.BLOCK.getId(itemBlock);
                            BooleanProperty[] allProps = net.f3rr3.reshaped.util.BlockSegmentUtils.CORNER_PROPERTIES;

                            for (int i = 0; i < 8; i++) {
                                if (allProps[i] == property) {
                                    cbe.setCornerMaterial(i, newMaterial);
                                    break;
                                }
                            }
                        }
                        consumeItem(player, stack, itemBlock, world, pos);
                    }
                    return net.minecraft.util.ActionResult.SUCCESS;
                }
            } else if (state.getBlock() instanceof MixedVerticalStepBlock mixedBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof VerticalStepBlock itemBlock) {
                // Mixed Vertical Step Interaction
                Vec3d localHit = getLocalHit(hitResult, pos);

                BooleanProperty property = mixedBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hitResult.getSide(), true);
                if (property != null && !state.get(property)) {
                    if (!world.isClient) {
                        BlockState newState = state.with(property, true);
                        world.setBlockState(pos, newState, 3);

                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof VerticalStepBlockEntity verticalStepBlockEntity) {
                            Identifier newMaterial = Registries.BLOCK.getId(itemBlock);
                            BooleanProperty[] allProps = net.f3rr3.reshaped.util.BlockSegmentUtils.VERTICAL_STEP_PROPERTIES;

                            for (int i = 0; i < 4; i++) {
                                if (allProps[i] == property) {
                                    verticalStepBlockEntity.setMaterial(i, newMaterial);
                                    break;
                                }
                            }
                        }
                        consumeItem(player, stack, itemBlock, world, pos);
                    }
                    return net.minecraft.util.ActionResult.SUCCESS;
                }
            } else if (state.getBlock() instanceof MixedStepBlock mixedBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof StepBlock itemBlock) {
                // Mixed Step Interaction
                // Removed strict axis check to allow adding onto mixed blocks regardless of item default state.
                // We enforce the existing block's axis.

                Vec3d localHit = getLocalHit(hitResult, pos);
                BooleanProperty property = mixedBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hitResult.getSide(), true, state);
                if (property != null && !state.get(property)) {
                    if (!world.isClient) {
                        BlockState newState = state.with(property, true);
                        world.setBlockState(pos, newState, 3);

                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof StepBlockEntity sbe) {
                            Identifier newMaterial = Registries.BLOCK.getId(itemBlock);
                            BooleanProperty[] allProps = net.f3rr3.reshaped.util.BlockSegmentUtils.STEP_PROPERTIES;

                            for (int i = 0; i < 4; i++) {
                                if (allProps[i] == property) {
                                    sbe.setMaterial(i, newMaterial);
                                    break;
                                }
                            }
                        }
                        consumeItem(player, stack, itemBlock, world, pos);
                    }
                    return net.minecraft.util.ActionResult.SUCCESS;
                }
            } else if (state.getBlock() instanceof MixedVerticalSlabBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof VerticalSlabBlock itemBlock) {
                // Do not derive axis/facing from itemBlock default state (always NORTH).
                // The target mixed block already defines its axis; resolve segment from hit.
                net.minecraft.util.math.Direction.Axis axis = state.get(MixedVerticalSlabBlock.AXIS);
                BooleanProperty propToSet = null;

                if (!state.get(MixedVerticalSlabBlock.NEGATIVE)) {
                    if (hitResult.getSide() == (axis == net.minecraft.util.math.Direction.Axis.Z ? net.minecraft.util.math.Direction.NORTH : net.minecraft.util.math.Direction.WEST) ||
                            (axis == net.minecraft.util.math.Direction.Axis.Z ? hitResult.getPos().z - pos.getZ() < 0.5 : hitResult.getPos().x - pos.getX() < 0.5)) {
                        propToSet = MixedVerticalSlabBlock.NEGATIVE;
                    }
                }
                if (propToSet == null && !state.get(MixedVerticalSlabBlock.POSITIVE)) {
                    if (hitResult.getSide() == (axis == net.minecraft.util.math.Direction.Axis.Z ? net.minecraft.util.math.Direction.SOUTH : net.minecraft.util.math.Direction.EAST) ||
                            (axis == net.minecraft.util.math.Direction.Axis.Z ? hitResult.getPos().z - pos.getZ() > 0.5 : hitResult.getPos().x - pos.getX() > 0.5)) {
                        propToSet = MixedVerticalSlabBlock.POSITIVE;
                    }
                }

                if (propToSet != null) {
                    if (!world.isClient) {
                        BlockState newState = state.with(propToSet, true);
                        world.setBlockState(pos, newState, 3);

                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof VerticalSlabBlockEntity verticalSlabBlockEntity) {
                            verticalSlabBlockEntity.setMaterial(propToSet == MixedVerticalSlabBlock.NEGATIVE ? 0 : 1, Registries.BLOCK.getId(itemBlock));
                        }
                        consumeItem(player, stack, itemBlock, world, pos);
                    }
                    return net.minecraft.util.ActionResult.SUCCESS;
                }
            } else if (state.getBlock() instanceof MixedSlabBlock && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock itemBlock) {
                BooleanProperty propToSet = null;

                if (state.get(MixedSlabBlock.BOTTOM) && !state.get(MixedSlabBlock.TOP)) {
                    if (hitResult.getSide() == net.minecraft.util.math.Direction.UP || (hitResult.getSide().getAxis().isHorizontal() && (hitResult.getPos().y - pos.getY() > 0.5))) {
                        propToSet = MixedSlabBlock.TOP;
                    }
                } else if (!state.get(MixedSlabBlock.BOTTOM) && state.get(MixedSlabBlock.TOP)) {
                    if (hitResult.getSide() == net.minecraft.util.math.Direction.DOWN || (hitResult.getSide().getAxis().isHorizontal() && (hitResult.getPos().y - pos.getY() < 0.5))) {
                        propToSet = MixedSlabBlock.BOTTOM;
                    }
                }

                if (propToSet != null) {
                    if (!world.isClient) {
                        BlockState newState = state.with(propToSet, true);
                        world.setBlockState(pos, newState, 3);

                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof SlabBlockEntity sbe) {
                            sbe.setMaterial(propToSet == MixedSlabBlock.BOTTOM ? 0 : 1, Registries.BLOCK.getId(itemBlock));
                        }
                        consumeItem(player, stack, itemBlock, world, pos);
                    }
                    return net.minecraft.util.ActionResult.SUCCESS;
                }
            }
            // Logic for MixedCornerBlock additions is handled in CornerBlock.getPlacementState/onPlaced
            // because there is no Block type change, just state update.

            return net.minecraft.util.ActionResult.PASS;
        });


        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof VerticalSlabBlock) {
                if (world.isClient) return true;

                ItemStack tool = player.getMainHandStack();
                if (!player.isCreative() && state.isToolRequired() && !tool.isSuitableFor(state)) {
                    return true;
                }

                HitResult hitResult = player.raycast(5.0, 1.0F, false);
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    if (blockHitResult.getBlockPos().equals(pos)) {
                        if (state.get(VerticalSlabBlock.TYPE) == SlabType.DOUBLE) {
                            Vec3d localHit = getLocalHit(blockHitResult, pos);
                            Direction.Axis axis = state.get(VerticalSlabBlock.FACING).getAxis();

                            Direction remainingFacing;
                            if (axis == Direction.Axis.Z) {
                                remainingFacing = localHit.z > 0.5 ? Direction.SOUTH : Direction.NORTH;
                            } else {
                                remainingFacing = localHit.x > 0.5 ? Direction.EAST : Direction.WEST;
                            }

                            BlockState newState = state
                                    .with(VerticalSlabBlock.TYPE, SlabType.BOTTOM)
                                    .with(VerticalSlabBlock.FACING, remainingFacing)
                                    .with(VerticalSlabBlock.WATERLOGGED, state.get(VerticalSlabBlock.WATERLOGGED));

                            world.setBlockState(pos, newState, 3);

                            if (!player.isCreative()) {
                                Block.dropStack(world, pos, new ItemStack(state.getBlock().asItem()));
                            }

                            syncChunkToWatchers(world, pos);

                            return false;
                        }
                    }
                }

                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);

                if (!player.isCreative()) {
                    int count = state.get(VerticalSlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
                    for (int i = 0; i < count; i++) {
                        Block.dropStack(world, pos, new ItemStack(state.getBlock().asItem()));
                    }
                }

                syncChunkToWatchers(world, pos);

                return false;
            }

            if (state.getBlock() instanceof VerticalStairsBlock) {
                if (world.isClient) return true;

                ItemStack tool = player.getMainHandStack();
                if (!player.isCreative() && state.isToolRequired() && !tool.isSuitableFor(state)) {
                    return true;
                }

                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);

                if (!player.isCreative()) {
                    Block.dropStack(world, pos, new ItemStack(state.getBlock().asItem()));
                }

                syncChunkToWatchers(world, pos);

                return false;
            }

            if (state.getBlock() instanceof CornerBlock || state.getBlock() instanceof MixedCornerBlock
                    || state.getBlock() instanceof StepBlock || state.getBlock() instanceof MixedStepBlock
                    || state.getBlock() instanceof VerticalStepBlock || state.getBlock() instanceof MixedVerticalStepBlock
                    || state.getBlock() instanceof MixedSlabBlock || state.getBlock() instanceof MixedVerticalSlabBlock) {
                // Client side prediction causes desync/invisibility because the client predicts "Air" before receiving updates.
                // We handle this on the Server by enforcing the Block State restoration and Delaying the BE update.
                if (world.isClient) return true;

                // Raycast to find which specific corner the player is looking at
                double reach = 5.0;
                HitResult hitResult = player.raycast(reach, 1.0F, false);

                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    if (blockHitResult.getBlockPos().equals(pos)) {
                        Vec3d localHit = getLocalHit(blockHitResult, pos);

                        BooleanProperty property = null;
                        if (state.getBlock() instanceof CornerBlock cb) {
                            property = cb.getPropertyFromHit(localHit.x, localHit.y, localHit.z, blockHitResult.getSide(), false);
                        } else if (state.getBlock() instanceof MixedCornerBlock mcb) {
                            property = mcb.getPropertyFromHit(localHit.x, localHit.y, localHit.z, blockHitResult.getSide(), false);
                        } else if (state.getBlock() instanceof StepBlock sb) {
                            property = sb.getPropertyFromHit(localHit.x, localHit.y, localHit.z, blockHitResult.getSide(), false, state);
                        } else if (state.getBlock() instanceof MixedStepBlock mixedStepBlock) {
                            property = mixedStepBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, blockHitResult.getSide(), false, state);
                        } else if (state.getBlock() instanceof VerticalStepBlock vsb) {
                            property = vsb.getPropertyFromHit(localHit.x, localHit.y, localHit.z, blockHitResult.getSide(), false);
                        } else if (state.getBlock() instanceof MixedVerticalStepBlock mixedVerticalStepBlock) {
                            property = mixedVerticalStepBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, blockHitResult.getSide(), false);
                        } else if (state.getBlock() instanceof MixedSlabBlock mixedSlabBlock) {
                            property = mixedSlabBlock.getPropertyFromHit(localHit.y, blockHitResult.getSide(), false);
                        } else if (state.getBlock() instanceof MixedVerticalSlabBlock mixedVerticalSlabBlock) {
                            property = mixedVerticalSlabBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, blockHitResult.getSide(), false, state);
                        }

                        if (property != null && state.get(property)) {
                            int count = 0;
                            BooleanProperty[] allProps;
                            allProps = getSegmentProperties(state.getBlock());
                            for (BooleanProperty p : allProps) {
                                if (state.get(p)) count++;
                            }

                            if (count > 1) {
                                Identifier materialId = null;

                                if (state.getBlock() instanceof MixedCornerBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof CornerBlockEntity cbe) {
                                        // 1. Capture current materials
                                        Identifier[] capturedMaterials = new Identifier[8];
                                        for (int i = 0; i < 8; i++) {
                                            capturedMaterials[i] = cbe.getCornerMaterial(i);
                                        }

                                        // 2. Check if we should revert to unmixed
                                        // Determine new state and remaining segments
                                        BlockState newState = state.with(property, false);
                                        MaterialAnalysis analysis = analyzeMaterials(newState, allProps, capturedMaterials);

                                        boolean converted = false;
                                        if (!analysis.isMixed() && analysis.commonMaterial() != null) {
                                            Block unmixedBlock = Registries.BLOCK.get(analysis.commonMaterial());
                                            if (unmixedBlock instanceof CornerBlock) {
                                                BlockState unmixedState = unmixedBlock.getDefaultState();
                                                unmixedState = applyProperties(newState, unmixedState, allProps);
                                                unmixedState = unmixedState.with(CornerBlock.WATERLOGGED, state.get(CornerBlock.WATERLOGGED));
                                                world.setBlockState(pos, unmixedState, 3);
                                                converted = true;
                                            }
                                        }

                                        if (!converted) {
                                            // 2. Update Block State (Restore block from potential client-side Air prediction)
                                            world.setBlockState(pos, newState, 3);

                                            // 3. Update Block Entity (Locally on Server)
                                            BlockEntity newBe = world.getBlockEntity(pos);
                                            if (newBe instanceof CornerBlockEntity newCbe) {
                                                for (int i = 0; i < 8; i++) {
                                                    if (allProps[i] == property) {
                                                        newCbe.setCornerMaterial(i, null);
                                                    } else {
                                                        Identifier target = capturedMaterials[i];
                                                        if (target != null) newCbe.setCornerMaterial(i, target);
                                                    }
                                                }
                                            }
                                        }

                                        // Extract materialID for drop from captured
                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else if (state.getBlock() instanceof MixedStepBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof StepBlockEntity sbe) {
                                        Identifier[] capturedMaterials = new Identifier[4];
                                        for (int i = 0; i < 4; i++) capturedMaterials[i] = sbe.getMaterial(i);

                                        // Check unmixed Logic
                                        BlockState newState = state.with(property, false);
                                        MaterialAnalysis analysis = analyzeMaterials(newState, allProps, capturedMaterials);

                                        boolean converted = false;
                                        if (!analysis.isMixed() && analysis.commonMaterial() != null) {
                                            Block unmixedBlock = Registries.BLOCK.get(analysis.commonMaterial());
                                            if (unmixedBlock instanceof StepBlock) {
                                                BlockState unmixedState = unmixedBlock.getDefaultState()
                                                        .with(StepBlock.AXIS, state.get(StepBlock.AXIS))
                                                        .with(StepBlock.WATERLOGGED, state.get(StepBlock.WATERLOGGED));

                                                unmixedState = applyProperties(newState, unmixedState, allProps);
                                                world.setBlockState(pos, unmixedState, 3);
                                                converted = true;
                                            }
                                        }

                                        if (!converted) {
                                            world.setBlockState(pos, newState, 3);
                                            BlockEntity newBe = world.getBlockEntity(pos);
                                            if (newBe instanceof StepBlockEntity newSbe) {
                                                for (int i = 0; i < 4; i++) {
                                                    if (allProps[i] == property) newSbe.setMaterial(i, null);
                                                    else if (capturedMaterials[i] != null)
                                                        newSbe.setMaterial(i, capturedMaterials[i]);
                                                }
                                            }
                                        }

                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else if (state.getBlock() instanceof MixedVerticalStepBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof VerticalStepBlockEntity verticalStepBlockEntity) {
                                        Identifier[] capturedMaterials = new Identifier[4];
                                        for (int i = 0; i < 4; i++)
                                            capturedMaterials[i] = verticalStepBlockEntity.getMaterial(i);

                                        BlockState newState = state.with(property, false);
                                        MaterialAnalysis analysis = analyzeMaterials(newState, allProps, capturedMaterials);

                                        boolean converted = false;
                                        if (!analysis.isMixed() && analysis.commonMaterial() != null) {
                                            Block unmixedBlock = Registries.BLOCK.get(analysis.commonMaterial());
                                            if (unmixedBlock instanceof VerticalStepBlock) {
                                                BlockState unmixedState = unmixedBlock.getDefaultState()
                                                        .with(VerticalStepBlock.WATERLOGGED, state.get(VerticalStepBlock.WATERLOGGED));

                                                unmixedState = applyProperties(newState, unmixedState, allProps);
                                                world.setBlockState(pos, unmixedState, 3);
                                                converted = true;
                                            }
                                        }

                                        if (!converted) {
                                            world.setBlockState(pos, newState, 3);
                                            BlockEntity newBe = world.getBlockEntity(pos);
                                            if (newBe instanceof VerticalStepBlockEntity newVerticalStepBlockEntity) {
                                                for (int i = 0; i < 4; i++) {
                                                    if (allProps[i] == property)
                                                        newVerticalStepBlockEntity.setMaterial(i, null);
                                                    else if (capturedMaterials[i] != null)
                                                        newVerticalStepBlockEntity.setMaterial(i, capturedMaterials[i]);
                                                }
                                            }
                                        }

                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else if (state.getBlock() instanceof MixedSlabBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof SlabBlockEntity sbe) {
                                        Identifier[] capturedMaterials = new Identifier[2];
                                        for (int i = 0; i < 2; i++) capturedMaterials[i] = sbe.getMaterial(i);

                                        // In MixedSlab, we only have 2 parts. If we are here, we had 2 parts (count > 1).
                                        // Removing 1 means 1 remains.
                                        // If one remains, it is BY DEFINITION "same material" (unique).

                                        BlockState newState = state.with(property, false);
                                        Identifier remainingMaterial = null;
                                        BooleanProperty remainingProp = null;

                                        for (int i = 0; i < 2; i++) {
                                            if (newState.get(allProps[i])) {
                                                remainingMaterial = capturedMaterials[i];
                                                remainingProp = allProps[i];
                                            }
                                        }

                                        boolean converted = false;
                                        if (remainingMaterial != null) {
                                            Block unmixedBlock = Registries.BLOCK.get(remainingMaterial);
                                            if (unmixedBlock instanceof SlabBlock) {
                                                BlockState unmixedState = unmixedBlock.getDefaultState()
                                                        .with(Properties.WATERLOGGED, state.get(Properties.WATERLOGGED));

                                                if (remainingProp == MixedSlabBlock.BOTTOM) {
                                                    unmixedState = unmixedState.with(Properties.SLAB_TYPE, SlabType.BOTTOM);
                                                } else {
                                                    unmixedState = unmixedState.with(Properties.SLAB_TYPE, SlabType.TOP);
                                                }

                                                world.setBlockState(pos, unmixedState, 3);
                                                converted = true;
                                            }
                                        }

                                        if (!converted) {
                                            world.setBlockState(pos, newState, 3);

                                            BlockEntity newBe = world.getBlockEntity(pos);
                                            if (newBe instanceof SlabBlockEntity newSbe) {
                                                for (int i = 0; i < 2; i++) {
                                                    if (allProps[i] == property) newSbe.setMaterial(i, null);
                                                    else if (capturedMaterials[i] != null)
                                                        newSbe.setMaterial(i, capturedMaterials[i]);
                                                }
                                            }
                                        }

                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else if (state.getBlock() instanceof MixedVerticalSlabBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof VerticalSlabBlockEntity verticalSlabBlockEntity) {
                                        Identifier[] capturedMaterials = new Identifier[2];
                                        for (int i = 0; i < 2; i++)
                                            capturedMaterials[i] = verticalSlabBlockEntity.getMaterial(i);

                                        BlockState newState = state.with(property, false);
                                        Identifier remainingMaterial = null;
                                        BooleanProperty remainingProp = null;

                                        for (int i = 0; i < 2; i++) {
                                            if (newState.get(allProps[i])) {
                                                remainingMaterial = capturedMaterials[i];
                                                remainingProp = allProps[i];
                                            }
                                        }

                                        boolean converted = false;
                                        if (remainingMaterial != null) {
                                            Block unmixedBlock = Registries.BLOCK.get(remainingMaterial);
                                            if (unmixedBlock instanceof VerticalSlabBlock) {
                                                BlockState unmixedState = unmixedBlock.getDefaultState()
                                                        .with(VerticalSlabBlock.WATERLOGGED, state.get(VerticalSlabBlock.WATERLOGGED));

                                                net.minecraft.util.math.Direction.Axis axis = state.get(MixedVerticalSlabBlock.AXIS);
                                                net.minecraft.util.math.Direction facing = axis == net.minecraft.util.math.Direction.Axis.Z
                                                        ? (remainingProp == MixedVerticalSlabBlock.NEGATIVE ? net.minecraft.util.math.Direction.NORTH : net.minecraft.util.math.Direction.SOUTH)
                                                        : (remainingProp == MixedVerticalSlabBlock.NEGATIVE ? net.minecraft.util.math.Direction.WEST : net.minecraft.util.math.Direction.EAST);

                                                unmixedState = unmixedState.with(VerticalSlabBlock.FACING, facing);
                                                world.setBlockState(pos, unmixedState, 3);
                                                converted = true;
                                            }
                                        }

                                        if (!converted) {
                                            world.setBlockState(pos, newState, 3);

                                            BlockEntity newBe = world.getBlockEntity(pos);
                                            if (newBe instanceof VerticalSlabBlockEntity newVerticalSlabBlockEntity) {
                                                for (int i = 0; i < 2; i++) {
                                                    if (allProps[i] == property)
                                                        newVerticalSlabBlockEntity.setMaterial(i, null);
                                                    else if (capturedMaterials[i] != null)
                                                        newVerticalSlabBlockEntity.setMaterial(i, capturedMaterials[i]);
                                                }
                                            }
                                        }

                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else {
                                    // Regular CornerBlock or StepBlock or VerticalStepBlock
                                    world.setBlockState(pos, state.with(property, false), 3);
                                }

                                // 4. FORCE Chunk Update to Sync Client
                                // This prevents visual desync where the block appears fully broken on the client.
                                syncChunkToWatchers(world, pos);

                                if (!player.isCreative()) {
                                    Block dropBlock = materialId != null ? Registries.BLOCK.get(materialId) : state.getBlock();
                                    Block.dropStack(world, pos, new ItemStack(dropBlock.asItem()));
                                }
                                return false; // Cancel the full block break
                            } else {
                                Identifier materialId = null;

                                if (state.getBlock() instanceof MixedCornerBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof CornerBlockEntity cbe) {
                                        Identifier[] capturedMaterials = new Identifier[8];
                                        for (int i = 0; i < 8; i++) {
                                            capturedMaterials[i] = cbe.getCornerMaterial(i);
                                        }
                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else if (state.getBlock() instanceof MixedStepBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof StepBlockEntity sbe) {
                                        Identifier[] capturedMaterials = new Identifier[4];
                                        for (int i = 0; i < 4; i++) capturedMaterials[i] = sbe.getMaterial(i);
                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else if (state.getBlock() instanceof MixedVerticalStepBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof VerticalStepBlockEntity verticalStepBlockEntity) {
                                        Identifier[] capturedMaterials = new Identifier[4];
                                        for (int i = 0; i < 4; i++)
                                            capturedMaterials[i] = verticalStepBlockEntity.getMaterial(i);
                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else if (state.getBlock() instanceof MixedSlabBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof SlabBlockEntity sbe) {
                                        Identifier[] capturedMaterials = new Identifier[2];
                                        for (int i = 0; i < 2; i++) capturedMaterials[i] = sbe.getMaterial(i);
                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else if (state.getBlock() instanceof MixedVerticalSlabBlock) {
                                    BlockEntity be = world.getBlockEntity(pos);
                                    if (be instanceof VerticalSlabBlockEntity verticalSlabBlockEntity) {
                                        Identifier[] capturedMaterials = new Identifier[2];
                                        for (int i = 0; i < 2; i++)
                                            capturedMaterials[i] = verticalSlabBlockEntity.getMaterial(i);
                                        materialId = materialForProperty(property, allProps, capturedMaterials);
                                    }
                                } else {
                                    materialId = Registries.BLOCK.getId(state.getBlock());
                                }

                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);

                                syncChunkToWatchers(world, pos);

                                if (!player.isCreative()) {
                                    Block dropBlock = materialId != null ? Registries.BLOCK.get(materialId) : state.getBlock();
                                    Block.dropStack(world, pos, new ItemStack(dropBlock.asItem()));
                                }
                                return false;
                            }
                        }
                    }
                }
            }
            return true;
        });
    }

    private record MaterialAnalysis(Identifier commonMaterial, boolean isMixed) {
    }
}

