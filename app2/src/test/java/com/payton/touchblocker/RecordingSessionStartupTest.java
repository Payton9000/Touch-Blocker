package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.profile.ActiveProfiles;
import com.payton.touchblocker.profile.ProfileDocument;
import com.payton.touchblocker.profile.ProfileKind;

import org.junit.Test;

public class RecordingSessionStartupTest {
    @Test
    public void layoutCallbackBeforeTimeoutWinsInitializationRace() {
        RecordingSessionStartup startup = new RecordingSessionStartup();

        assertTrue(startup.beginFromLayoutCallback());
        assertFalse(startup.beginFromTimeout());
    }

    @Test
    public void timeoutBeforeLayoutCallbackWinsInitializationRace() {
        RecordingSessionStartup startup = new RecordingSessionStartup();

        assertTrue(startup.beginFromTimeout());
        assertFalse(startup.beginFromLayoutCallback());
    }

    @Test
    public void cancellationPreventsBothPendingInitializationPathsUntilNextStart() {
        RecordingSessionStartup startup = new RecordingSessionStartup();

        startup.cancelPendingInitialization();

        assertFalse(startup.beginFromLayoutCallback());
        assertFalse(startup.beginFromTimeout());
        assertTrue(startup.resumeAfterLifecycleStart());
        assertTrue(startup.beginFromTimeout());
    }

    @Test
    public void firstLayoutSnapshotSelectsInnerProfileAndPreventsLaterReselection() {
        RecordingSessionStartup startup = new RecordingSessionStartup();
        ProfileDocument document = TestFixtures.document();
        DisplaySnapshot beforeLayout = TestFixtures.snapshot("foldable", null);
        DisplaySnapshot firstLayout = TestFixtures.snapshot("foldable", ProfileKind.INNER);
        DisplaySnapshot laterLayout = TestFixtures.snapshot("foldable", ProfileKind.OUTER);

        assertFalse(startup.hasStarted());
        try {
            startup.ensureActiveProfile(document, beforeLayout);
            fail("A pre-layout snapshot must not select a recording profile");
        } catch (IllegalStateException expected) {
            // The first WindowLayoutInfo callback owns initial profile selection.
        }

        assertTrue(startup.beginFirstLayoutInitialization());
        assertTrue(startup.hasStarted());

        assertNotNull(beforeLayout);
        ActiveProfiles.Active initial = startup.ensureActiveProfile(document, firstLayout);
        assertNotNull(initial);
        assertEquals(ProfileKind.INNER,
                initial.getDocument().getProfiles().get(initial.getProfileId()).getKind());

        assertFalse(startup.beginFirstLayoutInitialization());
        assertEquals(ProfileKind.INNER,
                startup.ensureActiveProfile(document, laterLayout)
                        .getDocument().getProfiles().get(initial.getProfileId()).getKind());
    }
}
