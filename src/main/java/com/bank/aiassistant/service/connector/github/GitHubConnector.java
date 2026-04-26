package com.bank.aiassistant.service.connector.github;

import com.bank.aiassistant.model.entity.ConnectorConfig;
import com.bank.aiassistant.service.connector.ConnectorCredentialService;
import com.bank.aiassistant.service.connector.spi.ConnectorHealth;
import com.bank.aiassistant.service.connector.spi.ConnectorType;
import com.bank.aiassistant.service.connector.spi.DataSourceConnector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * GitHub connector — searches issues, PRs, and code via GitHub REST API v3 + Search API.
 *
 * <p>Required credentials: {@code personalAccessToken}
 * <p>Config: {@code org}, {@code repos} (comma-separated repo names)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubConnector implements DataSourceConnector {

    private final ConnectorCredentialService credentialService;
    private final ObjectMapper objectMapper;

    private static final String GITHUB_API = "https://api.github.com";

    @Override
    public ConnectorType getType() { return ConnectorType.GITHUB; }

    @Override
    public ConnectorHealth healthCheck(ConnectorConfig config) {
        long start = System.currentTimeMillis();
        try {
            Map<String, String> creds = credentialService.decrypt(config.getEncryptedCredentials());
            buildClient(creds).get().uri("/user")
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
            return ConnectorHealth.ok(System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return ConnectorHealth.error(ex.getMessage());
        }
    }

    @Override
    public List<Map.Entry<String, Map<String, Object>>> query(ConnectorConfig config,
                                                               String query, int maxResults) {
        List<Map.Entry<String, Map<String, Object>>> results = new ArrayList<>();
        try {
            Map<String, String> creds = credentialService.decrypt(config.getEncryptedCredentials());
            String org = getConfigValue(config, "org");
            String qualifier = org != null ? " org:" + org : "";

            // Search issues and PRs
            String searchUrl = "/search/issues?q=" +
                    java.net.URLEncoder.encode(query + qualifier, java.nio.charset.StandardCharsets.UTF_8) +
                    "&per_page=" + maxResults;
            String body = buildClient(creds).get().uri(searchUrl)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(15));
            results.addAll(parseSearchResults(body));
        } catch (Exception ex) {
            log.error("GitHub query failed: {}", ex.getMessage());
        }
        return results;
    }

    @Override
    public List<Map.Entry<String, Map<String, Object>>> fetchAll(ConnectorConfig config) {
        return query(config, "is:open", 50);
    }

    private WebClient buildClient(Map<String, String> creds) {
        return WebClient.builder()
                .baseUrl(GITHUB_API)
                .defaultHeader("Authorization", "Bearer " + creds.get("personalAccessToken"))
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    private String getConfigValue(ConnectorConfig config, String key) {
        try {
            if (config.getConfig() != null)
                return objectMapper.readTree(config.getConfig()).path(key).asText(null);
        } catch (Exception ignored) {}
        return null;
    }

    private List<Map.Entry<String, Map<String, Object>>> parseSearchResults(String json) {
        List<Map.Entry<String, Map<String, Object>>> results = new ArrayList<>();
        try {
            JsonNode items = objectMapper.readTree(json).get("items");
            if (items == null) return results;
            items.forEach(item -> {
                String title = item.path("title").asText();
                String body = item.path("body").asText("");
                String url = item.path("html_url").asText();
                String state = item.path("state").asText();
                String number = item.path("number").asText();
                String content = String.format("[GitHub #%s] %s\nState: %s\n%s",
                        number, title, state,
                        body.length() > 2000 ? body.substring(0, 2000) + "…" : body);
                results.add(Map.entry(content, Map.of(
                        "source_type", "GITHUB",
                        "number", number,
                        "title", title,
                        "url", url
                )));
            });
        } catch (Exception ex) {
            log.error("Failed to parse GitHub response: {}", ex.getMessage());
        }
        return results;
    }
}
