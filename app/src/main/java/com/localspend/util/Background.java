package com.localspend.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny helper so database work stays off the UI thread. A single worker thread also serialises
 * writes, which keeps SQLite access simple.
 */
public final class Background {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Background() {
    }

    public static void run(Runnable work) {
        WORKER.execute(work);
    }

    public static void main(Runnable work) {
        MAIN.post(work);
    }

    public static void mainDelayed(Runnable work, long delayMillis) {
        MAIN.postDelayed(work, delayMillis);
    }

    public static void cancelMain(Runnable work) {
        MAIN.removeCallbacks(work);
    }
}
