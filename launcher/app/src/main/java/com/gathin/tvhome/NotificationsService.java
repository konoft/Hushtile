package com.gathin.tvhome;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * pane_layout's bell: the TV's notifications, which nothing shows once the stock home screen is disabled. Keeps the
 * ones worth a look (system updates, storage, Play Store, accounts) and leaves out ongoing ones (playback, downloads),
 * group summaries and marketing (CATEGORY_PROMO, CATEGORY_RECOMMENDATION). Needs notification access, granted once:
 * cmd notification allow_listener com.hushtile.home/com.gathin.tvhome.NotificationsService
 */
public final class NotificationsService extends NotificationListenerService {
    interface Listener {
        void onNotificationsChanged(List<StatusBarNotification> shown);
    }

    private static NotificationsService instance;
    private static Listener listener;
    private static final Handler main = new Handler(Looper.getMainLooper());

    static void setListener(Listener l) {
        listener = l;
        publish();
    }

    /** The notifications worth showing now, newest first; empty without access. */
    static List<StatusBarNotification> shown() {
        NotificationsService s = instance;
        if (s == null) return Collections.emptyList();
        StatusBarNotification[] all;
        try {
            all = s.getActiveNotifications();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
        List<StatusBarNotification> out = new ArrayList<>();
        if (all == null) return out;
        for (StatusBarNotification sbn : all) {
            if (keep(s, sbn)) out.add(sbn);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return out;
    }

    private static boolean keep(NotificationsService s, StatusBarNotification sbn) {
        if (sbn.isOngoing() || s.getPackageName().equals(sbn.getPackageName())) return false;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
        if (Notification.CATEGORY_PROMO.equals(n.category) || Notification.CATEGORY_RECOMMENDATION.equals(n.category)) return false;
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        return (title != null && title.length() > 0) || (text != null && text.length() > 0);
    }

    /** Removes one (if it can be cleared) or, with null, every one that can. */
    static void clear(StatusBarNotification sbn) {
        NotificationsService s = instance;
        if (s == null) return;
        try {
            if (sbn == null) s.cancelAllNotifications();
            else if (sbn.isClearable()) s.cancelNotification(sbn.getKey());
        } catch (RuntimeException ignored) {
        }
    }

    static boolean connected() {
        return instance != null;
    }

    private static void publish() {
        Listener l = listener;
        if (l == null) return;
        main.post(() -> l.onNotificationsChanged(shown()));
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        publish();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        publish();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        publish();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        publish();
    }
}
