package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Top shelf (top_shelf): a row of the focused dock app's suggestions above the dock, read from the preview
 * programs the app publishes to the TV provider. Up from the dock moves into the row, OK opens the title in
 * its app, Down goes back to the dock. Needs READ_TV_LISTINGS (granted over ADB: pm grant) to read the rows,
 * and INTERNET for the posters, which apps publish as https links. An app that publishes nothing gets no row.
 */
final class TopShelf {

    interface Host {
        /** Down from the row: back to the dock tile. */
        void onShelfDown();

        /** Up from the row: the top bar. The default focus search finds nothing above the row. */
        void onShelfUp();

        /** A poster for the backdrop: the focused card's, or the first card's when the row appears. */
        void onShelfArt(Bitmap poster);

        /** The provider was read again; a shown row may now have more, fewer or different titles. */
        void onShelfLoaded();
    }

    private static final class Item {
        final String title, poster, intent;

        Item(String title, String poster, String intent) {
            this.title = title;
            this.poster = poster;
            this.intent = intent;
        }
    }

    private static final int MAX_ITEMS = 10;
    private static final int CARD_W = 192, CARD_H = 108, GAP = 16;
    /** Posters are decoded about this wide (px): sharp on the 1080p screen at 1.08× focus, small in memory. */
    private static final int POSTER_PX = 420;
    private static final Uri PREVIEWS = Uri.parse("content://android.media.tv/preview_program");
    private static final String[] COLUMNS = {"package_name", "title", "poster_art_uri", "thumbnail_uri", "intent_uri"};

    private final Activity activity;
    private final Host host;
    private final float density;
    private final boolean animations;
    private final LinearLayout panel;
    private final TextView kicker;
    /** pane_layout: the shelf has its own pane of fixed height; an app with no suggestions shows its name there. */
    private final boolean pane;
    private final TextView placeholder;
    /** The Cinematic layout's line under its greeting; shown with the placeholder only in greeting mode. */
    private final TextView tagline;
    /** Cinematic layout: the placeholder greets, centred, instead of naming the focused app. */
    private boolean greetingMode;
    private String greeting = "";
    private final HorizontalScrollView scroller;
    private final LinearLayout row;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> posters = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private final Set<String> failed = Collections.synchronizedSet(new HashSet<>());
    private volatile Map<String, List<Item>> byPackage = Collections.emptyMap();
    private String shownPackage;

    TopShelf(Activity activity, FrameLayout root, int index, float density, boolean animations, Host host) {
        this(activity, root, index, density, animations, false, host);
    }

