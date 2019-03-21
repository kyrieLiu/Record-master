package com.inno.record.utils;

import android.text.TextUtils;
import android.util.Log;

public class Logger {
    private static final String TAG = "petproject";
    // xxh, Just For Test, Should change to false for release
    // private static final boolean DEBUG = false;
    private static final boolean DEBUG = true;

    private Logger() {
    }

    public static void e(String message) {
        if (DEBUG && !TextUtils.isEmpty(message)) {
            Log.e(TAG, message);
        }
    }

    public static void e(String message, Throwable e) {
        if (DEBUG && !TextUtils.isEmpty(message)) {
            Log.e(TAG, message, e);
        }
    }

    public static void w(String message) {
        if (DEBUG && !TextUtils.isEmpty(message)) {
            Log.w(TAG, message);
        }
    }

    public static void w(String message, Throwable e) {
        if (DEBUG && !TextUtils.isEmpty(message)) {
            Log.w(TAG, message, e);
        }
    }

    public static void i(String message) {
        if (DEBUG && !TextUtils.isEmpty(message))
            Log.i(TAG, message);
    }

    public static void i(String message, Throwable e) {
        if (DEBUG && !TextUtils.isEmpty(message))
            Log.i(TAG, message, e);
    }

    public static void d(String message) {
        if (DEBUG && !TextUtils.isEmpty(message))
            Log.d(TAG, message);
    }

}
