package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** The two TVs Hushtile runs on find their own examples/ folder from what Android reports, and an unknown TV none. */
public class DeviceProfileTest {
    private static JSONArray profiles() throws Exception {
        String text = new String(Files.readAllBytes(Paths.get("src/main/assets/profiles.json")), StandardCharsets.UTF_8);
        return new JSONObject(text).getJSONArray("profiles");
    }

    @Test
    public void nokiaFindsItsFolder() throws Exception {
        // ro.product.brand NOKIA, manufacturer SCBC, model ATV R1, device R1, board rt41
        DeviceProfile.Identity tv = new DeviceProfile.Identity("NOKIA SCBC ATV R1", "R1", "NOKIA", "rt41", "rtd284o", "ATV R1");
        assertEquals("nokia-atv-r1", DeviceProfile.match(profiles(), tv).getString("id"));
    }

    @Test
    public void onePlusCebuFindsItsFolder() throws Exception {
        DeviceProfile.Identity tv = new DeviceProfile.Identity("OnePlus OnePlus OnePlus TV", "Cebu", "Cebu", "m7632", "m7632", "C00");
        assertEquals("oneplus-tv-cebu", DeviceProfile.match(profiles(), tv).getString("id"));
    }

    @Test
    public void anUnknownTvMatchesNothing() throws Exception {
        DeviceProfile.Identity tv = new DeviceProfile.Identity("Acme Acme Vision 50", "vision50", "vision50", "unknown", "mt5862", "Vision 50");
        assertNull(DeviceProfile.match(profiles(), tv));
    }

    @Test
    public void hiddenAppsAddUp() throws Exception {
        JSONObject look = new JSONObject("{\"hidden_apps\":[\"a\"],\"columns\":6}");
        DeviceProfile.merge(look, new JSONObject("{\"hidden_apps\":[\"b\"],\"columns\":5}"));
        assertEquals(2, look.getJSONArray("hidden_apps").length());
        assertEquals(5, look.getInt("columns"));
    }
}
