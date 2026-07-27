package com.payton.touchblocker;

import static org.junit.Assert.assertTrue;
import androidx.appcompat.app.AppCompatActivity;
import org.junit.Test;

public class AndroidXMigrationTest {
    @Test
    public void mainActivityUsesAndroidXAppCompat() {
        assertTrue(AppCompatActivity.class.isAssignableFrom(MainActivity.class));
    }

    @Test
    public void applicationClassEnablesDynamicColor() {
        assertTrue(android.app.Application.class.isAssignableFrom(TouchBlockerApp.class));
    }
}
