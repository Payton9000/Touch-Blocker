package com.payton.touchblocker.geometry;

import com.payton.touchblocker.OverlayWindowGeometry;
import com.payton.touchblocker.display.IntRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Groups blocking-point squares that overlap or sit within {@link #MERGE_GAP_DP} of each
 * other into shared overlay windows, so the overlay service can open one window per
 * cluster instead of one window per point.
 *
 * <p>A cluster of two or more squares only collapses into a single window when its
 * bounding box does not waste more than {@link #MAX_WASTE_RATIO} times the area actually
 * covered by its member squares. Otherwise the cluster falls back to one window per
 * member, so a chain of transitively-adjacent-but-far-apart points cannot balloon into
 * one large window that would block unrelated touches in the empty space between them.
 */
public final class OverlayClusterPlanner {
    /** Squares within this many dp of each other (edge to edge) are treated as adjacent. */
    public static final float MERGE_GAP_DP = 4f;

    /** Maximum allowed ratio of a cluster's bounding-box area to its covered area. */
    public static final float MAX_WASTE_RATIO = 1.25f;

    private OverlayClusterPlanner() {
    }

    public static List<WindowPlan> plan(List<PlannedCircle> circles, float density) {
        if (circles.isEmpty()) {
            return Collections.emptyList();
        }

        int count = circles.size();
        IntRect[] squares = new IntRect[count];
        for (int i = 0; i < count; i++) {
            squares[i] = circles.get(i).squareBounds();
        }

        int[] parent = new int[count];
        for (int i = 0; i < count; i++) {
            parent[i] = i;
        }
        int gapPx = Math.round(MERGE_GAP_DP * density);
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (adjacent(squares[i], squares[j], gapPx)) {
                    union(parent, i, j);
                }
            }
        }

        List<WindowPlan> plans = new ArrayList<>();
        boolean[] rootProcessed = new boolean[count];
        for (int i = 0; i < count; i++) {
            int root = find(parent, i);
            if (rootProcessed[root]) {
                continue;
            }
            rootProcessed[root] = true;

            List<Integer> memberIndices = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                if (find(parent, j) == root) {
                    memberIndices.add(j);
                }
            }
            plans.addAll(planForCluster(circles, squares, memberIndices));
        }

        Collections.sort(plans, new Comparator<WindowPlan>() {
            @Override
            public int compare(WindowPlan left, WindowPlan right) {
                return Integer.compare(firstPointId(left), firstPointId(right));
            }
        });
        return plans;
    }

    private static int firstPointId(WindowPlan plan) {
        return plan.getMembers().get(0).getPointId();
    }

    /**
     * Builds the window plan(s) for one connected cluster: a single merged window when the
     * cluster's bounding box is not too wasteful, otherwise one window per member square.
     */
    private static List<WindowPlan> planForCluster(
            List<PlannedCircle> circles, IntRect[] squares, List<Integer> memberIndices) {
        int memberCount = memberIndices.size();
        List<PlannedCircle> members = new ArrayList<>(memberCount);
        IntRect[] memberSquares = new IntRect[memberCount];
        for (int i = 0; i < memberCount; i++) {
            int index = memberIndices.get(i);
            members.add(circles.get(index));
            memberSquares[i] = squares[index];
        }
        Collections.sort(members, new Comparator<PlannedCircle>() {
            @Override
            public int compare(PlannedCircle left, PlannedCircle right) {
                return Integer.compare(left.getPointId(), right.getPointId());
            }
        });

        IntRect bbox = boundingBox(memberSquares);
        List<WindowPlan> plans = new ArrayList<>(1);
        if (memberCount == 1 || !isWasteful(bbox, memberSquares)) {
            plans.add(new WindowPlan(bbox, members));
        } else {
            for (PlannedCircle member : members) {
                plans.add(new WindowPlan(member.squareBounds(), Collections.singletonList(member)));
            }
        }
        return plans;
    }

    private static boolean isWasteful(IntRect bbox, IntRect[] memberSquares) {
        long bboxArea = (long) bbox.width() * (long) bbox.height();
        long unionArea = unionArea(memberSquares);
        return bboxArea > MAX_WASTE_RATIO * (double) unionArea;
    }

    /**
     * Two squares are adjacent when one, inflated by {@code gapPx} on every side, intersects
     * or touches the other. This intentionally does not use {@link IntRect#intersects}, which
     * treats touching edges (e.g. a.right == b.left) as non-intersecting.
     */
    private static boolean adjacent(IntRect a, IntRect b, int gapPx) {
        return a.getLeft() - gapPx < b.getRight()
                && a.getRight() + gapPx > b.getLeft()
                && a.getTop() - gapPx < b.getBottom()
                && a.getBottom() + gapPx > b.getTop();
    }

    private static int find(int[] parent, int index) {
        int root = index;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[index] != root) {
            int next = parent[index];
            parent[index] = root;
            index = next;
        }
        return root;
    }

    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[rootB] = rootA;
        }
    }

    private static IntRect boundingBox(IntRect[] squares) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (IntRect square : squares) {
            left = Math.min(left, square.getLeft());
            top = Math.min(top, square.getTop());
            right = Math.max(right, square.getRight());
            bottom = Math.max(bottom, square.getBottom());
        }
        return new IntRect(left, top, right, bottom);
    }

    /**
     * Computes the union area of the given squares by coordinate compression: the distinct x
     * and y edges of all squares slice the plane into a grid of cells that are each either
     * fully inside or fully outside every square, so summing the area of the covered cells
     * gives the exact union area (unlike summing per-square areas, which double-counts
     * overlaps).
     */
    private static long unionArea(IntRect[] squares) {
        int[] xs = new int[squares.length * 2];
        int[] ys = new int[squares.length * 2];
        for (int i = 0; i < squares.length; i++) {
            xs[i * 2] = squares[i].getLeft();
            xs[i * 2 + 1] = squares[i].getRight();
            ys[i * 2] = squares[i].getTop();
            ys[i * 2 + 1] = squares[i].getBottom();
        }
        int[] sortedXs = distinctSorted(xs);
        int[] sortedYs = distinctSorted(ys);

        long area = 0;
        for (int xi = 0; xi < sortedXs.length - 1; xi++) {
            int cellLeft = sortedXs[xi];
            int cellRight = sortedXs[xi + 1];
            for (int yi = 0; yi < sortedYs.length - 1; yi++) {
                int cellTop = sortedYs[yi];
                int cellBottom = sortedYs[yi + 1];
                if (coveredByAny(squares, cellLeft, cellTop, cellRight, cellBottom)) {
                    area += (long) (cellRight - cellLeft) * (cellBottom - cellTop);
                }
            }
        }
        return area;
    }

    private static boolean coveredByAny(
            IntRect[] squares, int left, int top, int right, int bottom) {
        for (IntRect square : squares) {
            if (left >= square.getLeft()
                    && right <= square.getRight()
                    && top >= square.getTop()
                    && bottom <= square.getBottom()) {
                return true;
            }
        }
        return false;
    }

    private static int[] distinctSorted(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int uniqueCount = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[uniqueCount - 1]) {
                sorted[uniqueCount] = sorted[i];
                uniqueCount++;
            }
        }
        return Arrays.copyOf(sorted, uniqueCount);
    }

    /** One blocking-point circle being placed onto the overlay, in resolved screen pixels. */
    public static final class PlannedCircle {
        private final int pointId;
        private final float centerX;
        private final float centerY;
        private final float diameterPx;

        public PlannedCircle(int pointId, float centerX, float centerY, float diameterPx) {
            this.pointId = pointId;
            this.centerX = centerX;
            this.centerY = centerY;
            this.diameterPx = diameterPx;
        }

        public int getPointId() {
            return pointId;
        }

        public float getCenterX() {
            return centerX;
        }

        public float getCenterY() {
            return centerY;
        }

        public float getDiameterPx() {
            return diameterPx;
        }

        /** The square overlay window centered on this circle, sized to its diameter. */
        public IntRect squareBounds() {
            int sizePx = Math.max(1, Math.round(diameterPx));
            return OverlayWindowGeometry.centeredAt(centerX, centerY, sizePx).getBounds();
        }
    }

    /** One overlay window to create, covering the bounding box of its member circles. */
    public static final class WindowPlan {
        private final IntRect bounds;
        private final List<PlannedCircle> members;

        private WindowPlan(IntRect bounds, List<PlannedCircle> members) {
            this.bounds = bounds;
            this.members = Collections.unmodifiableList(new ArrayList<>(members));
        }

        /** The union bounding box of all member squares; the window's screen position and size. */
        public IntRect getBounds() {
            return bounds;
        }

        /** Member circles to draw inside this window, sorted by point id. */
        public List<PlannedCircle> getMembers() {
            return members;
        }

        /** Stable identity for this window: member point ids sorted and joined with ','. */
        public String key() {
            StringBuilder key = new StringBuilder();
            for (int i = 0; i < members.size(); i++) {
                if (i > 0) {
                    key.append(',');
                }
                key.append(members.get(i).getPointId());
            }
            return key.toString();
        }
    }
}
