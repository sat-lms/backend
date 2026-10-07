package com.sat.lms.global.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ContentDisposition;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class DownloadContentDispositionTest {
    @ParameterizedTest
    @ValueSource(strings = {"report.pdf", "과제 안내.pdf", "(제출서류)AI활용 아이디어 서약서 서식.pdf",
            "경진대회 개요서 (2).pdf", "과제 최종본.v2.JAVA", "과제 안내 (최종).pdf", "report \"final\".pdf",
            "100% + ready.pdf", "filename*=example.pdf", "emoji 😀.pdf"})
    void preservesUsableNamesWithAsciiFallbackAndUtf8ExtendedFilename(String name) {
        String header = DownloadContentDisposition.create(name,"notices/1/uuid.pdf");
        assertThat(header).startsWith("attachment; filename=\"").contains("; filename*=UTF-8''");
        assertThat(header.chars()).allMatch(c -> c >= 32 && c < 127);
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(name);
        String encoded = header.substring(header.lastIndexOf("; filename*=UTF-8''") + "; filename*=UTF-8''".length());
        assertThat(encoded).doesNotContain("%2520");
        // RFC 5987 permits literal '+': unlike form encoding it does not mean a space.
        assertThat(URLDecoder.decode(encoded.replace("+", "%2B"),StandardCharsets.UTF_8)).isEqualTo(name);
        if (name.contains(" ")) assertThat(encoded).contains("%20");
        if (name.contains("과제")) assertThat(encoded).contains("%EA%B3%BC%EC%A0%9C");
        String asciiOnly = header.substring(0,header.lastIndexOf("; filename*="));
        assertThat(ContentDisposition.parse(asciiOnly).isAttachment()).isTrue();
        assertThat(ContentDisposition.parse(asciiOnly).getFilename()).isNotBlank();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "../secret.pdf", "folder/report.pdf", "folder\\report.pdf",
            "report\r\nX-Injected: yes.pdf", "bad\u0000.pdf", "bad\u007f.pdf", " report.pdf", "report.pdf ", "bad\uD800.pdf"})
    void abnormalLegacyNamesUseSafeFallbackWithoutChangingStoredMetadata(String name) {
        String header=DownloadContentDisposition.create(name,"submissions/5/uuid.PDF");
        assertThat(header).isEqualTo("attachment; filename=\"download.PDF\"; filename*=UTF-8''download.PDF");
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo("download.PDF");
    }

    @Test
    void excessivelyLongNameAndMissingOrUnsafeExtensionHaveDeterministicFallback() {
        assertThat(ContentDisposition.parse(DownloadContentDisposition.create("a".repeat(256),"notices/1/uuid.txt")).getFilename()).isEqualTo("download.txt");
        assertThat(ContentDisposition.parse(DownloadContentDisposition.create(null,"notices/1/uuid")).getFilename()).isEqualTo("download");
        assertThat(ContentDisposition.parse(DownloadContentDisposition.create(null,"notices/1/uuid.bad\r\next")).getFilename()).isEqualTo("download");
    }

    @Test
    void asciiFilenameFallbackRetainsOriginalAndEscapesQuotes() {
        String header=DownloadContentDisposition.create("report \"final\".pdf","notices/1/uuid.pdf");
        assertThat(header).startsWith("attachment; filename=\"report \\\"final\\\".pdf\";");
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo("report \"final\".pdf");
        assertThat(DownloadContentDisposition.create("report.pdf","notices/1/uuid.pdf"))
                .isEqualTo("attachment; filename=\"report.pdf\"; filename*=UTF-8''report.pdf");
    }
}
