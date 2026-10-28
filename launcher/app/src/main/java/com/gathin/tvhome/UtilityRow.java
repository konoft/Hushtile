package com.gathin.tvhome;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * pane_layout's bottom pane: small tiles for tools (utility_apps) in one row ending in More, which opens a second
 * row (utility_more_apps, then All apps for the App library). While the second row is open the suggestion pane
 * folds away, so the panes never overlap; going back up to the apps closes it again.
 */
final class UtilityRow {
    interface Host {
        /** Up from the first row: back to the app grid. */
        void onUtilityUp();

        /** The second row opened (true) or closed, after the suggestion pane has moved. */
        void onUtilityExpanded(boolean expanded);

        /** The All apps tile. */
        void onAllApps();
    }

    static final String MORE = "@more", ALL_APPS = "@library";
    /** The Cinematic layout's row: at most this many tools, as wide glass pills, without More. */
    private static final int PILLS = 5;
    private static final String BLUETOOTH_CONNECT = "com.gathin.btconnect";
    private static final int TILE_W = 108, TILE_H = 42, GAP = 14;
    private boolean pills;

    private final Activity activity;
    private final Host host;
    private final float density;
    private final boolean animations;
    private final Map<String, String> labels;
    private final LinearLayout row1, row2;
    private boolean expanded;
    /** The suggestion pane: it gives up the second row's height while that is open, so the panes between stay put. */
    private View companion;
    private int companionFull = -1;
    private ValueAnimator slide;
    /** The tile last opened, to put focus back on when Home comes back from its app; null otherwise. */
    private View returnTo;

    /** Spreads each row's tiles evenly between these insets from the pane's edges (the app band's edges). */
    void alignTo(int left, int right) {
        for (LinearLayout r : new LinearLayout[]{row1, row2}) {
            r.setPadding(left, r.getPaddingTop(), right, r.getPaddingBottom());
            for (int i = 0; i < r.getChildCount(); i++) {
                View tile = r.getChildAt(i);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tile.getLayoutParams();
                lp.width = 0;
                lp.weight = 1;
                tile.setLayoutParams(lp);
                // The card and the name fill the tile's share of the row.
                for (int c = 0; c < ((ViewGroup) tile).getChildCount(); c++) {
                    View child = ((ViewGroup) tile).getChildAt(c);
                    ViewGroup.LayoutParams clp = child.getLayoutParams();
                    clp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    child.setLayoutParams(clp);
                }
            }
        }
    }

    /** Back from a tool's app: focus its tile again (opening the second row first if it was there). True if done. */
    boolean restoreFocus() {
        View tile = returnTo;
        returnTo = null;
        if (tile == null) return false;
        if (tile.getParent() == row2 && !expanded) setExpanded(true);
        tile.post(tile::requestFocus);
        return true;
    }

    /** The More tile reads Less, with an upward chevron, while the second row is open. */
    private void showMoreAs(boolean open) {
        View more = row1.findViewWithTag(MORE);
        if (!(more instanceof ViewGroup)) return;
        ViewGroup tile = (ViewGroup) more;
        ViewGroup card = (ViewGroup) tile.getChildAt(0);
        ((ImageView) card.getChildAt(0)).setImageResource(open ? R.drawable.ic_less : R.drawable.ic_more);
        ((TextView) tile.getChildAt(1)).setText(open ? R.string.utility_less : R.string.utility_more);
    }

    void setCompanion(View pane) {
        companion = pane;
    }

    UtilityRow(Activity activity, LinearLayout pane, List<String> first, List<String> more, Map<String, String> labels,
               boolean animations, Host host) {
        this(activity, pane, first, more, labels, animations, false, host);
    }

    /** pills: the Cinematic layout, one row of the first four utility_apps and All apps as wide glass pills, no More. */
    UtilityRow(Activity activity, LinearLayout pane, List<String> first, List<String> more, Map<String, String> labels,
               boolean animations, boolean pills, Host host) {
        this.activity = activity;
        this.host = host;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.animations = animations;
        this.labels = labels;
        row1 = row();
        row2 = row();
        row2.setVisibility(View.GONE);
        pane.addView(row1);
        pane.addView(row2);
        List<String> top, rest;
        this.pills = pills;
        if (pills) {
            // The first four tools, then All apps last.
            top = new ArrayList<>(installed(first));
            if (top.size() > PILLS - 1) top = new ArrayList<>(top.subList(0, PILLS - 1));
            top.add(ALL_APPS);
            rest = new ArrayList<>();
        } else {
            top = new ArrayList<>(installed(first));
            top.add(MORE);
            rest = new ArrayList<>(installed(more));
            rest.add(ALL_APPS);
        }
        fill(row1, top, true);
        fill(row2, rest, false);
    }

    boolean focusFirst() {
        return row1.getChildCount() > 0 && row1.getChildAt(0).requestFocus();
    }

