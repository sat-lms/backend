package com.sat.lms.global.storage;

import java.util.Locale;
import java.util.regex.Pattern;

public final class FileExtensionExtractor {
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final Pattern EXTENSION = Pattern.compile("[A-Za-z0-9]{1,20}");

    private FileExtensionExtractor() {
    }

    public static String extract(String filename) {
        if (!isSafeFilename(filename)) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(lastDot + 1);
        if (!EXTENSION.matcher(extension).matches()) {
            return "";
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    private static boolean isSafeFilename(String filename) {
        return filename != null && !filename.isBlank() && filename.equals(filename.trim())
                && filename.length() <= MAX_FILENAME_LENGTH
                && !filename.equals(".") && !filename.equals("..") && !filename.contains("..")
                && !filename.contains("/") && !filename.contains("\\")
                && filename.chars().noneMatch(Character::isISOControl);
    }
}
