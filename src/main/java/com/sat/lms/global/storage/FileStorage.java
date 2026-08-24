package com.sat.lms.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {
    StoredFile upload(MultipartFile file, String directory);
    void delete(String storageKey);
    String createDownloadUrl(String storageKey);
}
