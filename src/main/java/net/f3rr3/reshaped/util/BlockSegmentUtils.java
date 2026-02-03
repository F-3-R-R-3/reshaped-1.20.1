package net.f3rr3.reshaped.util;

import net.minecraft.util.math.Direction;

/**
 * Shared utility methods for segment-based blocks (Steps, Vertical Steps, Corners).
 */
public class BlockSegmentUtils {

    /**
     * Common hit detection logic for quadrant-based blocks.
     * Extracts logic used in VerticalStepBlock and CornerBlock.
     */
    public static Quadrant getQuadrantFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement, boolean checkY) {
        double offset = isPlacement ? 0.05 : -0.05;
        double testX = hitX + side.getOffsetX() * offset;
        double testY = hitY + side.getOffsetY() * offset;
        double testZ = hitZ + side.getOffsetZ() * offset;

        testX = Math.max(0.001, Math.min(0.999, testX));
        testY = Math.max(0.001, Math.min(0.999, testY));
        testZ = Math.max(0.001, Math.min(0.999, testZ));

        boolean isUp = testY > 0.5;
        boolean isNorth = testZ < 0.5;
        boolean isWest = testX < 0.5;

        return new Quadrant(isUp, isNorth, isWest);
    }

    public record Quadrant(boolean isUp, boolean isNorth, boolean isWest) {
        public boolean isPlusX() { return !isWest; }
        public boolean isPlusZ() { return !isNorth; }
    }
}
