package com.bank.aiassistant.service.ingestion;

import com.bank.aiassistant.model.entity.IngestionJob;
import com.bank.aiassistant.repository.IngestionJobRepository;
import com.bank.aiassistant.service.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Core ingestion pipeline for <b>static documents</b> (PDF, DOCX, TXT, HTML, etc.).
 *
 * <p>For live / frequently-changing data sources (Jira, Confluence, GitHub, etc.)
 * the connectors handle their own extraction and call this pipeline with pre-parsed text.
 *
 * <p>Flow:
 * <pre>
 *   Raw file bytes
 *     → DocumentReader (PDF / Tika)
 *     → DocumentChunker (sliding window, 512 tok, 64 overlap)
 *     → Metadata enrichment (source_type, connector_id, user_id, ingested_at)
 *     → EmbeddingClient (text-embedding-3-small via Spring AI)
 *     → pgvector store
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionPipeline {

    private final DocumentChunker chunker;
    private final VectorStoreService vectorStoreService;
    private final IngestionJobRepository jobRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ingest a file from raw bytes. Runs asynchronously.
     *
     * @param fileBytes   raw file content
     * @param filename    original filename (used to detect content type)
     * @param connectorId connector config ID (or "UPLOAD" for direct uploads)
     * @param extraMeta   additional metadata for retrieval filtering
     * @return future resolving to the ingestion job ID
     */
    @Async
    public CompletableFuture<String> ingestFile(byte[] fileBytes, String filename,
                                                 String connectorId,
                                                 Map<String, Object> extraMeta) {
        IngestionJob job = createJob(connectorId, filename);
        try {
            job.setStatus(IngestionJob.JobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            Resource resource = new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return filename; }
            };

            // Choose reader based on extension
            List<Document> rawDocs = filename.toLowerCase().endsWith(".pdf")
                    ? readPdf(resource)
                    : readWithTika(resource);

            // Build metadata
            Map<String, Object> meta = new java.util.HashMap<>(extraMeta != null ? extraMeta : Map.of());
            meta.put("source_ref", filename);
            meta.put("connector_id", connectorId != null ? connectorId : "UPLOAD");
            meta.put("ingested_at", Instant.now().toString());

            // Chunk + embed + store
            List<Document> chunks = chunker.chunk(rawDocs, meta);
            job.setChunksTotal(chunks.size());
            vectorStoreService.store(chunks);

            job.setChunksProcessed(chunks.size());
            job.setStatus(IngestionJob.JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            log.info("Ingested '{}': {} chunks stored. jobId={}", filename, chunks.size(), job.getId());
            return CompletableFuture.completedFuture(job.getId());

        } catch (Exception ex) {
            log.error("Ingestion failed for '{}': {}", filename, ex.getMessage(), ex);
            job.setStatus(IngestionJob.JobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Ingest pre-extracted text (used by live connectors).
     *
     * @param texts    list of (text, metadata) pairs
     * @param jobLabel label for the ingestion job record
     */
    @Async
    public CompletableFuture<String> ingestTexts(List<Map.Entry<String, Map<String, Object>>> texts,
                                                  String jobLabel, String connectorType) {
        IngestionJob job = createJob(connectorType, jobLabel);
        try {
            job.setStatus(IngestionJob.JobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

            List<Document> rawDocs = texts.stream()
                    .map(e -> new Document(e.getKey(), e.getValue()))
                    .toList();

            List<Document> chunks = chunker.chunk(rawDocs, null);
            job.setChunksTotal(chunks.size());
            vectorStoreService.store(chunks);

            job.setChunksProcessed(chunks.size());
            job.setStatus(IngestionJob.JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            return CompletableFuture.completedFuture(job.getId());

        } catch (Exception ex) {
            job.setStatus(IngestionJob.JobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            jobRepository.save(job);
            return CompletableFuture.failedFuture(ex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Readers
    // ─────────────────────────────────────────────────────────────────────────

    private List<Document> readPdf(Resource resource) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource,
                PdfDocumentReaderConfig.builder()
                        .withPagesPerDocument(1)
                        .build()
        );
        return reader.get();
    }

    private List<Document> readWithTika(Resource resource) {
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        return reader.get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private IngestionJob createJob(String connectorType, String sourceRef) {
        IngestionJob job = IngestionJob.builder()
                .connectorType(connectorType != null ? connectorType : "UNKNOWN")
                .sourceRef(sourceRef)
                .status(IngestionJob.JobStatus.PENDING)
                .build();
        return jobRepository.save(job);
    }
}
