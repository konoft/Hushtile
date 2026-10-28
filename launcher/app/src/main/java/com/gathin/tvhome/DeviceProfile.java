package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hushtile on a TV nobody here has tried: its look from assets/hushtile.json, then what the cleanyourtv community found
 * on that TV (assets/profiles.json, from examples/ by launcher/profiles/gen_profiles.py), then a theme pushed over ADB.
 *
 * <p>A profile is used when the TV's brand matches and one of its codenames (device, product, board, hardware or a
 * model word) appears in the tested TV's description. With no profile, package rules fill in what a vendor app implies:
 * the Live TV app or Source panel every tested TV with that app used.
 */
final class DeviceProfile {
    private static final String TAG = "DeviceProfile";
    private static JSONObject base;
    private static String matchedId;

    private DeviceProfile() {
    }

    /** The settings Config reads: Hushtile's look, under this TV's profile, under the pushed theme (may be null). */
    static synchronized JSONObject layered(Context ctx, JSONObject overlay) {
        if (base == null) base = build(ctx);
        JSONObject out = copy(base);
        if (overlay != null) merge(out, overlay);
        return out;
    }

    /** The examples/ folder this TV matched, or null. */
    static String matchedId() {
        return matchedId;
    }

    private static JSONObject build(Context ctx) {
        JSONObject look = asset(ctx, "hushtile.json");
        JSONObject out = look != null ? look : new JSONObject();
        JSONObject profiles = asset(ctx, "profiles.json");
        if (profiles == null) return out;
        JSONObject profile = match(profiles.optJSONArray("profiles"), identity());
        if (profile != null) {
            matchedId = profile.optString("id", null);
            merge(out, profile.optJSONObject("config"));
            Log.i(TAG, "matched tested TV " + matchedId);
        }
        applyPackageRules(ctx, out, profiles.optJSONArray("package_rules"), profile != null ? profile.optJSONObject("config") : null);
        return out;
    }

    /** Lower-case words that name this TV: brand words, and codenames to compare with a profile's tokens. */
    static final class Identity {
        final String brand;
        final Set<String> codes = new HashSet<>();

        Identity(String brand, String... names) {
            this.brand = brand.toLowerCase(Locale.ROOT);
            for (String n : names) {
                if (n == null) continue;
                for (String w : n.toLowerCase(Locale.ROOT).split("[\\s,;()/+]+")) {
                    if (w.length() >= 3) codes.add(w);
                }
            }
        }
    }

    private static Identity identity() {
        return new Identity(Build.BRAND + " " + Build.MANUFACTURER + " " + Build.MODEL,
                Build.DEVICE, Build.PRODUCT, Build.BOARD, Build.HARDWARE, Build.MODEL);
    }

    /** The best profile: the brand must match, then the most shared codenames wins; none shared is no match. */
    static JSONObject match(JSONArray profiles, Identity tv) {
        if (profiles == null) return null;
        JSONObject best = null;
        int bestScore = 0;
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject p = profiles.optJSONObject(i);
            if (p == null || !brandMatches(p.optJSONArray("brand"), tv.brand)) continue;
            JSONArray tokens = p.optJSONArray("tokens");
            int score = 0;
            for (int t = 0; tokens != null && t < tokens.length(); t++) {
                if (tv.codes.contains(tokens.optString(t))) score++;
            }
            if (score > bestScore) {
                best = p;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean brandMatches(JSONArray brand, String tvBrand) {
        for (int i = 0; brand != null && i < brand.length(); i++) {
            if (tvBrand.contains(brand.optString(i))) return true;
        }
        return false;
    }

    /**
     * For each key the profile left unset, the first rule whose vendor app is installed. A TV maker's own app comes
     * before Android's generic ones (com.android.tv), and among those the one more tested TVs used.
     */
    private static void applyPackageRules(Context ctx, JSONObject out, JSONArray rules, JSONObject profileConfig) {
        if (rules == null) return;
        List<JSONObject> ordered = new ArrayList<>();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.optJSONObject(i);
            if (r != null) ordered.add(r);
        }
        ordered.sort((a, b) -> {
            boolean ga = generic(a.optString("needs")), gb = generic(b.optString("needs"));
            if (ga != gb) return ga ? 1 : -1;
            return Integer.compare(b.optInt("tvs"), a.optInt("tvs"));
        });
        PackageManager pm = ctx.getPackageManager();
        Set<String> decided = new HashSet<>();
        if (profileConfig != null) {
            for (Iterator<String> it = profileConfig.keys(); it.hasNext(); ) decided.add(it.next());
        }
        for (JSONObject r : ordered) {
            JSONObject config = r.optJSONObject("config");
            if (config == null || !installed(pm, r.optString("needs"))) continue;
            boolean fresh = true;
            for (Iterator<String> it = config.keys(); it.hasNext(); ) if (decided.contains(it.next())) fresh = false;
            if (!fresh) continue;
            merge(out, config);
            for (Iterator<String> it = config.keys(); it.hasNext(); ) decided.add(it.next());
        }
    }

    private static boolean generic(String pkg) {
        return pkg.startsWith("com.android.") || pkg.startsWith("com.google.");
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Copies {@code top} over {@code into}; hidden_apps add up instead, so a TV's extras stay hidden under any theme. */
    static void merge(JSONObject into, JSONObject top) {
        if (top == null) return;
        for (Iterator<String> it = top.keys(); it.hasNext(); ) {
            String key = it.next();
            try {
                if ("hidden_apps".equals(key) && into.optJSONArray(key) != null && top.optJSONArray(key) != null) {
                    Set<String> all = new LinkedHashSet<>();
                    JSONArray a = into.getJSONArray(key), b = top.getJSONArray(key);
                    for (int i = 0; i < a.length(); i++) all.add(a.getString(i));
                    for (int i = 0; i < b.length(); i++) all.add(b.getString(i));
                    into.put(key, new JSONArray(all));
                } else {
                    into.put(key, top.get(key));
                }
            } catch (JSONException ignored) {
            }
        }
    }

    private static JSONObject copy(JSONObject o) {
        try {
            return new JSONObject(o.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONObject asset(Context ctx, String name) {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return new JSONObject(buf.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException | JSONException e) {
            Log.w(TAG, "no " + name + ": " + e.getMessage());
            return null;
        }
    }
}
