package com.payton.touchblocker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogManager {
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE_PREFIX = "touch_log_";

    public static void appendPoint(Context context, TouchPoint point) {
        File logFile = getOrCreateLogFile(context);
        if (logFile == null) {
            return;
        }
        String line = point.getTimestamp() + "," + point.getId() + "," + point.getX() + "," + point.getY() + "," + point.getDurationMs() + "\n";
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(logFile, true));
            writer.write(line);
        } catch (IOException ignored) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static Intent buildShareIntent(Context context) {
        File logFile = getLatestLogFile(context);
        if (logFile == null || !logFile.exists()) {
            return null;
        }
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider",
                logFile);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private static File getOrCreateLogFile(Context context) {
        File dir = new File(context.getExternalFilesDir(null), LOG_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File logFile = new File(dir, LOG_FILE_PREFIX + getDateStamp() + ".csv");
        if (!logFile.exists()) {
            try {
                if (logFile.createNewFile()) {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
                    writer.write("timestamp,id,x,y,duration_ms\n");
                    writer.close();
                }
            } catch (IOException ignored) {
                return null;
            }
        }
        return logFile;
    }

    private static File getLatestLogFile(Context context) {
        File dir = new File(context.getExternalFilesDir(null), LOG_DIR);
        if (!dir.exists()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        File latest = files[0];
        for (File file : files) {
            if (file.lastModified() > latest.lastModified()) {
                latest = file;
            }
        }
        return latest;
    }

    private static String getDateStamp() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }
}
