package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SkyworthMenuTest {
    @Test
    public void skyworthAndBlaupunktGetTheRow() {
        assertTrue(SkyworthMenu.applies("skyworth", "SW"));      // the Blaupunkt SWTV-20AE
        assertTrue(SkyworthMenu.applies("Skyworth", "Karbonn"));
        assertTrue(SkyworthMenu.applies("SPPL", "Blaupunkt"));
        assertTrue(SkyworthMenu.applies("BLAUPUNKT", null));
    }

    @Test
    public void everyOtherTvKeepsItsMenu() {
        assertFalse(SkyworthMenu.applies("SCBC", "Nokia"));
        assertFalse(SkyworthMenu.applies("OnePlus", "OnePlus"));
        assertFalse(SkyworthMenu.applies("TCL", "TCL"));
        assertFalse(SkyworthMenu.applies(null, null));
    }
}
