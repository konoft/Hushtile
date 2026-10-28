package com.gathin.tvhome;

import android.widget.TextView;

/**
 * pane_layout's one rule for text: a faint dark shadow under every light label drawn over the wallpaper, the frosted
 * header or a translucent card. On a TV that draws at 720p and scales the picture up, it keeps the letter edges
 * apart from what is behind them, so labels read sharp instead of soft.
 */
final class Crisp {
    private Crisp() {
    }

    static void apply(TextView... views) {
        for (TextView v : views) {
            if (v == null) continue;
            float d = v.getResources().getDisplayMetrics().density;
            v.setShadowLayer(2.5f * d, 0f, 1f * d, 0xA6000000);
        }
    }
}