    /** Lock badges after the App lock settings changed. */
    void refreshLocks() {
        for (LinearLayout r : new LinearLayout[]{row1, row2}) {
            for (int i = 0; i < r.getChildCount(); i++) {
                View tile = r.getChildAt(i);
                Object pkg = tile.getTag();
                View badge = tile.findViewWithTag("lock");
                if (badge != null) badge.setVisibility(pkg instanceof String && AppLock.isLocked(activity, (String) pkg) ? View.VISIBLE : View.GONE);
            }
        }
    }

    void collapse() {
        if (expanded) setExpanded(false);
    }

    /**
     * Opens or closes the second row. Its height grows while the suggestion pane shrinks by the same amount, from the
     * top, so the thumbnails slide up behind the header and the app rows in between do not move or leave a gap.
     */
    private void setExpanded(boolean open) {
        expanded = open;
        showMoreAs(open);
        if (slide != null) slide.cancel();
        ViewGroup.LayoutParams lp = row2.getLayoutParams();
        int width = ((View) row2.getParent()).getWidth();
        row2.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int full = row2.getMeasuredHeight();
        int from = row2.getVisibility() == View.VISIBLE ? (lp.height >= 0 ? lp.height : full) : 0;
        int to = open ? full : 0;
        if (companion != null && companionFull < 0) companionFull = companion.getHeight();
        if (open) {
            lp.height = from;
            row2.setVisibility(View.VISIBLE);
            if (companion instanceof ViewGroup) ((ViewGroup) companion).setClipChildren(true);
        }
        slide = ValueAnimator.ofInt(from, to);
        slide.setDuration(animations ? 240 : 0);
        slide.addUpdateListener(a -> {
            int h = (int) a.getAnimatedValue();
            lp.height = h;
            row2.setLayoutParams(lp);
            if (companion != null && companionFull > 0) {
                ViewGroup.LayoutParams clp = companion.getLayoutParams();
                clp.height = Math.max(0, companionFull - h);
                companion.setLayoutParams(clp);
            }
        });
        slide.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (expanded) return;
                row2.setVisibility(View.GONE);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                row2.setLayoutParams(lp);
                if (companion instanceof ViewGroup) ((ViewGroup) companion).setClipChildren(false);
            }
        });
        slide.start();
        host.onUtilityExpanded(open);
    }

    private List<String> installed(List<String> pkgs) {
        List<String> out = new ArrayList<>();
        PackageManager pm = activity.getPackageManager();
        for (String p : pkgs) {
            if (launchIntent(pm, p) != null) out.add(p);
        }
        return out;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(activity);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(dp(14), dp(4), dp(14), 0);
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void fill(LinearLayout r, List<String> keys, boolean first) {
        for (int i = 0; i < keys.size(); i++) r.addView(tile(keys.get(i), i, keys.size(), first));
    }

    /** The Cinematic layout's tool: a wide glass pill with the icon beside the name, sharing the row equally. */
    private View pill(String key, int index, int count) {
        PackageManager pm = activity.getPackageManager();
        LinearLayout pill = new LinearLayout(activity);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setFocusable(true);
        pill.setTag(key);
        pill.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        if (index > 0) lp.leftMargin = dp(GAP + 4);
        pill.setLayoutParams(lp);
        // Dark glass with a thin light edge.
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x66262C36, 0x590B0E14});
        bg.setCornerRadius(dp(23));
        bg.setStroke(dp(1), 0x4DFFFFFF);
        pill.setBackground(bg);

        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int line = pillIcon(key);
        icon.setImageDrawable(line != 0 ? activity.getDrawable(line) : iconFor(pm, key));
        pill.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView label = new TextView(activity);
        label.setText(labelFor(pm, key));
        label.setTextColor(0xEBFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        label.setTypeface(android.graphics.Typeface.create(activity.getString(R.string.font_regular), android.graphics.Typeface.NORMAL));
        label.setSingleLine(true);
        Crisp.apply(label);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(8);
        pill.addView(label, tlp);
        // A name wider than the pill (Bluetooth Devices) shrinks to fit rather than being cut.
        pill.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int room = (r - l) - pill.getPaddingLeft() - pill.getPaddingRight() - icon.getWidth() - tlp.leftMargin;
            if (room <= 0 || label.getPaint().measureText(label.getText().toString()) <= room) return;
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, label.getTextSize() * room / label.getPaint().measureText(label.getText().toString()));
        });

        pill.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.05f : 1f;
            if (animations) v.animate().scaleX(s).scaleY(s).setDuration(150).start();
            else {
                v.setScaleX(s);
                v.setScaleY(s);
            }
            // Focused: white, 70% see-through, as in the top bar and on the app tiles.
            bg.setColors(focused ? new int[]{0x4DFFFFFF, 0x4DFFFFFF} : new int[]{0x66262C36, 0x590B0E14});
            bg.setStroke(dp(focused ? 2 : 1), focused ? 0x66FFFFFF : 0x4DFFFFFF);
        });
        pill.setOnClickListener(v -> {
            returnTo = v;
            open(key);
        });
        pill.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                host.onUtilityUp();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == count - 1;
        });
        return pill;
    }

    /** A white line icon for the known tools in a pill, as the Cinematic row draws them; 0 for an app's own icon. */
    private static int pillIcon(String key) {
        switch (key) {
            case ALL_APPS: return R.drawable.ic_apps;
            case BLUETOOTH_CONNECT: return R.drawable.ic_bluetooth;
            case "io.github.luyzfernando08.sleeptimer": return R.drawable.ic_moon;
            case "io.github.maxhayim.quiethours": return R.drawable.ic_quiet;
            case "com.android.vending": return R.drawable.ic_store;
            case "io.github.sumyabazar1.weather": return R.drawable.ic_cloud;
            default: return 0;
        }
    }

    private View tile(String key, int index, int count, boolean firstRow) {
        if (pills) return pill(key, index, count);
        PackageManager pm = activity.getPackageManager();
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClipChildren(false);
        tile.setTag(key);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE_W), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index > 0) lp.leftMargin = dp(GAP);
        tile.setLayoutParams(lp);

        FrameLayout card = new FrameLayout(activity);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x33FFFFFF);
        card.setBackground(bg);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(2), 0xFFFFFFFF);
        card.setForeground(ring);
        card.getForeground().setAlpha(0);
        tile.addView(card, new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H)));

        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(BLUETOOTH_CONNECT.equals(key) ? 52 : 28), dp(28), Gravity.CENTER);
        card.addView(icon, ilp);
        icon.setImageDrawable(iconFor(pm, key));

        ImageView lock = new ImageView(activity);
        lock.setImageResource(R.drawable.ic_lock);
        lock.setTag("lock");
        lock.setVisibility(AppLock.isLocked(activity, key) ? View.VISIBLE : View.GONE);
        card.addView(lock, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.TOP | Gravity.END));
        ((FrameLayout.LayoutParams) lock.getLayoutParams()).setMargins(0, dp(5), dp(6), 0);

        TextView label = new TextView(activity);
        label.setText(labelFor(pm, key));
        label.setTextColor(0xE6FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        label.setTypeface(android.graphics.Typeface.create(activity.getString(R.string.font_medium), android.graphics.Typeface.NORMAL));
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        // The whole name: it scrolls while the tile is focused instead of ending in "...".
        label.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        label.setMarqueeRepeatLimit(-1);
        label.setHorizontalFadingEdgeEnabled(true);
        label.setSelected(true);   // a name longer than the tile scrolls round all the time
        Crisp.apply(label);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(TILE_W), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(3);
        tile.addView(label, tlp);

        tile.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.08f : 1f;
            if (animations) card.animate().scaleX(s).scaleY(s).setDuration(150).start();
            else {
                card.setScaleX(s);
                card.setScaleY(s);
            }
            card.getForeground().setAlpha(focused ? 255 : 0);
            label.setTextColor(focused ? 0xFFFFFFFF : 0xE6FFFFFF);
            label.setSelected(true);
        });
        tile.setOnClickListener(v -> {
            if (!MORE.equals(key)) returnTo = v;
            open(key);
        });
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (firstRow) {
                    collapse();
                    host.onUtilityUp();
                } else {
                    focusIn(row1, index);
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (firstRow && expanded) focusIn(row2, index);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && !firstRow) {
                collapse();
                focusIn(row1, row1.getChildCount() - 1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == count - 1;
        });
        return tile;
    }

    private void focusIn(LinearLayout r, int index) {
        if (r.getChildCount() == 0) return;
        r.getChildAt(Math.min(index, r.getChildCount() - 1)).requestFocus();
    }

    private void open(String key) {
        if (MORE.equals(key)) {
            setExpanded(!expanded);
            if (expanded) row2.post(() -> focusIn(row2, 0));
            return;
        }
        if (ALL_APPS.equals(key)) {
            host.onAllApps();
            return;
        }
        AppLock.guard(activity, key, () -> {
            Intent i = launchIntent(activity.getPackageManager(), key);
            if (i == null) return;
            try {
                activity.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                UsageRank.recordLaunch(activity, key);
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        });
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private Drawable iconFor(PackageManager pm, String key) {
        if (MORE.equals(key)) return activity.getDrawable(R.drawable.ic_more);
        if (ALL_APPS.equals(key)) return activity.getDrawable(R.drawable.ic_apps);
        if (BLUETOOTH_CONNECT.equals(key)) return activity.getDrawable(R.drawable.ic_bt_speaker);
        try {
            return pm.getApplicationIcon(key);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private String labelFor(PackageManager pm, String key) {
        if (MORE.equals(key)) return activity.getString(R.string.utility_more);
        if (ALL_APPS.equals(key)) return activity.getString(R.string.utility_all_apps);
        if (labels.containsKey(key)) return labels.get(key);
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(key, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return key;
        }
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }
}
