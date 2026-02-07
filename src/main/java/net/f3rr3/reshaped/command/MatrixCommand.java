package net.f3rr3.reshaped.command;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Corner.CornerBlock;
import net.f3rr3.reshaped.block.Step.StepBlock;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

public class MatrixCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("place_matrix")
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

                    for (Map.Entry<Block, List<Block>> entry : matrixMap.entrySet()) {
                        Block base = entry.getKey();
                        List<Block> variants = entry.getValue();

                        // X offset for each column, Z offset is 0
                        BlockPos colRoot = startPos.add(columnIdx * 2, 0, 0);

                        // Row 1: Base Block
                        context.getSource().getWorld().setBlockState(colRoot, base.getDefaultState());

                        // Successive rows: Variants
                        for (int i = 0; i < variants.size(); i++) {
                            BlockPos variantPos = colRoot.up(i + 1);
                            Block variant = variants.get(i);
                            net.minecraft.block.BlockState state = variant.getDefaultState();

                            if (variant instanceof CornerBlock) {
                                state = state.with(CornerBlock.DOWN_NE, true).with(CornerBlock.UP_SW, true);
                            }
                            if (variant instanceof StepBlock) {
                                state = state.with(StepBlock.DOWN_BACK, true).with(StepBlock.UP_FRONT, true);
                            }

                            context.getSource().getWorld().setBlockState(variantPos, state);
                        }

                        columnIdx++;
                    }

                    int totalColumns = matrixMap.size();
                    context.getSource().sendFeedback(() -> Text.literal("Placed matrix with " + totalColumns + " columns!"), true);
                    return 1;
                })));
    }
}
