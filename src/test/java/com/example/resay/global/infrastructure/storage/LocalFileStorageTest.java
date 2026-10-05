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

        boolean deleted = localFileStorage.delete(savedPath);

        assertTrue(deleted);
        assertFalse(Files.exists(Paths.get(savedPath)));
    }

    @Test
    void delete_없는_파일은_이미_삭제된_것으로_본다() {
        assertDoesNotThrow(() -> assertTrue(localFileStorage.delete(tempDir.resolve("none.mp3").toString())));
    }

    @Test
    void delete_지우지_못하면_false를_반환() throws Exception {
        // 내용이 있는 폴더는 지울 수 없어 삭제 실패 상황을 만든다
        Path notEmptyDir = Files.createDirectories(tempDir.resolve("not-empty"));
        Files.writeString(notEmptyDir.resolve("inner.mp3"), "내용");

        assertFalse(localFileStorage.delete(notEmptyDir.toString()));
    }
}
