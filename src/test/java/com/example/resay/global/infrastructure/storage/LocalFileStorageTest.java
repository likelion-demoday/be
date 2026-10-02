package com.example.resay.global.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage localFileStorage;

    @BeforeEach
    void setUp() {
        localFileStorage = new LocalFileStorage();
        ReflectionTestUtils.setField(localFileStorage, "basePath", tempDir.toString());
    }

    @Test
    void delete_저장한_파일을_삭제() {
        MockMultipartFile file = new MockMultipartFile(
                "audioFile", "test.mp3", "audio/mpeg", "내용".getBytes());
        String savedPath = localFileStorage.save(file, "mp3");
        assertTrue(Files.exists(Paths.get(savedPath)));

        localFileStorage.delete(savedPath);

        assertFalse(Files.exists(Paths.get(savedPath)));
    }

    @Test
    void delete_없는_파일이어도_예외가_나지_않음() {
        assertDoesNotThrow(() -> localFileStorage.delete(tempDir.resolve("none.mp3").toString()));
    }
}
