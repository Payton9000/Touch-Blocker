package com.payton.touchblocker.display;

public final class IntRect {
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public IntRect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    public boolean contains(int x, int y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public boolean intersects(IntRect other) {
        if (other == null) {
            throw new NullPointerException("other == null");
        }
        return left < right
                && top < bottom
                && other.left < other.right
                && other.top < other.bottom
                && left < other.right
                && right > other.left
                && top < other.bottom
                && bottom > other.top;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IntRect)) {
            return false;
        }
        IntRect that = (IntRect) other;
        return left == that.left
                && top == that.top
                && right == that.right
                && bottom == that.bottom;
    }

    @Override
    public int hashCode() {
        int result = left;
        result = 31 * result + top;
        result = 31 * result + right;
        result = 31 * result + bottom;
        return result;
    }
}
