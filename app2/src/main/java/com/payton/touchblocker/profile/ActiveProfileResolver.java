package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;

/** Resolves the current profile and persists the first profile for a newly seen display. */
public final class ActiveProfileResolver {
    private ActiveProfileResolver() {
    }

    public static Resolution resolve(
            ProfileRepository repository,
            ProfileDocument document,
            DisplaySnapshot snapshot
    ) {
        if (repository == null) {
            throw new NullPointerException("repository == null");
        }
        if (document == null) {
            throw new NullPointerException("document == null");
        }
        if (snapshot == null) {
            throw new NullPointerException("snapshot == null");
        }

        ProfileSelection selection = new ProfileSelector().select(snapshot, document);
        ActiveProfiles.Active active = ActiveProfiles.ensure(document, snapshot);
        if (active == null) {
            return new Resolution(document, selection, null, false);
        }
        if (active.isCreated() && !repository.save(active.getDocument())) {
            return new Resolution(document, selection, null, false);
        }
        ScreenProfile profile = active.getDocument().getProfiles().get(active.getProfileId());
        return new Resolution(active.getDocument(), selection, profile, active.isCreated());
    }

    public static final class Resolution {
        private final ProfileDocument document;
        private final ProfileSelection selection;
        private final ScreenProfile profile;
        private final boolean created;

        private Resolution(
                ProfileDocument document,
                ProfileSelection selection,
                ScreenProfile profile,
                boolean created
        ) {
            this.document = document;
            this.selection = selection;
            this.profile = profile;
            this.created = created;
        }

        public ProfileDocument getDocument() {
            return document;
        }

        public ProfileSelection getSelection() {
            return selection;
        }

        public ScreenProfile getProfile() {
            return profile;
        }

        public boolean isCreated() {
            return created;
        }
    }
}