    /** pane: the row goes into container (pane_layout's shelf pane) at its own fixed height, never moved. */
    TopShelf(Activity activity, ViewGroup container, int index, float density, boolean animations, boolean pane, Host host) {
        this.pane = pane;
        this.activity = activity;
        this.host = host;
        this.density = density;
        this.animations = animations;

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setAlpha(0f);
        panel.setVisibility(View.GONE);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        // Only Up from the dock enters the row: the focus finder would otherwise send Right from Inputs into it.
        panel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        kicker = new TextView(activity);
        kicker.setTextColor(pane ? 0xE6FFFFFF : 0xB3FFFFFF);
        kicker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        kicker.setLetterSpacing(0.22f);
        kicker.setAllCaps(true);
        kicker.setSingleLine(true);
        kicker.setTypeface(android.graphics.Typeface.create(activity.getString(R.string.font_medium), android.graphics.Typeface.NORMAL));
        if (pane) Crisp.apply(kicker);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.leftMargin = dp(70);
        klp.bottomMargin = dp(pane ? 2 : 4);
        panel.addView(kicker, klp);

        scroller = new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setFocusable(false);
        scroller.setSmoothScrollingEnabled(animations);

        row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // Room for the focused card to grow, and the start lined up with the clock and the dock.
        row.setPadding(dp(70), dp(pane ? 6 : 10), dp(70), dp(pane ? 2 : 6));
        scroller.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        placeholder = new TextView(activity);
        placeholder.setTextColor(0xFFFFFFFF);
        placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f);
        placeholder.setSingleLine(true);
        placeholder.setIncludeFontPadding(false);
        placeholder.setTypeface(android.graphics.Typeface.create(activity.getString(pane ? R.string.font_regular : R.string.font_light), android.graphics.Typeface.NORMAL));
        if (pane) Crisp.apply(placeholder);
        placeholder.setVisibility(View.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = dp(70);
        plp.topMargin = dp(46);
        panel.addView(placeholder, plp);
        tagline = new TextView(activity);
        tagline.setTextColor(0xFFEDEFF2);
        tagline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        tagline.setSingleLine(true);
        tagline.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        tagline.setTypeface(android.graphics.Typeface.create(activity.getString(R.string.font_medium), android.graphics.Typeface.NORMAL));
        tagline.setText(R.string.cinematic_tagline);
        Crisp.apply(tagline);
        tagline.setVisibility(View.GONE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(14);
        panel.addView(tagline, tlp);
        if (pane) {
            panel.setMinimumHeight(height());
            container.setMinimumHeight(height());
            container.setVisibility(View.VISIBLE);
            // Anchored to the bottom: when the pane gives room to the tools' second row, the top slides out of view.
            container.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height(),
                    android.view.Gravity.BOTTOM));
        } else {
            container.addView(panel, index, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        refresh();
    }

    /**
     * Cinematic layout: where the focused app has no suggestions, the pane shows this greeting, centred and larger,
     * with the tagline under it. Called again each minute as the time of day changes.
     */
    void setGreeting(String text) {
        if (!greetingMode) {
            greetingMode = true;
            placeholder.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f);
            placeholder.setTypeface(android.graphics.Typeface.create(activity.getString(R.string.font_light), android.graphics.Typeface.NORMAL));
            LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) placeholder.getLayoutParams();
            plp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            plp.leftMargin = 0;
            plp.topMargin = dp(40);
            placeholder.setLayoutParams(plp);
        }
        greeting = text == null ? "" : text;
        if (placeholder.getVisibility() == View.VISIBLE) placeholder.setText(greeting);
    }

    /** Reads every app's suggestions again, off the main thread; the shown row follows on onShelfLoaded. */
    void refresh() {
        io.execute(() -> {
            Map<String, List<Item>> read = read(activity.getContentResolver());
            main.post(() -> {
                byPackage = read;
                host.onShelfLoaded();
            });
        });
    }

    boolean hasItems(String pkg) {
        List<Item> items = pkg == null ? null : byPackage.get(pkg);
        return items != null && !items.isEmpty();
    }

    /**
     * Shows pkg's row with its bottom at bottomY (root coordinates) and returns the height it takes, 0 when the
     * app publishes nothing (and the row is hidden).
     */
    float show(String pkg, String label, float bottomY) {
        List<Item> items = pkg == null ? null : byPackage.get(pkg);
        if (items == null || items.isEmpty()) {
            if (!pane) {
                hide();
                return 0f;
            }
            // pane_layout: the pane keeps its height and names the app instead.
            if (!("name:" + pkg).equals(shownPackage)) {
                shownPackage = "name:" + pkg;
                row.removeAllViews();
                kicker.setVisibility(View.GONE);
                scroller.setVisibility(View.GONE);
                placeholder.setText(greetingMode ? greeting : label);
                placeholder.setVisibility(View.VISIBLE);
                tagline.setVisibility(greetingMode ? View.VISIBLE : View.GONE);
                panel.setVisibility(View.VISIBLE);
                panel.animate().cancel();
                panel.setAlpha(animations ? 0f : 1f);
                if (animations) panel.animate().alpha(1f).setDuration(220).start();
            }
            return height();
        }
        boolean changed = !pkg.equals(shownPackage);
        if (changed) {
            shownPackage = pkg;
            placeholder.setVisibility(View.GONE);
            tagline.setVisibility(View.GONE);
            kicker.setVisibility(View.VISIBLE);
            scroller.setVisibility(View.VISIBLE);
            kicker.setText(activity.getString(R.string.top_shelf_kicker, label));
            fill(pkg, items);
            scroller.scrollTo(0, 0);
        }
        int h = height();
        if (!pane) panel.setY(bottomY - h);
        panel.setVisibility(View.VISIBLE);
        if (changed) {
            panel.animate().cancel();
            if (animations) {
                panel.setAlpha(0f);
                panel.animate().alpha(1f).setDuration(260).start();
            } else {
                panel.setAlpha(1f);
            }
            Bitmap first = posters.get(items.get(0).poster);
            if (first != null) host.onShelfArt(first);
        } else if (panel.getAlpha() < 1f) {
            panel.animate().alpha(1f).setDuration(animations ? 200 : 0).start();
        }
        return h;
    }

    void hide() {
        if (pane || shownPackage == null) return;
        shownPackage = null;
        panel.animate().cancel();
        if (animations) {
            panel.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                if (shownPackage == null) panel.setVisibility(View.GONE);
            }).start();
        } else {
            panel.setAlpha(0f);
            panel.setVisibility(View.GONE);
        }
    }

    /** Whether v is one of the row's cards. */
    boolean contains(View v) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == panel) return true;
        }
        return false;
    }

    boolean isShown() {
        return shownPackage != null;
    }

    /** Up from the dock: focus the row's first card. False when there is no row to go to. */
    boolean focusFirst() {
        if (shownPackage == null || row.getChildCount() == 0 || placeholder.getVisibility() == View.VISIBLE) return false;
        scroller.scrollTo(0, 0);
        panel.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        return row.getChildAt(0).requestFocus();
    }

    void destroy() {
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    /** The row's full height: the kicker line, the cards with their titles, and the padding around them. */
    private int height() {
        if (pane) return dp(12 + 4 + 2) + dp(6) + dp(CARD_H) + dp(6) + dp(18) + dp(2);
        return dp(12 + 4 + 2) + dp(10) + dp(CARD_H) + dp(8) + dp(18) + dp(6);
    }

    private void fill(String pkg, List<Item> items) {
        row.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            row.addView(card(pkg, items.get(i), i, items.size()));
        }
    }

    private View card(String pkg, Item item, int index, int count) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClipChildren(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(CARD_W), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index > 0) clp.leftMargin = dp(GAP);
        card.setLayoutParams(clp);

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setCornerRadius(dp(10));
        placeholder.setColor(0x26FFFFFF);
        image.setBackground(placeholder);
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(3), 0xFFFFFFFF);
        image.setForeground(ring);
        image.getForeground().setAlpha(0);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
            }
        });
        image.setClipToOutline(true);
        card.addView(image, new LinearLayout.LayoutParams(dp(CARD_W), dp(CARD_H)));

        TextView title = new TextView(activity);
        title.setText(item.title);
        title.setTextColor(pane ? 0xD9FFFFFF : 0x99FFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTypeface(android.graphics.Typeface.create(activity.getString(pane ? R.string.font_medium : R.string.font_regular), android.graphics.Typeface.NORMAL));
        if (pane) Crisp.apply(title);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(CARD_W), dp(18));
        tlp.topMargin = dp(pane ? 6 : 8);
        card.addView(title, tlp);

        bindPoster(image, item.poster);

        card.setOnFocusChangeListener((v, focused) -> {
            float s = focused ? 1.08f : 1f;
            if (animations) {
                image.animate().scaleX(s).scaleY(s).translationY(focused ? -dp(4) : 0f).setDuration(160).start();
            } else {
                image.setScaleX(s);
                image.setScaleY(s);
            }
            image.getForeground().setAlpha(focused ? 255 : 0);
            image.setElevation(focused ? dp(8) : 0f);
            title.setTextColor(focused ? 0xFFFFFFFF : pane ? 0xD9FFFFFF : 0x99FFFFFF);
            if (!focused) main.post(() -> {
                if (!panel.hasFocus()) panel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            });
            if (focused) {
                Bitmap art = posters.get(item.poster);
                if (art != null) host.onShelfArt(art);
                // The whole card on screen, the same 70dp from the edge as the row's start, never half cut off.
                scroller.post(() -> keepInView(card));
            }
        });
        card.setOnClickListener(v -> AppLock.guard(activity, pkg, () -> open(pkg, item)));
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
                host.onShelfDown();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                host.onShelfUp();
                return true;
            }
            // The row loops: Right on the last card goes round to the first, Left on the first to the last.
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0 && count > 1) {
                row.getChildAt(count - 1).requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == count - 1 && count > 1) {
                row.getChildAt(0).requestFocus();
                // Back to the row's own start, the first card lined up with the clock, not flush with the edge.
                scroller.post(() -> {
                    if (animations) scroller.smoothScrollTo(0, 0);
                    else scroller.scrollTo(0, 0);
                });
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? count <= 1 : false;
        });
        return card;
    }

    /** Scrolls the row just enough that this card, zoomed, sits fully inside the screen with the row's margin. */
    private void keepInView(View card) {
        int margin = dp(70);
        int left = card.getLeft() - dp(10), right = card.getRight() + dp(10);   // room for the focus zoom
        int x = scroller.getScrollX(), w = scroller.getWidth();
        int target = x;
        if (left - x < margin) target = left - margin;
        else if (right - x > w - margin) target = right - w + margin;
        target = Math.max(0, target);
        if (target == x) return;
        if (animations) scroller.smoothScrollTo(target, 0);
        else scroller.scrollTo(target, 0);
    }

    private void bindPoster(ImageView image, String uri) {
        image.setTag(uri);
        Bitmap cached = posters.get(uri);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        if (failed.contains(uri)) return;
        ContentResolver cr = activity.getContentResolver();
        io.execute(() -> {
            Bitmap bmp = fetch(cr, uri);
            main.post(() -> {
                if (bmp == null) {
                    failed.add(uri);
                    return;
                }
                posters.put(uri, bmp);
                if (uri.equals(image.getTag())) {
                    image.setImageBitmap(bmp);
                    if (animations) {
                        image.setAlpha(0f);
                        image.animate().alpha(1f).setDuration(200).start();
                    }
                }
                // The first poster of the shown row is the backdrop until a card takes focus.
                List<Item> items = shownPackage == null ? null : byPackage.get(shownPackage);
                if (items != null && !items.isEmpty() && uri.equals(items.get(0).poster) && !row.hasFocus()) {
                    host.onShelfArt(bmp);
                }
            });
        });
    }

    /** Opens the title in its own app; the app's front screen if it cannot. */
    private void open(String pkg, Item item) {
        // Back on Home, focus is on the app's tile, not on a card of a row that may have changed meanwhile.
        host.onShelfDown();
        Intent intent = null;
        try {
            intent = Intent.parseUri(item.intent, Intent.URI_INTENT_SCHEME);
        } catch (Exception ignored) {
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // An https link (Netflix, Apple TV) would otherwise open a browser chooser, not the app.
            if (intent.getPackage() == null && intent.getComponent() == null) intent.setPackage(pkg);
            try {
                activity.startActivity(intent);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                try {
                    activity.startActivity(intent.setPackage(null));
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                }
            }
        }
        Intent front = activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (front == null) front = activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if (front != null) {
            try {
                activity.startActivity(front.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    /**
     * Every app's previews in the provider's order, at most MAX_ITEMS each, without untitled rows (Zee5's profile
     * avatars) or repeats. The query names its columns: on this firmware the provider's own full projection asks for
     * columns its database does not have. Empty without READ_TV_LISTINGS.
     */
    private static Map<String, List<Item>> read(ContentResolver cr) {
        Map<String, List<Item>> out = new HashMap<>();
        Map<String, Set<String>> seen = new HashMap<>();
        try (Cursor c = cr.query(PREVIEWS, COLUMNS, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                String pkg = c.getString(0), title = c.getString(1), intent = c.getString(4);
                String poster = !TextUtils.isEmpty(c.getString(2)) ? c.getString(2) : c.getString(3);
                if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(title) || TextUtils.isEmpty(poster) || TextUtils.isEmpty(intent)) continue;
                List<Item> list = out.computeIfAbsent(pkg, k -> new ArrayList<>());
                if (list.size() >= MAX_ITEMS) continue;
                if (!seen.computeIfAbsent(pkg, k -> new HashSet<>()).add(title)) continue;
                list.add(new Item(title, poster, intent));
            }
        } catch (Exception ignored) {
            // No permission, or a provider that refuses: no rows.
        }
        return out;
    }

    /** One poster, decoded about POSTER_PX wide; null when it cannot be had. */
    private static Bitmap fetch(ContentResolver cr, String uri) {
        try {
            byte[] data;
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = conn.getInputStream()) {
                    data = readAll(in);
                } finally {
                    conn.disconnect();
                }
            } else {
                try (InputStream in = cr.openInputStream(Uri.parse(uri))) {
                    if (in == null) return null;
                    data = readAll(in);
                }
            }
            if (data == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (bounds.outWidth / (opts.inSampleSize * 2) >= POSTER_PX) opts.inSampleSize *= 2;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** The whole stream, or null past 8 MB (no poster is that large). */
    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > 8 * 1024 * 1024) return null;
        }
        return out.toByteArray();
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }
}
