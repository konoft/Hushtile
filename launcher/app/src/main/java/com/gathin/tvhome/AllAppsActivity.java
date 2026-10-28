package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * pane_layout's All apps: every installed app in a 6-column grid, by name. OK opens it (after the PIN when it is
 * locked), holding OK opens its App info. Back returns to Home.
 */
public final class AllAppsActivity extends Activity {
    private static final int COLUMNS = 6;

    private static final class App {
        final String pkg, label;
        final Intent launch;
        final Drawable banner, icon;

        App(String pkg, String label, Intent launch, Drawable banner, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.launch = launch;
            this.banner = banner;
            this.icon = icon;
        }
    }

    private float density;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        List<App> apps = load();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xF20B0E14);
        page.setPadding(dp(56), dp(28), dp(56), 0);

        TextView title = new TextView(this);
        title.setText(getString(R.string.all_apps_title, apps.size()));
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
        title.setTypeface(android.graphics.Typeface.create(getString(R.string.font_medium), android.graphics.Typeface.NORMAL));
        page.addView(title);

        RecyclerView grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(0, dp(14), 0, dp(30));
        grid.setItemAnimator(null);
        grid.setAdapter(new Tiles(apps));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        glp.topMargin = dp(8);
        page.addView(grid, glp);
        setContentView(page);
        grid.post(() -> {
            RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
            if (first != null) first.itemView.requestFocus();
        });
    }

    /** Every app with a TV or phone entry point, one per package, by name. */
    private List<App> load() {
        PackageManager pm = getPackageManager();
        List<App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                // A system app with only a phone-style entry is a maker's tool (the factory menu, a disclaimer page,
                // a Netflix-key helper), never something to open by mistake from here.
                boolean system = (ri.activityInfo.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                if (Intent.CATEGORY_LAUNCHER.equals(category) && system) continue;
                if (!seen.add(pkg)) continue;
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                Drawable banner = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category) ? ri.activityInfo.loadBanner(pm) : null;
                if (banner != null && !AppAdapter.isBannerShaped(banner.getIntrinsicWidth(), banner.getIntrinsicHeight())) banner = null;
                out.add(new App(pkg, String.valueOf(ri.loadLabel(pm)), launch, banner, banner == null ? ri.loadIcon(pm) : null));
            }
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private void open(App app) {
        AppLock.guard(this, app.pkg, () -> {
            try {
                startActivity(app.launch);
                UsageRank.recordLaunch(this, app.pkg);
                finish();
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        });
    }

    private final class Tiles extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<App> apps;

        Tiles(List<App> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int cell = (parent.getWidth() > 0 ? parent.getWidth() : getResources().getDisplayMetrics().widthPixels - dp(112)) / COLUMNS;
            int w = cell - dp(22), h = Math.round(w * 9f / 16f);
            LinearLayout tile = new LinearLayout(AllAppsActivity.this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setFocusable(true);
            tile.setClipChildren(false);
            tile.setPadding(0, dp(8), 0, dp(10));
            tile.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            FrameLayout card = new FrameLayout(AllAppsActivity.this);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            bg.setColor(0xFF1C1C1E);
            card.setBackground(bg);
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(12));
            ring.setStroke(dp(3), 0xFFFFFFFF);
            card.setForeground(ring);
            card.getForeground().setAlpha(0);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                }
            });
            card.setClipToOutline(true);
            tile.addView(card, new LinearLayout.LayoutParams(w, h));

            ImageView banner = new ImageView(AllAppsActivity.this);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ImageView icon = new ImageView(AllAppsActivity.this);
            card.addView(icon, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            ImageView lock = new ImageView(AllAppsActivity.this);
            lock.setImageResource(R.drawable.ic_lock);
            lock.setBackgroundResource(R.drawable.lock_badge_bg);
            lock.setPadding(dp(3), dp(3), dp(3), dp(3));
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP | Gravity.END);
            llp.setMargins(0, dp(5), dp(5), 0);
            card.addView(lock, llp);

            TextView name = new TextView(AllAppsActivity.this);
            name.setTextColor(0xE6FFFFFF);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            name.setMarqueeRepeatLimit(-1);
            name.setHorizontalFadingEdgeEnabled(true);
            name.setSelected(true);   // a name longer than the tile scrolls round all the time
            name.setTypeface(android.graphics.Typeface.create(getString(R.string.font_medium), android.graphics.Typeface.NORMAL));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(6);
            tile.addView(name, nlp);

            tile.setOnFocusChangeListener((v, focused) -> {
                float s = focused ? 1.08f : 1f;
                card.animate().scaleX(s).scaleY(s).setDuration(140).start();
                card.getForeground().setAlpha(focused ? 255 : 0);
                name.setTextColor(focused ? 0xFFFFFFFF : 0xE6FFFFFF);
                name.setSelected(true);
            });
            return new RecyclerView.ViewHolder(tile) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            App app = apps.get(position);
            LinearLayout tile = (LinearLayout) holder.itemView;
            FrameLayout card = (FrameLayout) tile.getChildAt(0);
            ImageView banner = (ImageView) card.getChildAt(0);
            ImageView icon = (ImageView) card.getChildAt(1);
            View lock = card.getChildAt(2);
            TextView name = (TextView) tile.getChildAt(1);
            banner.setImageDrawable(app.banner);
            banner.setVisibility(app.banner != null ? View.VISIBLE : View.GONE);
            icon.setImageDrawable(app.icon);
            icon.setVisibility(app.banner == null ? View.VISIBLE : View.GONE);
            lock.setVisibility(AppLock.isLocked(AllAppsActivity.this, app.pkg) ? View.VISIBLE : View.GONE);
            name.setText(app.label);
            tile.setOnClickListener(v -> open(app));
            tile.setOnLongClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            });
            tile.setOnKeyListener((v, keyCode, event) -> event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_MENU && v.performLongClick());
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }
}
