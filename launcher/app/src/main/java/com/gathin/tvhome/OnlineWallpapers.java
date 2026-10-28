package com.gathin.tvhome;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Launcher settings' "Get new wallpapers from Wikimedia Commons": the last eight Commons pictures of the day that suit
 * a TV (landscape JPEGs), 1920 wide, saved into the wallpapers/ collection as commons-YYYYMMDD.jpg. Each one's
 * caption in captions.txt names its photographer and licence, which CC BY and CC BY-SA ask for. No key is needed.
 */
final class OnlineWallpapers {
    private static final String FEED = "https://en.wikipedia.org/api/rest_v1/feed/featured/";
    private static final String AGENT = "Hushtile (https://github.com/konoft/Hushtile)";
    private static final int DAYS = 8;

    private OnlineWallpapers() {
    }

    /** Runs on a background thread. The number of pictures added, or -1 when the feed could not be read. */
    static int download(File folder) {
        if (!folder.isDirectory() && !folder.mkdirs()) return -1;
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        StringBuilder captions = new StringBuilder();
        int added = 0, read = 0;
        for (int i = 0; i < DAYS; i++, day.add(Calendar.DAY_OF_MONTH, -1)) {
            String date = String.format(Locale.ROOT, "%04d%02d%02d", day.get(Calendar.YEAR),
                    day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
            JSONObject image;
            try {
                String path = date.substring(0, 4) + "/" + date.substring(4, 6) + "/" + date.substring(6);
                image = new JSONObject(new String(get(FEED + path), "UTF-8")).optJSONObject("image");
                read++;
            } catch (Exception e) {
                continue;
            }
            String url = image == null ? null : pictureUrl(image);
            if (url == null) continue;
            File out = new File(folder, "commons-" + date + ".jpg");
            captions.append(out.getName()).append('\t').append(caption(image)).append('\n');
            if (out.isFile() && out.length() > 0) continue;
            try {
                byte[] data = get(url);
                File part = new File(folder, out.getName() + ".part");
                try (FileOutputStream f = new FileOutputStream(part)) {
                    f.write(data);
                }
                if (part.renameTo(out)) added++;
                else part.delete();
            } catch (Exception ignored) {
                // One picture that fails leaves the others.
            }
        }
        if (read == 0) return -1;
        appendCaptions(new File(folder, "captions.txt"), captions.toString());
        return added;
    }

    /**
     * The picture 1920 wide, or the original when it is smaller; null for a portrait or a picture that is not a JPEG.
     * Commons makes thumbnails only in its standard widths, of which 1920 is one.
     */
    static String pictureUrl(JSONObject image) {
        JSONObject original = image.optJSONObject("image");
        JSONObject thumb = image.optJSONObject("thumbnail");
        if (original == null) return null;
        int w = original.optInt("width"), h = original.optInt("height");
        String src = original.optString("source", "").replaceFirst("\\?.*$", "");
        if (w < 1280 || h <= 0 || w * 10 < h * 13 || !src.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g$")) return null;
        if (w <= 1920 || thumb == null) return src;
        String t = thumb.optString("source", "").replaceFirst("\\?.*$", "");
        return t.matches(".*/\\d+px-[^/]+$") ? t.replaceFirst("/\\d+px-([^/]+)$", "/1920px-$1") : src;
    }

    /** "Atakule TV tower in Ankara · A.Savin, CC BY-SA 4.0", short enough for one line. */
    static String caption(JSONObject image) {
        String what = text(image, "description");
        if (what.length() > 70) {
            int cut = what.lastIndexOf(' ', 67);
            what = what.substring(0, cut > 30 ? cut : 67) + "…";
        }
        String who = text(image, "artist");
        JSONObject license = image.optJSONObject("license");
        String type = license == null ? "" : license.optString("type", "").trim();
        StringBuilder out = new StringBuilder(what);
        if (!who.isEmpty() || !type.isEmpty()) {
            if (out.length() > 0) out.append(" · ");
            out.append(who);
            if (!who.isEmpty() && !type.isEmpty()) out.append(", ");
            out.append(type);
        }
        return out.length() > 0 ? out.toString() : "Wikimedia Commons";
    }

    private static String text(JSONObject image, String key) {
        JSONObject o = image.optJSONObject(key);
        String s = o == null ? "" : o.optString("text", "");
        return s.replaceAll("[\\t\\r\\n]+", " ").replaceAll(" {2,}", " ").trim().replaceAll("[.,;:]$", "");
    }

    /** Adds the lines whose picture is not captioned yet, keeping the theme's own captions. */
    private static void appendCaptions(File file, String lines) {
        if (lines.isEmpty()) return;
        String existing = "";
        try (InputStream in = new java.io.FileInputStream(file)) {
            existing = new String(readAll(in), "UTF-8");
        } catch (Exception ignored) {
        }
        StringBuilder add = new StringBuilder();
        for (String line : lines.split("\n")) {
            String name = line.substring(0, line.indexOf('\t') + 1);
            if (!existing.startsWith(name) && !existing.contains("\n" + name)) add.append(line).append('\n');
        }
        if (add.length() == 0) return;
        try (FileOutputStream f = new FileOutputStream(file, true)) {
            if (!existing.isEmpty() && !existing.endsWith("\n")) f.write('\n');
            f.write(add.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    private static byte[] get(String url) throws java.io.IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        // Wikimedia asks every client to say who it is.
        conn.setRequestProperty("User-Agent", AGENT);
        try (InputStream in = conn.getInputStream()) {
            return readAll(in);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
