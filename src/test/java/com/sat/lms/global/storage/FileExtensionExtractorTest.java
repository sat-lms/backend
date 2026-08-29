package com.sat.lms.global.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FileExtensionExtractorTest {
    @Test
    void extractsLastExtensionAndNormalizesCase() {
        assertThat(FileExtensionExtractor.extract("report.pdf")).isEqualTo("pdf");
        assertThat(FileExtensionExtractor.extract("REPORT.PDF")).isEqualTo("pdf");
        assertThat(FileExtensionExtractor.extract("archive.tar.gz")).isEqualTo("gz");
    }

    @ParameterizedTest
    @MethodSource("normalKoreanFilenames")
    void extractsExtensionFromNormalKoreanFilename(String filename, String expectedExtension) {
        assertThat(FileExtensionExtractor.extract(filename)).isEqualTo(expectedExtension);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            ".bashrc", ".sh", "a.", "a",
            "report.abcdefghijklmnopqrstu",
            "../report.pdf", "folder/report.pdf", "folder\\report.pdf"
    })
    void invalidOrMissingExtensionReturnsEmpty(String filename) {
        assertThat(FileExtensionExtractor.extract(filename)).isEmpty();
    }

    static Stream<Arguments> normalKoreanFilenames() {
        return Stream.of(
                Arguments.of("(제출서류)AI활용 아이디어 서약서 서식.pdf", "pdf"),
                Arguments.of("5월14일 실습사진.png", "png"),
                Arguments.of("경진대회 개요서 (2).pdf", "pdf"),
                Arguments.of("과제 최종본.v2.JAVA", "java")
        );
    }
}
