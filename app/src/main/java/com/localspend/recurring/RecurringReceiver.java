package com.localspend.recurring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.localspend.data.RecurringProcessor;
import com.localspend.util.Background;

/** Adds due repeating payments on the daily alarm and re-arms the alarm after a restart. */
public class RecurringReceiver extends BroadcastReceiver {

    public static final String ACTION_PROCESS = "com.localspend.action.PROCESS_RECURRING";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        final Context app = context.getApplicationContext();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            RecurringScheduler.schedule(app);
        } else if (!ACTION_PROCESS.equals(action)) {
            return;
        }
        final PendingResult result = goAsync();
        Background.run(() -> {
            try {
                RecurringProcessor.processDue(app, true);
            } finally {
                result.finish();
            }
        });
    }
}
