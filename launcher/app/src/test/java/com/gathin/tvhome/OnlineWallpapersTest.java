package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

public class OnlineWallpapersTest {
    private static JSONObject picture(String file, int w, int h) throws Exception {
        String base = "https://upload.wikimedia.org/wikipedia/commons/7/7e/" + file;
        return new JSONObject()
                .put("image", new JSONObject().put("source", base + "?utm_source=x").put("width", w).put("height", h))
                .put("thumbnail", new JSONObject()
                        .put("source", "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7e/" + file + "/960px-" + file + "?utm=x"))
                .put("description", new JSONObject().put("text", "Atakule TV tower in Ankara."))
                .put("artist", new JSONObject().put("text", "A.Savin"))
                .put("license", new JSONObject().put("type", "CC BY-SA 4.0"));
    }

    @Test
    public void largeLandscapeIsFetched1920Wide() throws Exception {
        assertEquals("https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7e/A.jpg/1920px-A.jpg",
                OnlineWallpapers.pictureUrl(picture("A.jpg", 6379, 3600)));
    }

    @Test
    public void smallerLandscapeIsTheOriginal() throws Exception {
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/7/7e/A.jpeg",
                OnlineWallpapers.pictureUrl(picture("A.jpeg", 1600, 1000)));
    }

    @Test
    public void portraitsAndNonJpegsAreSkipped() throws Exception {
        assertNull(OnlineWallpapers.pictureUrl(picture("A.jpg", 3000, 4000)));
        assertNull(OnlineWallpapers.pictureUrl(picture("A.png", 4000, 2250)));
        assertNull(OnlineWallpapers.pictureUrl(picture("A.jpg", 800, 450)));
    }

    @Test
    public void captionCreditsPhotographerAndLicence() throws Exception {
        assertEquals("Atakule TV tower in Ankara · A.Savin, CC BY-SA 4.0", OnlineWallpapers.caption(picture("A.jpg", 1, 1)));
    }
}
