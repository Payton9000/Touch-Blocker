package com.payton.touchblocker.profile;

import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SharedPreferencesKeyValueStore implements KeyValueStore {
    private final SharedPreferences preferences;

    public SharedPreferencesKeyValueStore(SharedPreferences preferences) {
        if (preferences == null) {
            throw new NullPointerException("preferences == null");
        }
        this.preferences = preferences;
    }

    @Override
    public String getString(String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    @Override
    public boolean putStringsAndBooleans(
            Map<String, String> strings,
            Map<String, Boolean> booleans
    ) {
        if (strings == null) {
            throw new NullPointerException("strings == null");
        }
        if (booleans == null) {
            throw new NullPointerException("booleans == null");
        }
        LinkedHashSet<String> touchedKeys = new LinkedHashSet<>();
        touchedKeys.addAll(strings.keySet());
        touchedKeys.addAll(booleans.keySet());
        Map<String, ?> allValues = preferences.getAll();
        LinkedHashMap<String, Object> previousValues = new LinkedHashMap<>();
        for (String key : touchedKeys) {
            if (allValues.containsKey(key)) {
                previousValues.put(key, copyPreferenceValue(allValues.get(key), key));
            }
        }

        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, String> entry : strings.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Boolean> entry : booleans.entrySet()) {
            editor.putBoolean(entry.getKey(), entry.getValue());
        }
        if (editor.commit()) {
            return true;
        }

        SharedPreferences.Editor rollback = preferences.edit();
        for (String key : touchedKeys) {
            if (previousValues.containsKey(key)) {
                restorePreferenceValue(rollback, key, previousValues.get(key));
            } else {
                rollback.remove(key);
            }
        }
        rollback.commit();
        return false;
    }

    private static Object copyPreferenceValue(Object value, String key) {
        if (value instanceof String
                || value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float) {
            return value;
        }
        if (value instanceof Set) {
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            for (Object item : (Set<?>) value) {
                if (!(item instanceof String)) {
                    throw new IllegalStateException(
                            "SharedPreferences value for " + key + " is not a string set");
                }
                copy.add((String) item);
            }
            return copy;
        }
        throw new IllegalStateException(
                "Unsupported SharedPreferences value for " + key + ": " + value);
    }

    private static void restorePreferenceValue(
            SharedPreferences.Editor editor,
            String key,
            Object value
    ) {
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else {
            @SuppressWarnings("unchecked")
            Set<String> strings = (Set<String>) value;
            editor.putStringSet(key, new LinkedHashSet<>(strings));
        }
    }
}
