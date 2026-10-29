package com.localspend.lock;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.localspend.data.AppSettings;
import com.localspend.logic.LockPolicy;
import com.localspend.ui.LockActivity;

/**
 * Keeps the app locked when a PIN is set. It watches every screen: when the app comes to the
 * front and has not been unlocked (or was out of sight for more than 15 seconds), the PIN screen
 * is shown on top. Nothing in the other screens needs to know about it.
 */
public class AppLock implements Application.ActivityLifecycleCallbacks {

    private static boolean unlocked = false;
    private static long backgroundSinceMillis = 0;

    private final Context appContext;
    private int startedScreens = 0;

    public AppLock(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Called by the PIN screen once the right PIN or fingerprint is given. */
    public static void markUnlocked() {
        unlocked = true;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (startedScreens == 0) {
            // coming back to the front: lock again if we were away for a while
            if (unlocked && backgroundSinceMillis > 0
                    && LockPolicy.shouldRelock(backgroundSinceMillis, System.currentTimeMillis())) {
                unlocked = false;
            }
        }
        startedScreens++;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (activity instanceof LockActivity) {
            return;
        }
        if (new AppSettings(appContext).isLockOn() && !unlocked) {
            activity.startActivity(new Intent(activity, LockActivity.class));
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        startedScreens = Math.max(0, startedScreens - 1);
        if (startedScreens == 0) {
            backgroundSinceMillis = System.currentTimeMillis();
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
