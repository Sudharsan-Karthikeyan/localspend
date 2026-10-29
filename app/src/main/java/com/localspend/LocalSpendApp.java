package com.localspend;

import android.app.Application;

import com.localspend.lock.AppLock;

/** Starts the app lock, which watches every screen so none can be opened without the PIN. */
public class LocalSpendApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new AppLock(this));
    }
}
