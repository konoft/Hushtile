package com.gathin.tvhome;

import java.util.Locale;

/** Which sets get the gear's "Picture & Sound" row that points to the remote's Menu key. */
final class SkyworthMenu {
    private SkyworthMenu() { }

    /** A set made by Skyworth (its manufacturer) or sold as Blaupunkt (its brand or manufacturer). */
    static boolean applies(String manufacturer, String brand) {
        String m = manufacturer == null ? "" : manufacturer.toLowerCase(Locale.ROOT);
        String b = brand == null ? "" : brand.toLowerCase(Locale.ROOT);
        return m.contains("skyworth") || m.contains("blaupunkt") || b.contains("blaupunkt");
    }
}
