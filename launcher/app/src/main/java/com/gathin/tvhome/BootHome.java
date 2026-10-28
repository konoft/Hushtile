package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** power_on_home_always after a cold start: Home as soon as the boot completes (see ScreenOnHome.BOOT_GUARD_MS). */
public final class BootHome extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Config.load(context).powerOnHomeAlways) return;
        ScreenOnHome.goHome(context);
    }
}
