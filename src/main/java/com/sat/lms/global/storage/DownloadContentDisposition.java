package com.sat.lms.global.storage;

import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;

/** Download-only header policy. Does not change upload validation or stored metadata. */
final class DownloadContentDisposition {
    private DownloadContentDisposition() {}

    static String create(String originalName, String storageKey) {
        String name = isUsable(originalName) ? originalName : fallback(storageKey);
        String asciiName = name.chars().allMatch(c -> c >= 32 && c < 127) ? name : fallback(name);
        String ascii = ContentDisposition.attachment().filename(asciiName).build().toString();
        String utf8 = ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString();
        // Spring 6.2 also emits an RFC 2047 encoded-word filename. Use a plain quoted ASCII
        // fallback instead, retaining Spring's RFC 5987 filename* encoding (spaces become %20).
        return ascii + utf8.substring(utf8.lastIndexOf("; filename*="));
    }

    private static boolean isUsable(String name) {
        if (name == null || name.isBlank() || !name.equals(name.trim()) || name.length() > 255
                || name.equals(".") || name.contains("..") || name.contains("/") || name.contains("\\")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c)) return false;
            if (Character.isHighSurrogate(c)) {
                if (++i >= name.length() || !Character.isLowSurrogate(name.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    /** Keep only a short ASCII extension; never expose a UUID or a path as the fallback name. */
    private static String fallback(String source) {
        if (source != null) {
            int dot = source.lastIndexOf('.');
            if (dot >= 0) {
                String extension = source.substring(dot + 1);
                if (extension.matches("[A-Za-z0-9]{1,10}")) return "download." + extension;
            }
        }
        return "download";
    }
}
