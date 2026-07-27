package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PointStoreTest {

    @Test
    public void getRawPointsJsonReturnsStoredValueWhenPresent() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        prefs.strings.put("points_json", "[{\"id\":1}]");
        Context context = new FakeContext(prefs);

        assertEquals("[{\"id\":1}]", PointStore.getRawPointsJson(context));
    }

    @Test
    public void getRawPointsJsonDefaultsToEmptyArrayWhenAbsent() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        Context context = new FakeContext(prefs);

        assertEquals("[]", PointStore.getRawPointsJson(context));
    }

    private static final class FakeContext extends ContextWrapper {
        private final SharedPreferences prefs;

        FakeContext(SharedPreferences prefs) {
            super(null);
            this.prefs = prefs;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return prefs;
        }
    }

    private static final class FakeSharedPreferences implements SharedPreferences {
        private final Map<String, String> strings = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getString(String key, String defaultValue) {
            return strings.containsKey(key) ? strings.get(key) : defaultValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defaultValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(String key, int defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLong(String key, long defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Editor edit() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
