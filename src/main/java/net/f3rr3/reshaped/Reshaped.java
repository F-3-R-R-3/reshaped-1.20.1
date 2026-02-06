package net.f3rr3.reshaped;

import net.f3rr3.reshaped.block.*;
import net.f3rr3.reshaped.block.entity.*;
import net.f3rr3.reshaped.command.MatrixCommand;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.BlockRegistryScanner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
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
        MatrixCommand.register();

        // Register network receivers
        NetworkHandler.registerServerReceivers();

        // Handle CornerBlock -> MixedCornerBlock transition manually to preserve data
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
                             BlockState mixedState = MIXED_VERTICAL_STEP.getDefaultState();
                             BooleanProperty[] allProps = net.f3rr3.reshaped.util.BlockSegmentUtils.VERTICAL_STEP_PROPERTIES;
                             mixedState = applyProperties(state, mixedState, allProps);
                             mixedState = mixedState.with(property, true);
                             mixedState = mixedState.with(VerticalStepBlock.WATERLOGGED, state.get(VerticalStepBlock.WATERLOGGED));
                             
                             world.setBlockState(pos, mixedState, 3);
                             
                             BlockEntity be = world.getBlockEntity(pos);
                             if (be instanceof VerticalStepBlockEntity verticalStepBlockEntity) {
                                 Identifier oldMaterial = Registries.BLOCK.getId(state.getBlock());
                                 Identifier newMaterial = Registries.BLOCK.getId(itemBlock);
                                 
                                 for (int i = 0; i < 4; i++) if (state.get(allProps[i])) verticalStepBlockEntity.setMaterial(i, oldMaterial);
                                 for (int i = 0; i < 4; i++) if (allProps[i] == property) verticalStepBlockEntity.setMaterial(i, newMaterial);
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
                             BlockState mixedState = MIXED_STEP.getDefaultState().with(StepBlock.AXIS, state.get(StepBlock.AXIS));
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
                                 for (int i = 0; i < 4; i++) if (allProps[i] == property) sbe.setMaterial(i, newMaterial);
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
                              if (hitResult.getSide() == net.minecraft.util.math.Direction.NORTH || (hitResult.getPos().z - pos.getZ() > 0.5)) success = true;
                          } else if (state.get(VerticalSlabBlock.FACING) == net.minecraft.util.math.Direction.SOUTH) {
                              if (hitResult.getSide() == net.minecraft.util.math.Direction.SOUTH || (hitResult.getPos().z - pos.getZ() < 0.5)) success = true;
                          } else if (state.get(VerticalSlabBlock.FACING) == net.minecraft.util.math.Direction.WEST) {
                              if (hitResult.getSide() == net.minecraft.util.math.Direction.WEST || (hitResult.getPos().x - pos.getX() > 0.5)) success = true;
                          } else if (state.get(VerticalSlabBlock.FACING) == net.minecraft.util.math.Direction.EAST) {
                              if (hitResult.getSide() == net.minecraft.util.math.Direction.EAST || (hitResult.getPos().x - pos.getX() < 0.5)) success = true;
                          }
                         
                         if (success) {
                              if (!world.isClient) {
                                  // Convert to Mixed Vertical Slab
                                  net.minecraft.util.math.Direction facing = state.get(VerticalSlabBlock.FACING);
                                  net.minecraft.util.math.Direction.Axis axis = facing.getAxis();
                                  
                                  BlockState mixedState = MIXED_VERTICAL_SLAB.getDefaultState().with(MixedVerticalSlabBlock.AXIS, axis);
                                  
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
                                 BlockState mixedState = MIXED_SLAB.getDefaultState()
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
                 net.minecraft.util.math.Direction facing = itemBlock.getDefaultState().get(VerticalSlabBlock.FACING);
                 net.minecraft.util.math.Direction.Axis axis = state.get(MixedVerticalSlabBlock.AXIS);
                 
                 if (facing.getAxis() == axis) {
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

        // Handle corner block partial mining
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
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
                                                        else if (capturedMaterials[i] != null) newSbe.setMaterial(i, capturedMaterials[i]);
                                                    }
                                                }
                                            }
                                            
                                            materialId = materialForProperty(property, allProps, capturedMaterials);
                                        }
                                    } else if (state.getBlock() instanceof MixedVerticalStepBlock) {
                                        BlockEntity be = world.getBlockEntity(pos);
                                        if (be instanceof VerticalStepBlockEntity verticalStepBlockEntity) {
                                            Identifier[] capturedMaterials = new Identifier[4];
                                            for (int i = 0; i < 4; i++) capturedMaterials[i] = verticalStepBlockEntity.getMaterial(i);
                                            
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
                                                        if (allProps[i] == property) newVerticalStepBlockEntity.setMaterial(i, null);
                                                        else if (capturedMaterials[i] != null) newVerticalStepBlockEntity.setMaterial(i, capturedMaterials[i]);
                                                    }
                                                }
                                            }
                                            
                                            materialId = materialForProperty(property, allProps, capturedMaterials);
                                        }
                                    } else if (state.getBlock() instanceof MixedSlabBlock) {
                                     BlockEntity be = world.getBlockEntity(pos);
                                     if (be instanceof SlabBlockEntity sbe) {
                                         Identifier[] capturedMaterials = new Identifier[2];
                                         for(int i=0; i<2; i++) capturedMaterials[i] = sbe.getMaterial(i);
                                         
                                         // In MixedSlab, we only have 2 parts. If we are here, we had 2 parts (count > 1).
                                         // Removing 1 means 1 remains.
                                         // If one remains, it is BY DEFINITION "same material" (unique).
                                         
                                         BlockState newState = state.with(property, false);
                                         Identifier remainingMaterial = null;
                                         BooleanProperty remainingProp = null;
                                         
                                         for(int i=0; i<2; i++) {
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
                                                 for(int i=0; i<2; i++) {
                                                     if (allProps[i] == property) newSbe.setMaterial(i, null);
                                                     else if (capturedMaterials[i] != null) newSbe.setMaterial(i, capturedMaterials[i]);
                                                 }
                                             }
                                         }
                                         
                                         materialId = materialForProperty(property, allProps, capturedMaterials);
                                     }
                                } else if (state.getBlock() instanceof MixedVerticalSlabBlock) {
                                     BlockEntity be = world.getBlockEntity(pos);
                                     if (be instanceof VerticalSlabBlockEntity verticalSlabBlockEntity) {
                                         Identifier[] capturedMaterials = new Identifier[2];
                                         for(int i=0; i<2; i++) capturedMaterials[i] = verticalSlabBlockEntity.getMaterial(i);
                                         
                                         BlockState newState = state.with(property, false);
                                         Identifier remainingMaterial = null;
                                         BooleanProperty remainingProp = null;
                                         
                                         for(int i=0; i<2; i++) {
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
                                                 for(int i=0; i<2; i++) {
                                                     if (allProps[i] == property) newVerticalSlabBlockEntity.setMaterial(i, null);
                                                     else if (capturedMaterials[i] != null) newVerticalSlabBlockEntity.setMaterial(i, capturedMaterials[i]);
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
                                if (world.getServer() != null) {
                                    WorldChunk chunk = world.getWorldChunk(pos);
                                    if (chunk != null) {
                                        ((ServerChunkManager) world.getChunkManager()).threadedAnvilChunkStorage.getPlayersWatchingChunk(chunk.getPos(), false).forEach(serverPlayer -> serverPlayer.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null)));
                                    }
                                }

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
            return new BooleanProperty[] {MixedSlabBlock.BOTTOM, MixedSlabBlock.TOP};
        }
        return new BooleanProperty[] {MixedVerticalSlabBlock.NEGATIVE, MixedVerticalSlabBlock.POSITIVE};
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

    private record MaterialAnalysis(Identifier commonMaterial, boolean isMixed) {
    }
}
