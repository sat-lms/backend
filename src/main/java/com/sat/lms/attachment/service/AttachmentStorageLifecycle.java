package com.sat.lms.attachment.service;

import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Component
public class AttachmentStorageLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AttachmentStorageLifecycle.class);
    private static final int MAX_DELETE_ATTEMPTS = 3;
    private static final long DELETE_RETRY_DELAY_MILLIS = 100;

    private final FileStorage fileStorage;

    public AttachmentStorageLifecycle(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public boolean registerRollbackCompensation(List<StoredFile> uploaded) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        List<StoredFile> requestFiles = List.copyOf(uploaded);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) compensate(requestFiles);
            }
        });
        return true;
    }

    public void compensate(List<StoredFile> uploaded) {
        for (StoredFile stored : uploaded) deleteWithRetry(stored.storageKey());
    }

    public void deleteAfterCommit(List<String> storageKeys) {
        List<String> keys = List.copyOf(storageKeys);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(keys);
                }
            });
        } else {
            deleteQuietly(keys);
        }
    }

    private void deleteQuietly(List<String> storageKeys) {
        for (String storageKey : storageKeys) deleteWithRetry(storageKey);
    }

    private void deleteWithRetry(String storageKey) {
        for (int attempt = 1; attempt <= MAX_DELETE_ATTEMPTS; attempt++) {
            try {
                fileStorage.delete(storageKey);
                return;
            } catch (RuntimeException exception) {
                if (attempt == MAX_DELETE_ATTEMPTS) {
                    log.error("S3 object cleanup failed after {} attempts", MAX_DELETE_ATTEMPTS);
                    return;
                }
                log.warn("Retrying S3 object cleanup (attempt {}/{})", attempt, MAX_DELETE_ATTEMPTS);
                if (!sleepBeforeRetry()) return;
            }
        }
    }

    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(DELETE_RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting to retry S3 object cleanup");
            return false;
        }
    }
}
