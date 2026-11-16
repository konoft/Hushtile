package com.gathin.tvhome;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * tidy_memory: closes apps that sit in the background on a TV with little RAM, so the one being watched has room.
 *
 * Many streaming apps start themselves at every boot and stay cached; on a 1.5–2 GB TV that pushes the next app
 * into swap. Home asks Android to end the background processes of apps nobody has used for tidy_memory_minutes,
 * once shortly after boot and again (at most every few minutes) when Home comes back. It uses
 * ActivityManager.killBackgroundProcesses, which never touches the app on screen, a foreground service (music
 * playing on) or a bound service, and the app opens normally next time. Kept: Home itself, the tools (utility
 * apps and add-ons, such as a running sleep timer), the keyboard, accessibility and notification services, Live TV
 * and anything in keep_running. Without usage access only the boot pass runs, since recently used apps can't be told.
 */
final class MemoryTidy {
    private static final String TAG = "MemoryTidy";
    /** RAM at or below this counts as low (bytes); 2 GB TVs report a little under 2 GiB. */
    static final long LOW_RAM_BYTES = 2_600L * 1024 * 1024;
    /** The boot pass: this long after the boot, when the apps that start themselves have done so. */
    static final long BOOT_PASS_MS = 75_000;
    /** The boot window: until then Home defers work that can wait. */
    static final long BOOT_WINDOW_MS = 120_000;
    /** At most one pass this often when Home comes back. */
    static final long MIN_INTERVAL_MS = 5 * 60_000;

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static long lastRun = -MIN_INTERVAL_MS;
    private static boolean bootPassDone;

    private MemoryTidy() {
    }

    /** A TV short of RAM: Android's own low-RAM flag, or 2.5 GB or less. */
    static boolean lowRam(long totalMem, boolean lowRamDevice) {
        return lowRamDevice || (totalMem > 0 && totalMem <= LOW_RAM_BYTES);
    }

    static boolean lowRam(Context context) {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        if (am == null) return false;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return lowRam(info.totalMem, am.isLowRamDevice());
    }

    /** Still starting up: less than BOOT_WINDOW_MS since the boot. */
    static boolean booting(long uptimeMs) {
        return uptimeMs < BOOT_WINDOW_MS;
    }

    /** How long after now the boot pass is due (0 or less: due), from the time since boot. */
    static long bootPassDelay(long uptimeMs) {
        return BOOT_PASS_MS - uptimeMs;
    }

    /**
     * The packages to close: every candidate except Home, the kept ones, and those used since `since`
     * (lastUsed: package -> last use, wall clock; a package missing from it has not been used).
     */
    static List<String> targets(Collection<String> candidates, String self, Set<String> keep,
                                Map<String, Long> lastUsed, long since) {
        List<String> out = new ArrayList<>();
        for (String pkg : candidates) {
            if (pkg == null || pkg.equals(self) || keep.contains(pkg)) continue;
            Long used = lastUsed.get(pkg);
            if (used != null && used >= since) continue;
            if (!out.contains(pkg)) out.add(pkg);
        }
        return out;
    }

    /** The boot pass, from Home: closes what started itself since the boot (nothing has been used yet). */
    static void afterBoot(Context context, Config config) {
        if (bootPassDone || !config.tidyMemory || !lowRam(context)) return;
        bootPassDone = true;   // once per boot, however often Home is rebuilt meanwhile
        Context app = context.getApplicationContext();
        worker.execute(() -> run(app, config, true));
    }

    /** The pass when Home comes back: at most every MIN_INTERVAL_MS, and only with usage access. */
    static void onHome(Context context, Config config) {
        if (!config.tidyMemory || !lowRam(context)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastRun < MIN_INTERVAL_MS || booting(now)) return;
        lastRun = now;
        Context app = context.getApplicationContext();
        worker.execute(() -> run(app, config, false));
    }

    private static void run(Context context, Config config, boolean boot) {
        try {
            ActivityManager am = context.getSystemService(ActivityManager.class);
            if (am == null) return;
            long now = System.currentTimeMillis();
            long bootTime = now - SystemClock.elapsedRealtime();
            Map<String, Long> lastUsed = lastUsed(context, now);
            if (!boot && lastUsed == null) return;   // no usage access: can't tell what was just watched
            if (lastUsed == null) lastUsed = new HashMap<>();
            long since = boot ? bootTime : Math.max(bootTime, now - config.tidyMemoryMinutes * 60_000L);
            List<String> close = targets(launchableApps(context), context.getPackageName(), kept(context, config),
                    lastUsed, since);
            for (String pkg : close) am.killBackgroundProcesses(pkg);
            Log.i(TAG, (boot ? "boot" : "home") + " pass: asked " + close.size() + " apps to close");
        } catch (RuntimeException e) {
            Log.w(TAG, "tidy skipped", e);
        }
    }

    /** package -> last use since a day ago, or null without usage access. */
    private static Map<String, Long> lastUsed(Context context, long now) {
        UsageStatsManager usm = context.getSystemService(UsageStatsManager.class);
        if (usm == null) return null;
        List<UsageStats> stats;
        try {
            stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 24 * 3_600_000L, now);
        } catch (RuntimeException e) {
            return null;
        }
        if (stats == null || stats.isEmpty()) return null;   // what Android returns without the access
        Map<String, Long> out = new HashMap<>();
        for (UsageStats s : stats) {
            Long old = out.get(s.getPackageName());
            if (old == null || s.getLastTimeUsed() > old) out.put(s.getPackageName(), s.getLastTimeUsed());
        }
        return out;
    }

    /** Apps with a TV or phone launcher entry that the owner installed or that came as updatable apps. */
    private static Set<String> launchableApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> out = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(intent, 0)) {
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (!system || updated) out.add(ai.packageName);
            }
        }
        return out;
    }

    /** Never closed: tools, add-ons, Live TV, the keyboard, accessibility and notification services, keep_running. */
    private static Set<String> kept(Context context, Config config) {
        Set<String> keep = new HashSet<>(config.keepRunning);
        keep.addAll(config.utilityApps);
        keep.addAll(config.utilityMoreApps);
        if (!TextUtils.isEmpty(config.liveTvPackage)) keep.add(config.liveTvPackage);
        PackageManager pm = context.getPackageManager();
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Cards.CATEGORY), 0)) {
            keep.add(ri.activityInfo.packageName);
        }
        String ime = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        addOwners(keep, ime);
        addOwners(keep, Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        addOwners(keep, Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners"));
        return keep;
    }

    /** "pkg/.Cls:pkg2/.Cls2" -> pkg, pkg2. */
    static void addOwners(Set<String> keep, String components) {
        if (components == null || components.isEmpty()) return;
        for (String part : components.split(":")) {
            int slash = part.indexOf('/');
            String pkg = (slash > 0 ? part.substring(0, slash) : part).trim();
            if (!pkg.isEmpty()) keep.add(pkg);
        }
    }
}
