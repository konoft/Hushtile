package com.gathin.tvhome;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * order_by_usage: how much each app is used, most first. The TV's own usage statistics (foreground time over the
 * last year, readable once GET_USAGE_STATS is allowed over ADB: appops set ... GET_USAGE_STATS allow) plus the
 * launcher's own count of launches from Home, a minute each, so a fresh app opened from Home still climbs.
 */
final class UsageRank {
    private static final String PREFS = "usage_rank";
    private static final long YEAR_MS = 365L * 24 * 60 * 60_000;
    private static final long LAUNCH_WEIGHT_MS = 60_000;

    private UsageRank() {
    }

    static void recordLaunch(Context ctx, String pkg) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putInt(pkg, p.getInt(pkg, 0) + 1).apply();
    }

    /** Package to score (milliseconds); a package never used is missing. */
    static Map<String, Long> scores(Context ctx) {
        Map<String, Long> out = new HashMap<>();
        try {
            UsageStatsManager usm = ctx.getSystemService(UsageStatsManager.class);
            long now = System.currentTimeMillis();
            Map<String, UsageStats> stats = usm == null ? null : usm.queryAndAggregateUsageStats(now - YEAR_MS, now);
            if (stats != null) {
                for (Map.Entry<String, UsageStats> e : stats.entrySet()) {
                    long t = e.getValue().getTotalTimeInForeground();
                    if (t > 0) out.put(e.getKey(), t);
                }
            }
        } catch (RuntimeException ignored) {
            // Not allowed: the launcher's own counts still order the apps.
        }
        for (Map.Entry<String, ?> e : ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll().entrySet()) {
            if (!(e.getValue() instanceof Integer)) continue;
            long add = (Integer) e.getValue() * LAUNCH_WEIGHT_MS;
            out.merge(e.getKey(), add, Long::sum);
        }
        return out;
    }
}
