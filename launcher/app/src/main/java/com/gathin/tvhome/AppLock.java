package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App lock / kids PIN: a 4-digit PIN before the chosen apps open from Home (tiles, top shelf and App library) and
 * before Launcher settings open, so the lock cannot be switched off without it. A correct PIN keeps that app (or
 * the settings) open for UNLOCK_MS. Only Home is guarded: an app opened another way (voice, the TV's own Settings)
 * is not, which the settings row says.
 */
final class AppLock {
    private static final String PREFS = "app_lock";
    private static final String PIN = "pin_sha256";
    private static final String LOCKED = "locked_packages";
    static final String SETTINGS = "settings";
    private static final long UNLOCK_MS = 10 * 60_000L;
    /** Unlocked package (or SETTINGS) to the elapsed-realtime it was unlocked at; gone with the process. */
    private static final java.util.Map<String, Long> unlocked = new java.util.HashMap<>();

    private AppLock() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasPin(Context ctx) {
        return prefs(ctx).contains(PIN);
    }

    static Set<String> locked(Context ctx) {
        return new HashSet<>(prefs(ctx).getStringSet(LOCKED, Collections.emptySet()));
    }

    static boolean isLocked(Context ctx, String pkg) {
        return pkg != null && hasPin(ctx) && locked(ctx).contains(pkg);
    }

    /**
     * Runs open now when pkg is not locked (or was unlocked a moment ago); otherwise asks for the PIN first and runs
     * it only after the right one. Use SETTINGS for Launcher settings.
     */
    static void guard(Activity activity, String pkg, Runnable open) {
        guard(activity, pkg, open, null);
    }

    /** As above; denied runs on a wrong PIN or Cancel (Launcher settings close then). */
    static void guard(Activity activity, String pkg, Runnable open, Runnable denied) {
        boolean needed = SETTINGS.equals(pkg) ? hasPin(activity) : isLocked(activity, pkg);
        Long at = unlocked.get(pkg);
        if (!needed || (at != null && SystemClock.elapsedRealtime() - at < UNLOCK_MS)) {
            open.run();
            return;
        }
        askPin(activity, activity.getString(R.string.app_lock_enter), pin -> {
            if (matches(activity, pin)) {
                unlocked.put(pkg, SystemClock.elapsedRealtime());
                open.run();
            } else {
                Toast.makeText(activity, R.string.app_lock_wrong, Toast.LENGTH_SHORT).show();
                if (denied != null) denied.run();
            }
        }, denied);
    }

    /** Launcher settings' App lock row: set or change the PIN, choose the apps, or turn the lock off. */
    static void showSettings(Activity activity) {
        if (!hasPin(activity)) {
            setNewPin(activity, () -> chooseApps(activity));
            return;
        }
        String[] rows = {
                activity.getString(R.string.app_lock_choose_apps),
                activity.getString(R.string.app_lock_change_pin),
                activity.getString(R.string.app_lock_turn_off)};
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.app_lock_title_count, locked(activity).size()))
                .setItems(rows, (d, which) -> {
                    if (which == 0) chooseApps(activity);
                    else if (which == 1) setNewPin(activity, null);
                    else {
                        prefs(activity).edit().clear().apply();
                        unlocked.clear();
                        Toast.makeText(activity, R.string.app_lock_off, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The row's text: off, or how many apps are locked. */
    static String state(Context ctx) {
        if (!hasPin(ctx)) return ctx.getString(R.string.app_lock_state, ctx.getString(R.string.off));
        return ctx.getString(R.string.app_lock_state, ctx.getString(R.string.app_lock_apps, locked(ctx).size()));
    }

    private static void setNewPin(Activity activity, Runnable then) {
        askPin(activity, activity.getString(R.string.app_lock_new), first -> {
            if (first.length() != 4) {
                Toast.makeText(activity, R.string.app_lock_four_digits, Toast.LENGTH_SHORT).show();
                return;
            }
            askPin(activity, activity.getString(R.string.app_lock_repeat), second -> {
                if (!first.equals(second)) {
                    Toast.makeText(activity, R.string.app_lock_mismatch, Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs(activity).edit().putString(PIN, hash(first)).apply();
                unlocked.put(SETTINGS, SystemClock.elapsedRealtime());
                Toast.makeText(activity, R.string.app_lock_pin_set, Toast.LENGTH_SHORT).show();
                if (then != null) then.run();
            }, null);
        }, null);
    }

    /** Every app with a TV entry point, checked when locked; saved on OK. */
    private static void chooseApps(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        Set<String> chosen = locked(activity);
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i)[0];
            checked[i] = chosen.contains(apps.get(i)[1]);
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.app_lock_choose_apps)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Set<String> out = new HashSet<>();
                    for (int i = 0; i < apps.size(); i++) if (checked[i]) out.add(apps.get(i)[1]);
                    prefs(activity).edit().putStringSet(LOCKED, out).apply();
                    unlocked.keySet().retainAll(Collections.singleton(SETTINGS));
                    Toast.makeText(activity, activity.getString(R.string.app_lock_saved, out.size()), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    interface PinCallback {
        void onPin(String pin);
    }

    /** A 4-digit password field in a dialog; the TV keyboard opens with it. */
    static void askPin(Activity activity, String title, PinCallback done, Runnable cancelled) {
        EditText field = new EditText(activity);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        field.setSingleLine(true);
        FrameLayout box = new FrameLayout(activity);
        int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> done.onPin(field.getText().toString()))
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    if (cancelled != null) cancelled.run();
                })
                .setOnCancelListener(d -> {
                    if (cancelled != null) cancelled.run();
                })
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        // Four digits are the whole PIN: go on without hunting for OK on the keyboard.
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (s.length() == 4 && dialog.isShowing()) {
                    dialog.dismiss();
                    done.onPin(s.toString());
                }
            }
        });
        dialog.show();
        field.requestFocus();
    }

    private static boolean matches(Context ctx, String pin) {
        String saved = prefs(ctx).getString(PIN, null);
        return saved != null && saved.equals(hash(pin));
    }

    private static String hash(String pin) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("cyt-applock:" + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin;
        }
    }
}
