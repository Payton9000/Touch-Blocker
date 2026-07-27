package com.payton.touchblocker.display;

public final class DisplayStableKeyFactory {
    private DisplayStableKeyFactory() {
    }

    public static String create(
            String uniqueId,
            int naturalWidth,
            int naturalHeight,
            int maximumWidth,
            int maximumHeight,
            int densityDpi,
            String role
    ) {
        String normalizedUniqueId = uniqueId == null || uniqueId.length() == 0
                ? "unknown"
                : uniqueId;
        String normalizedRole = role == null || role.length() == 0 ? "unknown" : role;
        return normalizedUniqueId
                + "|natural=" + dimensions(naturalWidth, naturalHeight)
                + "|max=" + dimensions(maximumWidth, maximumHeight)
                + "|dpi=" + densityDpi
                + "|role=" + normalizedRole;
    }

    private static String dimensions(int width, int height) {
        return Math.min(width, height) + "x" + Math.max(width, height);
    }
}
