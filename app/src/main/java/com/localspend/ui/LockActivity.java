package com.localspend.ui;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import com.google.android.material.textfield.TextInputEditText;
import com.localspend.R;
import com.localspend.data.AppSettings;
import com.localspend.lock.AppLock;
import com.localspend.logic.LockPolicy;
import com.localspend.util.Background;

/**
 * The PIN screen shown on top of the app while it is locked. The PIN (or a fingerprint, if the
 * user turned that on) unlocks it; the back button leaves the app instead of going past the lock.
 * After five wrong PINs, further guesses are delayed.
 */
public class LockActivity extends AppCompatActivity {

    private AppSettings settings;
    private TextInputEditText etPin;
    private TextView tvError;
    private Button btnUnlock;
    private final Runnable countdown = this::updateLockout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        settings = new AppSettings(this);
        etPin = findViewById(R.id.etLockPin);
        tvError = findViewById(R.id.tvLockError);
        btnUnlock = findViewById(R.id.btnUnlock);
        btnUnlock.setOnClickListener(v -> tryPin());
        etPin.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryPin();
                return true;
            }
            return false;
        });

        Button btnFingerprint = findViewById(R.id.btnFingerprint);
        if (settings.isLockBiometricOn() && biometricAvailable()) {
            btnFingerprint.setVisibility(android.view.View.VISIBLE);
            btnFingerprint.setOnClickListener(v -> showBiometricPrompt());
            showBiometricPrompt();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLockout();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Background.cancelMain(countdown);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);   // leave the app; never fall through to the locked screen
    }

    private boolean biometricAvailable() {
        return BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showBiometricPrompt() {
        BiometricPrompt prompt = new BiometricPrompt(this, command -> Background.main(command),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        unlock();
                    }
                });
        prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_title))
                .setNegativeButtonText(getString(R.string.lock_use_pin))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build());
    }

    private void tryPin() {
        long wait = remainingLockout();
        if (wait > 0) {
            updateLockout();
            return;
        }
        String pin = etPin.getText() == null ? "" : etPin.getText().toString();
        if (settings.checkPin(pin)) {
            settings.resetFailedPins();
            unlock();
        } else {
            settings.recordFailedPin();
            etPin.setText("");
            tvError.setText(R.string.lock_wrong_pin);
            updateLockout();
        }
    }

    private long remainingLockout() {
        return LockPolicy.lockoutRemaining(settings.getFailedPinAttempts(),
                settings.getLastFailedPinMillis(), System.currentTimeMillis());
    }

    /** Disables the PIN field and counts down while too many wrong PINs have been tried. */
    private void updateLockout() {
        long wait = remainingLockout();
        boolean locked = wait > 0;
        etPin.setEnabled(!locked);
        btnUnlock.setEnabled(!locked);
        if (locked) {
            tvError.setText(getString(R.string.lock_try_again, (wait + 999) / 1000));
            Background.cancelMain(countdown);
            Background.mainDelayed(countdown, 1000);
        }
    }

    private void unlock() {
        AppLock.markUnlocked();
        finish();
    }
}
