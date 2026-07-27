package com.payton.touchblocker.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileDocument {
    private final int schemaVersion;
    private final Map<String, ScreenProfile> profiles;
    private final Map<String, String> manualBindings;

    public ProfileDocument(
            int schemaVersion,
        Map<String, ScreenProfile> profiles,
        Map<String, String> manualBindings
    ) {
        this.schemaVersion = schemaVersion;
        this.profiles = immutableProfileCopy(profiles);
        this.manualBindings = immutableCopy(manualBindings, "manualBindings");
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public Map<String, ScreenProfile> getProfiles() {
        return profiles;
    }

    public Map<String, String> getManualBindings() {
        return manualBindings;
    }

    public ProfileDocument withProfile(ScreenProfile profile) {
        if (profile == null) {
            throw new NullPointerException("profile == null");
        }
        LinkedHashMap<String, ScreenProfile> updated = new LinkedHashMap<>(profiles);
        updated.put(profile.getId(), profile);
        return new ProfileDocument(schemaVersion, updated, manualBindings);
    }

    public ProfileDocument withManualBinding(String stableKey, String profileId) {
        if (stableKey == null) {
            throw new NullPointerException("stableKey == null");
        }
        if (profileId == null) {
            throw new NullPointerException("profileId == null");
        }
        LinkedHashMap<String, String> updated = new LinkedHashMap<>(manualBindings);
        updated.put(stableKey, profileId);
        return new ProfileDocument(schemaVersion, profiles, updated);
    }

    public ProfileDocument withoutManualBinding(String stableKey) {
        if (stableKey == null) {
            throw new NullPointerException("stableKey == null");
        }
        LinkedHashMap<String, String> updated = new LinkedHashMap<>(manualBindings);
        updated.remove(stableKey);
        return new ProfileDocument(schemaVersion, profiles, updated);
    }

    private static <V> Map<String, V> immutableCopy(Map<String, V> source, String name) {
        if (source == null) {
            throw new NullPointerException(name + " == null");
        }
        LinkedHashMap<String, V> copy = new LinkedHashMap<>();
        for (Map.Entry<String, V> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                throw new NullPointerException(name + " contains a null key");
            }
            if (entry.getValue() == null) {
                throw new NullPointerException(name + " contains a null value");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, ScreenProfile> immutableProfileCopy(
            Map<String, ScreenProfile> source
    ) {
        Map<String, ScreenProfile> copy = immutableCopy(source, "profiles");
        for (Map.Entry<String, ScreenProfile> entry : copy.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().getId())) {
                throw new IllegalArgumentException(
                        "profile map key must match profile id: " + entry.getKey());
            }
        }
        return copy;
    }
}
