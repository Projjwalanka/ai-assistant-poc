package com.bank.aiassistant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * Serves generated artifacts (PDFs, Excel files, JSON exports).
 */
@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    @Value("${app.artifacts.output-dir:${java.io.tmpdir}/ai-artifacts}")
    private String outputDir;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf",  "application/pdf",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "json", "application/json",
            "png",  "image/png",
            "jpg",  "image/jpeg"
    );

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable String filename) {
        File file = Path.of(outputDir, filename).toFile();
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        String extension = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                : "octet-stream";
        String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
