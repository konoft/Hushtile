package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MemoryTidyTest {
    private static final long GB = 1024L * 1024 * 1024;

    @Test
    public void twoGigabyteTvsCountAsLowRam() {
        assertTrue(MemoryTidy.lowRam(1_811_760L * 1024, false));   // OnePlus 65U1S, "2 GB"
        assertTrue(MemoryTidy.lowRam(4 * GB, true));               // Android's own low-RAM flag
        assertFalse(MemoryTidy.lowRam(3 * GB, false));
        assertFalse(MemoryTidy.lowRam(0, false));                  // unknown: leave everything alone
    }

    @Test
    public void bootPassWaitsForTheAppsThatStartThemselves() {
        assertEquals(55_000, MemoryTidy.bootPassDelay(20_000));
        assertTrue(MemoryTidy.bootPassDelay(90_000) <= 0);
        assertTrue(MemoryTidy.booting(30_000));
        assertFalse(MemoryTidy.booting(10 * 60_000));
    }

    @Test
    public void closesOnlyIdleAppsThatAreNotKept() {
        List<String> installed = Arrays.asList("com.hushtile.home", "in.startv.hotstar", "com.sonyliv",
                "com.netflix.ninja", "io.github.luyzfernando08.sleeptimer", "com.spotify.tv.android");
        Set<String> keep = new HashSet<>(Arrays.asList("io.github.luyzfernando08.sleeptimer", "com.spotify.tv.android"));
        Map<String, Long> lastUsed = new HashMap<>();
        lastUsed.put("com.netflix.ninja", 10_000L);   // just watched
        lastUsed.put("com.sonyliv", 1_000L);          // used before the window
        List<String> close = MemoryTidy.targets(installed, "com.hushtile.home", keep, lastUsed, 5_000L);
        assertEquals(Arrays.asList("in.startv.hotstar", "com.sonyliv"), close);
    }

    @Test
    public void ownersOfServiceComponentsAreKept() {
        Set<String> keep = new HashSet<>();
        MemoryTidy.addOwners(keep, "com.hushtile.home/com.gathin.tvhome.HomeKeyService:com.example.a11y/.Svc");
        MemoryTidy.addOwners(keep, null);
        assertEquals(new HashSet<>(Arrays.asList("com.hushtile.home", "com.example.a11y")), keep);
        assertEquals(Collections.emptyList(),
                MemoryTidy.targets(Collections.singletonList("com.example.a11y"), "x", keep, new HashMap<>(), 0));
    }
}
