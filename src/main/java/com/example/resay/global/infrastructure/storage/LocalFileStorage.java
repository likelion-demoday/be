package com.example.resay.global.infrastructure.storage;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.global.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class LocalFileStorage {

    @Value("${storage.local.base-path}")
    private String basePath;

    public String save(MultipartFile file, String extension) {
        try {
            String fileName = UUID.randomUUID() + "." + extension;
            Path targetPath = Paths.get(basePath, fileName);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            throw new GeneralException(RecordingErrorCode.VALIDATION_ERROR);
        }
    }
}
