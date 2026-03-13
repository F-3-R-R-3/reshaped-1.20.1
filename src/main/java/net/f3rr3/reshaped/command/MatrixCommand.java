package net.f3rr3.reshaped.command;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Corner.CornerBlock;
import net.f3rr3.reshaped.block.Step.StepBlock;
import net.f3rr3.reshaped.block.Template.MixedBlockEntity;
import net.f3rr3.reshaped.block.VerticalStep.VerticalStepBlock;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MatrixCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("reshaped")
                .then(CommandManager.literal("place_all")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            if (Reshaped.MATRIX == null) {
                                context.getSource().sendError(Text.literal("Matrix is not initialized!"));
                                return 0;
                            }

                            BlockPos startPos = player.getBlockPos().add(2, 0, 0);
                            Map<Block, List<Block>> matrixMap = Reshaped.MATRIX.getMatrix();
                            int columnIdx = 0;

                            Reshaped.LOGGER.info("Executing /reshaped place_all with {} matrix columns.", matrixMap.size());
                            for (Map.Entry<Block, List<Block>> entry : matrixMap.entrySet()) {
                                Block base = entry.getKey();
                                List<Block> variants = entry.getValue();
                                String baseId = Registries.BLOCK.getId(base).toString();
                                String variantIds = variants.stream()
                                        .map(variant -> Registries.BLOCK.getId(variant).toString())
                                        .collect(Collectors.joining(", "));
                                Reshaped.LOGGER.info(
                                        "place_all column {} -> base: {}, variants({}): [{}]",
                                        columnIdx,
                                        baseId,
                                        variants.size(),
                                        variantIds
                                );

                                // X offset for each column, Z offset is 0
                                BlockPos colRoot = startPos.add(columnIdx * 2, 0, 0);

                                // Row 1: Base Block
                                context.getSource().getWorld().setBlockState(colRoot, base.getDefaultState());

                                // Successive rows: Variants (some blocks, e.g. doors, take 2 rows)
                                int yOffset = 1;
                                for (Block variant : variants) {
                                    BlockPos variantPos = colRoot.up(yOffset * 2);
                                    BlockState state = variant.getDefaultState();

                                    if (variant instanceof CornerBlock) {
                                        state = state.with(CornerBlock.DOWN_NE, true).with(CornerBlock.UP_SW, true);
                                    }
                                    if (variant instanceof StepBlock) {
                                        state = state.with(StepBlock.DOWN_BACK, true).with(StepBlock.UP_FRONT, true);
                                    }
                                    if (variant instanceof VerticalStepBlock) {
                                        state = state.with(VerticalStepBlock.NORTH_WEST, true).with(VerticalStepBlock.SOUTH_EAST, true);
                                    }

                                    if (variant instanceof DoorBlock && state.contains(DoorBlock.HALF)) {
                                        BlockState lower = state.with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
                                        BlockState upper = state.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                                        context.getSource().getWorld().setBlockState(variantPos, lower);
                                        context.getSource().getWorld().setBlockState(variantPos.up(), upper);
                                        yOffset += 2;
                                    } else {
                                        context.getSource().getWorld().setBlockState(variantPos, state);
                                        yOffset += 1;
                                    }
                                }

                                columnIdx++;
                            }

                            int totalColumns = matrixMap.size();
                            context.getSource().sendFeedback(() -> Text.literal("Placed matrix with " + totalColumns + " columns!"), true);
                            return 1;
                        }))
                .then(CommandManager.literal("getdata")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;

                            HitResult hitResult = player.raycast(5.0, 1.0F, false);
                            if (hitResult.getType() != HitResult.Type.BLOCK) {
                                context.getSource().sendError(Text.literal("No block targeted."));
                                return 0;
                            }

                            BlockPos targetPos = ((BlockHitResult) hitResult).getBlockPos();
                            BlockEntity blockEntity = context.getSource().getWorld().getBlockEntity(targetPos);
                            if (!(blockEntity instanceof MixedBlockEntity mixedBlockEntity)) {
                                context.getSource().sendError(Text.literal("Targeted block is not a reshaped block entity."));
                                return 0;
                            }

                            NbtCompound nbt = mixedBlockEntity.createNbt();
                            String blockId = Registries.BLOCK.getId(blockEntity.getCachedState().getBlock()).toString();
                            String blockEntityTypeId = Objects.requireNonNull(Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType())).toString();

                            context.getSource().sendFeedback(() -> Text.literal("Server data for " + targetPos.toShortString() + ":"), false);
                            context.getSource().sendFeedback(() -> Text.literal("Server Block: " + blockId), false);
                            context.getSource().sendFeedback(() -> Text.literal("Server BlockEntityType: " + blockEntityTypeId), false);
                            context.getSource().sendFeedback(() -> Text.literal("Server NBT: " + nbt), false);

                            NetworkHandler.requestClientBlockData(player, targetPos);
                            context.getSource().sendFeedback(() -> Text.literal("Requested client data..."), false);
                            return 1;
                        }))));
    }
}
