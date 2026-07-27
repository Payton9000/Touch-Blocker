package com.payton.touchblocker.display;

import com.payton.touchblocker.profile.ProfileKind;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DisplayProfileKindSuggesterTest {
    @Test
    public void unknownOrNonSeparatingGeometryDoesNotGuessOuterProfile() {
        assertNull(DisplayProfileKindSuggester.suggest(null));
        assertNull(DisplayProfileKindSuggester.suggest(new FoldFeatureData(
                new IntRect(500, 0, 500, 1800), false, false)));
    }

    @Test
    public void separatingFoldIsReliableInnerDisplayEvidence() {
        assertEquals(ProfileKind.INNER, DisplayProfileKindSuggester.suggest(
                new FoldFeatureData(new IntRect(500, 0, 520, 1800), true, true)));
    }
}
