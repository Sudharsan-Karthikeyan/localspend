package com.localspend.ui;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.localspend.R;
import com.localspend.data.SmsReviewRepository;
import com.localspend.util.Background;

import java.util.List;

/** Shows the senders the user trusts or blocks, and lets them remove a choice. */
final class SenderDialogs {

    private SenderDialogs() {
    }

    static void showSenders(Activity activity) {
        SmsReviewRepository review = SmsReviewRepository.getInstance(activity);
        Background.run(() -> {
            List<SmsReviewRepository.SenderRule> rules = review.getSenderRules();
            Background.main(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                AlertDialog.Builder b = new AlertDialog.Builder(activity)
                        .setTitle(R.string.senders_title)
                        .setPositiveButton(R.string.ok, null);
                if (rules.isEmpty()) {
                    b.setMessage(R.string.senders_empty);
                } else {
                    String[] labels = new String[rules.size()];
                    for (int i = 0; i < labels.length; i++) {
                        SmsReviewRepository.SenderRule r = rules.get(i);
                        labels[i] = activity.getString(r.trusted ? R.string.sender_trusted : R.string.sender_blocked,
                                r.label);
                    }
                    b.setItems(labels, (d, which) -> confirmRemove(activity, rules.get(which)));
                }
                b.show();
            });
        });
    }

    private static void confirmRemove(Activity activity, SmsReviewRepository.SenderRule rule) {
        new AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.sender_remove, rule.label))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove, (d, w) -> Background.run(() -> {
                    SmsReviewRepository.getInstance(activity).removeSender(rule.key);
                    Background.main(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            showSenders(activity);
                        }
                    });
                }))
                .show();
    }
}
