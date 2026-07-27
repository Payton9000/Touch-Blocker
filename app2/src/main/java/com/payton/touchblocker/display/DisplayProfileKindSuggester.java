package com.payton.touchblocker.display;

import com.payton.touchblocker.profile.ProfileKind;

public final class DisplayProfileKindSuggester {
    private DisplayProfileKindSuggester() {
    }

    public static ProfileKind suggest(FoldFeatureData foldFeature) {
        if (foldFeature == null || !foldFeature.isSeparating()) {
            return null;
        }
        return ProfileKind.INNER;
    }
}
