package com.payton.touchblocker;

import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.profile.ActiveProfiles;
import com.payton.touchblocker.profile.ProfileDocument;

/** Coordinates one recording session's first layout callback and profile selection. */
public final class RecordingSessionStartup {
    private enum State {
        WAITING,
        INITIALIZING,
        CANCELLED
    }

    private State state = State.WAITING;
    private ActiveProfiles.Active activeProfile;

    /** Returns true only for the first WindowLayoutInfo callback in this activity instance. */
    public synchronized boolean beginFirstLayoutInitialization() {
        return beginFromLayoutCallback();
    }

    /** Claims initialization when WindowLayoutInfo has populated the fold cache. */
    public synchronized boolean beginFromLayoutCallback() {
        return claimInitialization();
    }

    /** Claims initialization when WindowLayoutInfo did not arrive before the fallback deadline. */
    public synchronized boolean beginFromTimeout() {
        return claimInitialization();
    }

    /** Cancels an unclaimed initialization while the activity is stopped or destroyed. */
    public synchronized void cancelPendingInitialization() {
        if (state == State.WAITING) {
            state = State.CANCELLED;
        }
    }

    /** Re-arms a cancelled initialization when the same activity is started again. */
    public synchronized boolean resumeAfterLifecycleStart() {
        if (state == State.CANCELLED) {
            state = State.WAITING;
        }
        return state == State.WAITING;
    }

    public synchronized boolean isWaitingForInitialization() {
        return state == State.WAITING;
    }

    public synchronized boolean hasStarted() {
        return state != State.WAITING;
    }

    /** Selects the active profile once, using the snapshot captured by the race winner. */
    public synchronized ActiveProfiles.Active ensureActiveProfile(
            ProfileDocument document,
            DisplaySnapshot snapshot
    ) {
        if (state != State.INITIALIZING) {
            throw new IllegalStateException("Recording initialization has not been claimed");
        }
        if (activeProfile == null) {
            activeProfile = ActiveProfiles.ensure(document, snapshot);
        }
        return activeProfile;
    }

    private boolean claimInitialization() {
        if (state != State.WAITING) {
            return false;
        }
        state = State.INITIALIZING;
        return true;
    }
}
