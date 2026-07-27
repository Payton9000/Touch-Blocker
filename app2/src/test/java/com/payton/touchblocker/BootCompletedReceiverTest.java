package com.payton.touchblocker;

import android.content.Intent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BootCompletedReceiverTest {

    @Test
    public void restoresAfterBootCompleted() {
        assertTrue(BootCompletedReceiver.isRestoreTrigger(Intent.ACTION_BOOT_COMPLETED));
    }

    @Test
    public void restoresAfterUserUnlocks() {
        assertTrue(BootCompletedReceiver.isRestoreTrigger(Intent.ACTION_USER_UNLOCKED));
    }

    @Test
    public void restoresAfterPackageReplacement() {
        assertTrue(BootCompletedReceiver.isRestoreTrigger(Intent.ACTION_MY_PACKAGE_REPLACED));
    }

    @Test
    public void ignoresUnrelatedBroadcasts() {
        assertFalse(BootCompletedReceiver.isRestoreTrigger(Intent.ACTION_TIME_CHANGED));
        assertFalse(BootCompletedReceiver.isRestoreTrigger(null));
    }
}
