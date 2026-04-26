package com.bank.aiassistant.controller;

import com.bank.aiassistant.model.entity.IngestionJob;
import com.bank.aiassistant.repository.IngestionJobRepository;
import com.bank.aiassistant.service.ingestion.IngestionPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionPipeline ingestionPipeline;
    private final IngestionJobRepository jobRepository;

    /**
     * Upload one or more files for ingestion into the vector store.
     * Supports: PDF, DOCX, XLSX, TXT, HTML, MD
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "connectorId", required = false) String connectorId,
            @AuthenticationPrincipal UserDetails principal) throws Exception {

        Map<String, Object> meta = Map.of(
                "user_id", principal.getUsername(),
                "source_type", "DOCUMENTS"
        );
        CompletableFuture<String> future = ingestionPipeline.ingestFile(
                file.getBytes(), file.getOriginalFilename(), connectorId, meta);

        // Return immediately with job ID — client polls /api/ingestion/jobs/{id}
        return ResponseEntity.accepted().body(Map.of("message", "Ingestion started",
                "filename", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown"));
    }

    /**
     * List recent ingestion jobs.
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<IngestionJob>> listJobs() {
        return ResponseEntity.ok(jobRepository.findTop20ByOrderByCreatedAtDesc());
    }

    /**
     * Get a specific ingestion job status.
     */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<IngestionJob> getJob(@PathVariable String id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
