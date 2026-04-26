package com.bank.aiassistant.service.connector.github;

import com.bank.aiassistant.model.entity.ConnectorConfig;
import com.bank.aiassistant.model.entity.GithubContentIndex;
import com.bank.aiassistant.repository.GithubContentIndexRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubContextIndexService {

    private final GithubContentIndexRepository repository;
    private final ObjectMapper objectMapper;

    public record IndexedHit(
            String connectorId,
            String sourceType,
            String repo,
            String url,
            String title,
            String body,
            Map<String, Object> metadata,
            double score
    ) {}

    @Transactional
    public void replaceConnectorCorpus(ConnectorConfig connector,
                                       List<Map.Entry<String, Map<String, Object>>> documents) {
        repository.deleteByConnectorId(connector.getId());
        if (documents == null || documents.isEmpty()) {
            return;
        }

        List<GithubContentIndex> entities = new ArrayList<>(documents.size());
        for (Map.Entry<String, Map<String, Object>> entry : documents) {
            Map<String, Object> meta = entry.getValue() != null ? new HashMap<>(entry.getValue()) : new HashMap<>();
            String sourceType = asString(meta.getOrDefault("content_type", "unknown"));
            String title = deriveTitle(entry.getKey(), sourceType);
            String body = normalizeBody(entry.getKey());
            String url = asString(meta.getOrDefault("url",
                    "https://github.com/" + asString(meta.getOrDefault("repo", "unknown"))));
            Instant sourceUpdatedAt = parseInstant(meta.get("updated_at"));

            entities.add(GithubContentIndex.builder()
                    .connectorId(connector.getId())
                    .userId(connector.getOwner().getId())
                    .sourceType(sourceType)
                    .repo(asString(meta.get("repo")))
                    .url(url)
                    .title(title)
                    .body(body)
                    .metadata(meta)
                    .sourceUpdatedAt(sourceUpdatedAt)
                    .build());
        }
        repository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public List<IndexedHit> search(String userId, List<String> connectorIds, String query, int limit) {
        if (connectorIds == null || connectorIds.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = repository.searchRanked(userId, connectorIds, query, limit);
        List<IndexedHit> hits = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> metadata = new HashMap<>();
            try {
                Object raw = row[8];
                if (raw != null) {
                    if (raw instanceof Map<?, ?> mapValue) {
                        metadata = objectMapper.convertValue(mapValue, Map.class);
                    } else {
                        metadata = objectMapper.readValue(raw.toString(), Map.class);
                    }
                }
            } catch (Exception ignored) {
            }
            hits.add(new IndexedHit(
                    row[1] != null ? row[1].toString() : null,
                    row[3] != null ? row[3].toString() : "unknown",
                    row[4] != null ? row[4].toString() : null,
                    row[5] != null ? row[5].toString() : null,
                    row[6] != null ? row[6].toString() : null,
                    row[7] != null ? row[7].toString() : "",
                    metadata,
                    row[11] != null ? Double.parseDouble(row[11].toString()) : 0.0
            ));
        }
        return hits;
    }

    private String deriveTitle(String body, String sourceType) {
        if (body == null || body.isBlank()) {
            return sourceType.toUpperCase() + " item";
        }
        String firstLine = body.lines().findFirst().orElse(body).trim();
        if (firstLine.length() > 180) {
            return firstLine.substring(0, 180);
        }
        return firstLine;
    }

    private String normalizeBody(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().replace("\u0000", "");
        return cleaned.length() > 16000 ? cleaned.substring(0, 16000) : cleaned;
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
