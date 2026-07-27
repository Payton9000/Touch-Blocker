package com.payton.touchblocker.profile;

import java.util.Map;

public interface KeyValueStore {
    String getString(String key, String defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    boolean putStringsAndBooleans(
            Map<String, String> strings,
            Map<String, Boolean> booleans
    );
}
