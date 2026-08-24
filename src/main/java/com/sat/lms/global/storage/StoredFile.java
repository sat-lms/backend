package com.sat.lms.global.storage;

public record StoredFile(
        String originalName,
        String storedName,
        String storageKey,
        String extension,
        Long sizeKb
) {
}
