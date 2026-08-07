package com.sat.lms.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "공통 페이지네이션 응답 형식")
public class PageResponse<T> {

    @Schema(description = "조회된 데이터 목록")
    private final List<T> content;

    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    private final int page;

    @Schema(description = "페이지 크기", example = "20")
    private final int size;

    @Schema(description = "전체 데이터 개수", example = "42")
    private final long totalElements;

    @Schema(description = "전체 페이지 수", example = "3")
    private final int totalPages;

    private PageResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}