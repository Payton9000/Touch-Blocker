package com.payton.touchblocker.profile;

public final class ProfileSelection {
    public enum Status {
        MATCHED,
        UNKNOWN,
        AMBIGUOUS
    }

    public enum Reason {
        MANUAL_BINDING,
        EXACT_FINGERPRINT,
        SUGGESTED_KIND,
        AMBIGUOUS,
        NO_MATCH
    }

    private final Status status;
    private final Reason reason;
    private final String profileId;

    ProfileSelection(Status status, Reason reason, String profileId) {
        this.status = status;
        this.reason = reason;
        this.profileId = profileId;
    }

    public Status getStatus() {
        return status;
    }

    public Reason getReason() {
        return reason;
    }

    public String getProfileId() {
        return profileId;
    }
}
