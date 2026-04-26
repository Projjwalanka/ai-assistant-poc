package com.bank.aiassistant.service.connector.github;

import com.bank.aiassistant.model.entity.ConnectorConfig;
import com.bank.aiassistant.repository.ConnectorConfigRepository;
import com.bank.aiassistant.service.connector.ConnectorRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubConnectorSyncService {

    private final ConnectorConfigRepository connectorConfigRepository;
    private final ConnectorRegistry connectorRegistry;
    private final GitHubContextIndexService contextIndexService;

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    @Async
    public void syncConnectorAsync(String connectorId) {
        syncConnector(connectorId);
    }

    @Transactional
    public boolean syncConnector(String connectorId) {
        ConnectorConfig config = connectorConfigRepository.findById(connectorId).orElse(null);
        if (config == null || !config.isEnabled() || !"GITHUB".equalsIgnoreCase(config.getConnectorType())) {
            return false;
        }

        try {
            List<Map.Entry<String, Map<String, Object>>> docs = connectorRegistry.fetchAll(connectorId);
            contextIndexService.replaceConnectorCorpus(config, docs);
            config.setLastSyncAt(Instant.now());
            config.setLastError(null);
            connectorConfigRepository.save(config);
            log.info("GitHub connector sync complete: connectorId={} docs={}", connectorId, docs.size());
            return true;
        } catch (Exception ex) {
            config.setLastError(truncate(ex.getMessage(), 500));
            connectorConfigRepository.save(config);
            log.error("GitHub connector sync failed: connectorId={} error={}", connectorId, ex.getMessage(), ex);
            return false;
        }
    }

    public void syncIfStale(ConnectorConfig config) {
        if (config == null || !"GITHUB".equalsIgnoreCase(config.getConnectorType()) || !config.isEnabled()) {
            return;
        }
        if (config.getLastSyncAt() == null || config.getLastSyncAt().isBefore(Instant.now().minus(STALE_AFTER))) {
            syncConnector(config.getId());
        }
    }

    @Scheduled(fixedDelayString = "${app.github.ingestion.refresh-ms:1800000}")
    public void refreshAllEnabledGithubConnectors() {
        List<ConnectorConfig> configs = connectorConfigRepository.findByConnectorTypeIgnoreCaseAndEnabledTrue("GITHUB");
        for (ConnectorConfig config : configs) {
            if (config.getLastSyncAt() == null || config.getLastSyncAt().isBefore(Instant.now().minus(STALE_AFTER))) {
                syncConnectorAsync(config.getId());
            }
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
