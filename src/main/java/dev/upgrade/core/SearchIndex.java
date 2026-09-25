package dev.upgrade.core;

import java.text.Normalizer;
import java.util.Locale;

/** Kept independent of Minecraft so Unicode/name matching can be regression tested. */
public final class SearchIndex {
    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replace('ё', 'е').replace('_', ' ').replaceAll("\\s+", " ").strip();
    }
    public static boolean matches(String query, String id, String displayName) {
        String haystack = normalize(displayName + " " + id);
        for (String token : normalize(query).split(" ")) if (!haystack.contains(token)) return false;
        return true;
    }
}
