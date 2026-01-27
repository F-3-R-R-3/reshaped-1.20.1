package net.f3rr3.reshaped;

import net.f3rr3.reshaped.block.CornerBlock;
import net.f3rr3.reshaped.block.MixedCornerBlock;
import net.f3rr3.reshaped.block.entity.CornerBlockEntity;
import net.f3rr3.reshaped.command.MatrixCommand;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.BlockRegistryScanner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
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
    public static BlockMatrix MATRIX;
    public static BlockEntityType<CornerBlockEntity> CORNER_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        LOGGER.info("Reshaping the world...");

        // Initialize matrix immediately
        MATRIX = new BlockMatrix();

        // Start reactive block scanning and registration
        BlockRegistryScanner.init(MATRIX);

        // Register Mixed Corner Block
        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "mixed_corner"), MIXED_CORNER);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "mixed_corner"), new net.minecraft.item.BlockItem(MIXED_CORNER, new net.minecraft.item.Item.Settings()));

        // Corner block entity registration will be finalized after scanning
        // But we register the type here. We will use a late-bind approach for blocks.
        CORNER_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "corner_block_entity"),
                FabricBlockEntityTypeBuilder.create(CornerBlockEntity::new, MIXED_CORNER).build(null)
        );
        CornerBlockEntity.TYPE = CORNER_BLOCK_ENTITY;

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
                    double hitX = hitResult.getPos().x - (double) pos.getX();
                    double hitY = hitResult.getPos().y - (double) pos.getY();
                    double hitZ = hitResult.getPos().z - (double) pos.getZ();

                    BooleanProperty property = cornerBlock.getPropertyFromHit(hitX, hitY, hitZ, hitResult.getSide(), true);

                    if (property != null && !state.get(property)) {
                        // We are placing a DIFFERENT material into an empty slot.
                        // Perform the transition logic manually.
                        if (!world.isClient) {
                            // 1. Calculate new state (Mixed)
                            net.minecraft.block.BlockState mixedState = Reshaped.MIXED_CORNER.getDefaultState();
                            BooleanProperty[] allProps = {CornerBlock.DOWN_NW, CornerBlock.DOWN_NE, CornerBlock.DOWN_SW, CornerBlock.DOWN_SE,
                                    CornerBlock.UP_NW, CornerBlock.UP_NE, CornerBlock.UP_SW, CornerBlock.UP_SE};

                            // 2. Set bits from old state
                            for (BooleanProperty p : allProps) {
                                if (state.get(p)) mixedState = mixedState.with(p, true);
                            }
                            // 3. Set bit for new placement
                            mixedState = mixedState.with(property, true);

                            // 4. Preserve waterlogging
                            mixedState = mixedState.with(CornerBlock.WATERLOGGED, state.get(CornerBlock.WATERLOGGED));

                            // 5. Update World State (this creates the new BE)
                            world.setBlockState(pos, mixedState, 3);

                            // 6. Populate BE
                            BlockEntity be = world.getBlockEntity(pos);
                            if (be instanceof CornerBlockEntity cbe) {
                                Identifier oldMaterial = Registries.BLOCK.getId(state.getBlock());
                                Identifier newMaterial = Registries.BLOCK.getId(itemBlock);

                                // Fill old segments
                                for (int i = 0; i < 8; i++) {
                                    if (state.get(allProps[i])) {
                                        cbe.setCornerMaterial(i, oldMaterial);
                                    }
                                }
                                // Fill new segment
                                for (int i = 0; i < 8; i++) {
                                    if (allProps[i] == property) {
                                        cbe.setCornerMaterial(i, newMaterial);
                                        break;
                                    }
                                }
                            }

                            // 7. Consume Item and Play Sound
                            if (!player.isCreative()) {
                                stack.decrement(1);
                            }
                            net.minecraft.sound.BlockSoundGroup sound = itemBlock.getSoundGroup(itemBlock.getDefaultState());
                            world.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sound.SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                        }
                        return net.minecraft.util.ActionResult.SUCCESS;
                    }
                }
            }
            // Logic for MixedCornerBlock additions is handled in CornerBlock.getPlacementState/onPlaced
            // because there is no Block type change, just state update.

            return net.minecraft.util.ActionResult.PASS;
        });

        // Handle corner block partial mining
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof CornerBlock || state.getBlock() instanceof MixedCornerBlock) {
                // Client side prediction causes desync/invisibility because the client predicts "Air" before receiving updates.
                // We handle this on the Server by enforcing the Block State restoration and Delaying the BE update.
                if (world.isClient) return true;

                // Raycast to find which specific corner the player is looking at
                double reach = 5.0;
                HitResult hitResult = player.raycast(reach, 1.0F, false);

                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                    if (blockHitResult.getBlockPos().equals(pos)) {
                        double hitX = blockHitResult.getPos().x - (double) pos.getX();
                        double hitY = blockHitResult.getPos().y - (double) pos.getY();
                        double hitZ = blockHitResult.getPos().z - (double) pos.getZ();

                        BooleanProperty property = null;
                        if (state.getBlock() instanceof CornerBlock cb) {
                            property = cb.getPropertyFromHit(hitX, hitY, hitZ, blockHitResult.getSide(), false);
                        } else if (state.getBlock() instanceof MixedCornerBlock mcb) {
                            property = mcb.getPropertyFromHit(hitX, hitY, hitZ, blockHitResult.getSide(), false);
                        }

                        if (property != null && state.get(property)) {
                            int count = 0;
                            BooleanProperty[] allProps = {CornerBlock.DOWN_NW, CornerBlock.DOWN_NE, CornerBlock.DOWN_SW, CornerBlock.DOWN_SE,
                                    CornerBlock.UP_NW, CornerBlock.UP_NE, CornerBlock.UP_SW, CornerBlock.UP_SE};
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

                                        // 2. Update Block State (Restore block from potential client-side Air prediction)
                                        world.setBlockState(pos, state.with(property, false), 3);

                                        // 3. Update Block Entity (Locally on Server)
                                        // We do this immediately.
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

                                        // 4. FORCE Chunk Update to Sync Client
                                        // The Delayed Packet approach failed (potentially due to race conditions or packet loss during block restoration).
                                        // We now force a full Chunk Data packet to ensure the Client has the absolute truth (Block + BE).
                                        if (world.getServer() != null) {
                                            WorldChunk chunk = world.getWorldChunk(pos);
                                            if (chunk != null) {
                                                ((ServerChunkManager) world.getChunkManager()).threadedAnvilChunkStorage.getPlayersWatchingChunk(chunk.getPos(), false).forEach(serverPlayer -> serverPlayer.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null)));
                                            }

                                            // Extract materialID for drop from captured
                                            for (int k = 0; k < 8; k++) {
                                                if (allProps[k] == property) materialId = capturedMaterials[k];
                                            }
                                        }
                                    }
                                } else {
                                    world.setBlockState(pos, state.with(property, false), 3);
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
}