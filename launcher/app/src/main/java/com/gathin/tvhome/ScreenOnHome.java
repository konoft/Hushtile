package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

/**
 * power_on_home_always without the accessibility helper: when the screen turns on, Home comes to the front a
 * moment later, over whatever input or app the TV restored (Realtek and Philips sets wake into the last HDMI
 * source). Starting an activity from the background needs "display over other apps" (SYSTEM_ALERT_WINDOW),
 * allowed over ADB: appops set com.hushtile.home SYSTEM_ALERT_WINDOW allow. Registered once per process.
 */
final class ScreenOnHome {
    private static final long DELAY_MS = 1200;
    private static boolean registered;

    private ScreenOnHome() {
    }

    static void register(Context context) {
        if (registered) return;
        registered = true;
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        Runnable goHome = () -> goHome(app);
        app.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                main.removeCallbacks(goHome);
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) main.postDelayed(goHome, DELAY_MS);
            }
        }, screenFilter());
    }

    /** Brings Home to the front; quietly does nothing where starting from the background is not allowed. */
    static void goHome(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(context.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (RuntimeException ignored) {
            // Not allowed to start from the background: the TV keeps what it restored.
        }
    }

    /**
     * After a cold start (a power cut, the mains switch) Realtek sets open their Live TV app on the last HDMI input
     * by themselves, seconds after the boot, while the launcher is not even running. BootHome starts Home when the
     * boot completes; for this long after it, a window the TV puts over Home before any key is pressed is sent
     * back (MainActivity.onPause).
     */
    static final long BOOT_GUARD_MS = 120_000;

    static boolean inBootGuard() {
        return android.os.SystemClock.elapsedRealtime() < BOOT_GUARD_MS;
    }

    private static IntentFilter screenFilter() {
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        return f;
    }
}
