package com.localspend.recurring;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/**
 * Wakes the app once a day (shortly after midnight) so repeating payments are added on time, even
 * if the app is not opened. Uses an inexact alarm, which needs no special permission; the alarm is
 * set again after a restart.
 */
public final class RecurringScheduler {

    private RecurringScheduler() {
    }

    public static void schedule(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        Calendar first = Calendar.getInstance();
        first.add(Calendar.DAY_OF_MONTH, 1);
        first.set(Calendar.HOUR_OF_DAY, 0);
        first.set(Calendar.MINUTE, 10);
        first.set(Calendar.SECOND, 0);
        first.set(Calendar.MILLISECOND, 0);

        Intent intent = new Intent(context, RecurringReceiver.class)
                .setAction(RecurringReceiver.ACTION_PROCESS);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setInexactRepeating(AlarmManager.RTC, first.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pending);
    }
}
