package com.sat.lms.attachment.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.FileExtensionExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Component
public class AttachmentFileValidator {
    public static final int MAX_FILE_COUNT = 3;
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "hwp", "hwpx", "doc", "docx",
            "ppt", "pptx", "xls", "xlsx", "zip");

    public void validateList(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "첨부할 파일을 입력해주세요.");
        }
    }

    public void validateContents(List<MultipartFile> files) {
        long totalSize = 0;
        for (MultipartFile file : files) {
            validateFile(file);
            try {
                totalSize = Math.addExact(totalSize, file.getSize());
            } catch (ArithmeticException exception) {
                throw totalSizeExceeded();
            }
        }
        if (totalSize > MAX_TOTAL_SIZE_BYTES) throw totalSizeExceeded();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "파일 1개의 용량은 20MB를 초과할 수 없습니다.");
        }
        String originalName = file.getOriginalFilename();
        if (!isSafeOriginalName(originalName)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "올바르지 않은 파일명입니다.");
        }
        if (!ALLOWED_EXTENSIONS.contains(FileExtensionExtractor.extract(originalName))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 확장자입니다.");
        }
    }

    private boolean isSafeOriginalName(String name) {
        return name != null && !name.isBlank() && name.equals(name.trim()) && name.length() <= 255
                && !name.equals(".") && !name.equals("..") && !name.contains("..")
                && !name.contains("/") && !name.contains("\\")
                && name.chars().noneMatch(Character::isISOControl);
    }

    private BusinessException totalSizeExceeded() {
        return new BusinessException(HttpStatus.BAD_REQUEST,
                "전체 파일 용량은 50MB를 초과할 수 없습니다.");
    }
}
