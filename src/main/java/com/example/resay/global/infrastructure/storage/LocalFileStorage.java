package com.example.resay.global.infrastructure.storage;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.global.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
public class LocalFileStorage {

    @Value("${storage.local.base-path}")
    private String basePath;

    public String save(MultipartFile file, String extension) {
        Path targetPath = Paths.get(basePath, UUID.randomUUID() + "." + extension);
        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            delete(targetPath.toString());
            throw new GeneralException(RecordingErrorCode.VALIDATION_ERROR);
        }
    }

    // 정리 실패가 원래 예외를 가리지 않도록 삭제 실패는 로그만 남기고 결과만 돌려준다
    // 이미 없는 파일도 삭제된 것으로 본다 (이전 실행에서 지우고 기록만 실패한 경우)
    public boolean delete(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
            return true;
        } catch (IOException e) {
            log.warn("저장 파일 삭제 실패: {}", filePath, e);
            return false;
        }
    }
}
