package com.localspend.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.localspend.data.TransactionRepository;
import com.localspend.notify.BudgetAlerts;
import com.localspend.notify.Notifier;
import com.localspend.util.Background;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Picks up new SMS messages as they arrive so transactions appear without opening the app.
 * Long messages arrive in parts, so parts from the same sender are joined before parsing.
 * When a transaction from a trusted bank is captured the user gets a notification (tap it to check
 * or correct the category), and budgets are checked so the user is warned as a limit gets close.
 * A transaction-looking message from an unknown sender is held for review instead, with a
 * notification asking the user to check it.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) {
            return;
        }

        Map<String, StringBuilder> bySender = new LinkedHashMap<>();
        for (SmsMessage part : parts) {
            String sender = part.getOriginatingAddress() == null ? "" : part.getOriginatingAddress();
            StringBuilder sb = bySender.get(sender);
            if (sb == null) {
                sb = new StringBuilder();
                bySender.put(sender, sb);
            }
            if (part.getMessageBody() != null) {
                sb.append(part.getMessageBody());
            }
        }

        final Context appContext = context.getApplicationContext();
        final long receivedAt = System.currentTimeMillis();
        final PendingResult pending = goAsync();
        Background.run(() -> {
            try {
                SmsImporter importer = new SmsImporter(appContext,
                        TransactionRepository.getInstance(appContext));
                boolean added = false;
                for (Map.Entry<String, StringBuilder> e : bySender.entrySet()) {
                    SmsImporter.Outcome o = importer.importMessage(e.getKey(),
                            e.getValue().toString(), receivedAt);
                    if (o.kind == SmsImporter.Outcome.Kind.ADDED) {
                        added = true;
                        Notifier.notifyTransaction(appContext, o.transaction);
                    } else if (o.kind == SmsImporter.Outcome.Kind.QUEUED) {
                        Notifier.notifyReview(appContext, o.pending);
                    }
                }
                if (added) {
                    BudgetAlerts.check(appContext);
                }
            } finally {
                pending.finish();
            }
        });
    }
}
