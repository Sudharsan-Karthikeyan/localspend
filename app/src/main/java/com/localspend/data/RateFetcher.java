package com.localspend.data;

import com.localspend.logic.ExchangeRates;
import com.localspend.model.Currency;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks up the real exchange rate for a day (European Central Bank reference rates through the
 * free Frankfurter service). Only two currency codes and a date are sent: no amounts, merchants
 * or messages ever leave the device. Call from a background thread.
 */
public final class RateFetcher {

    private static final String BASE = "https://api.frankfurter.dev/v1/";
    private static final int TIMEOUT_MS = 4000;

    private RateFetcher() {
    }

    /** The URL for the rate of {@code from} in {@code to} on a day ("latest" for today or later). */
    static String urlFor(Currency from, Currency to, long dayStart, long nowMillis) {
        String when = dayStart >= ExchangeRates.dayStart(nowMillis) ? "latest"
                : new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(dayStart));
        return BASE + when + "?base=" + from.name() + "&symbols=" + to.name();
    }

    /** Reads the rate for {@code to} out of the service's answer, or null if it is not there. */
    public static BigDecimal parse(String json, Currency to) {
        if (json == null) {
            return null;
        }
        Matcher m = Pattern.compile("\"rates\"\\s*:\\s*\\{[^}]*\"" + to.name()
                + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(json);
        if (!m.find()) {
            return null;
        }
        BigDecimal rate = new BigDecimal(m.group(1));
        return rate.signum() > 0 ? rate : null;
    }

    /** The rate for the day, or null when it could not be fetched (offline, service down). */
    public static BigDecimal fetch(Currency from, Currency to, long dayStart) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlFor(from, to, dayStart,
                    System.currentTimeMillis())).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    body.append(line);
                }
            }
            return parse(body.toString(), to);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
