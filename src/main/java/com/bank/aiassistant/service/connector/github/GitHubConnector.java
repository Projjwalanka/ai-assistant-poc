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
 * GitHub connector — fetches repos, READMEs, issues, and PRs for vector ingestion
 * and supports live search via the GitHub Search API.
 *
 * <p>Supports both PAT ({@code personalAccessToken}) and OAuth tokens ({@code accessToken}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubConnector implements DataSourceConnector {

    private final ConnectorCredentialService credentialService;
    private final ObjectMapper objectMapper;

    private static final String GITHUB_API = "https://api.github.com";
    private static final int ITEMS_PER_REPO = 30;
    private static final int MAX_REPOS = 20;
    private static final int README_TRUNCATE = 8000;
    private static final int BODY_TRUNCATE = 3000;

    @Override
    public ConnectorType getType() { return ConnectorType.GITHUB; }

    @Override
    public boolean supportsBatchIngestion() { return true; }

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
            String qualifier = (org != null && !org.isBlank()) ? " org:" + org : "";

            String searchUrl = "/search/issues?q="
                    + java.net.URLEncoder.encode(query + qualifier, java.nio.charset.StandardCharsets.UTF_8)
                    + "&per_page=" + maxResults;
            String body = buildClient(creds).get().uri(searchUrl)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(15));
            results.addAll(parseIssueSearchResults(body, config.getId()));
        } catch (Exception ex) {
            log.error("GitHub live query failed for connector {}: {}", config.getId(), ex.getMessage());
        }
        return results;
    }

    /**
     * Full batch ingestion: repositories, READMEs, open issues, open pull requests.
     * Called on connector save and manual sync.
     */
    @Override
    public List<Map.Entry<String, Map<String, Object>>> fetchAll(ConnectorConfig config) {
        List<Map.Entry<String, Map<String, Object>>> all = new ArrayList<>();
        try {
            Map<String, String> creds = credentialService.decrypt(config.getEncryptedCredentials());
            WebClient client = buildClient(creds);
            String org = getConfigValue(config, "org");

            List<JsonNode> repos = fetchRepos(client, org);
            log.info("GitHub fetchAll: {} repos for connector={}", repos.size(), config.getId());

            for (JsonNode repo : repos) {
                String fullName = repo.path("full_name").asText();
                String repoUrl = repo.path("html_url").asText();
                String defaultBranch = repo.path("default_branch").asText("main");

                // Repository summary document
                all.add(repoSummaryEntry(repo, config.getId()));

                // README
                String readme = fetchReadme(client, fullName);
                if (readme != null && !readme.isBlank()) {
                    String truncated = readme.length() > README_TRUNCATE
                            ? readme.substring(0, README_TRUNCATE) + "\n…(truncated)" : readme;
                    all.add(Map.entry(
                            "# README — " + fullName + "\n\n" + truncated,
                            repoMeta("readme", config.getId(), fullName,
                                    repoUrl + "/blob/" + defaultBranch + "/README.md")
                    ));
                }

                // Open issues (no PRs)
                fetchIssues(client, fullName, config.getId(), all);

                // Open pull requests
                fetchPullRequests(client, fullName, config.getId(), all);
            }

        } catch (Exception ex) {
            log.error("GitHub fetchAll failed for connector {}: {}", config.getId(), ex.getMessage(), ex);
        }
        log.info("GitHub fetchAll complete: {} documents for connector={}", all.size(), config.getId());
        return all;
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private List<JsonNode> fetchRepos(WebClient client, String org) {
        try {
            String url = (org != null && !org.isBlank())
                    ? "/orgs/" + org + "/repos?type=all&sort=pushed&per_page=" + MAX_REPOS
                    : "/user/repos?sort=pushed&per_page=" + MAX_REPOS + "&affiliation=owner,collaborator";
            String body = client.get().uri(url)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(20));
            JsonNode arr = objectMapper.readTree(body);
            List<JsonNode> repos = new ArrayList<>();
            if (arr.isArray()) arr.forEach(repos::add);
            return repos;
        } catch (Exception e) {
            log.error("Failed to fetch repos: {}", e.getMessage());
            return List.of();
        }
    }

    private String fetchReadme(WebClient client, String repoFullName) {
        try {
            String metaBody = client.get().uri("/repos/" + repoFullName + "/readme")
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
            JsonNode meta = objectMapper.readTree(metaBody);
            String downloadUrl = meta.path("download_url").asText(null);
            if (downloadUrl == null || downloadUrl.isBlank()) return null;
            return WebClient.builder().build().get().uri(downloadUrl)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
        } catch (Exception e) {
            return null;
        }
    }

    private void fetchIssues(WebClient client, String repoFullName, String connectorId,
                              List<Map.Entry<String, Map<String, Object>>> out) {
        try {
            String url = "/repos/" + repoFullName + "/issues?state=open&per_page=" + ITEMS_PER_REPO
                    + "&sort=updated&direction=desc";
            String body = client.get().uri(url)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(15));
            JsonNode arr = objectMapper.readTree(body);
            if (!arr.isArray()) return;
            arr.forEach(item -> {
                if (item.has("pull_request")) return; // issues endpoint also returns PRs
                String number = item.path("number").asText();
                String title = item.path("title").asText();
                String issueBody = item.path("body").asText("");
                String url2 = item.path("html_url").asText();
                String state = item.path("state").asText();
                String labels = formatLabels(item.path("labels"));
                String text = String.format(
                        "GitHub Issue #%s [%s] — %s\nRepository: %s  Labels: %s\n\n%s",
                        number, state.toUpperCase(), title, repoFullName, labels,
                        issueBody.length() > BODY_TRUNCATE ? issueBody.substring(0, BODY_TRUNCATE) + "…" : issueBody
                );
                out.add(Map.entry(text, issueMeta("issue", connectorId, repoFullName, "#" + number, url2)));
            });
        } catch (Exception e) {
            log.debug("Failed to fetch issues for {}: {}", repoFullName, e.getMessage());
        }
    }

    private void fetchPullRequests(WebClient client, String repoFullName, String connectorId,
                                    List<Map.Entry<String, Map<String, Object>>> out) {
        try {
            String url = "/repos/" + repoFullName + "/pulls?state=open&per_page=" + ITEMS_PER_REPO
                    + "&sort=updated&direction=desc";
            String body = client.get().uri(url)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(15));
            JsonNode arr = objectMapper.readTree(body);
            if (!arr.isArray()) return;
            arr.forEach(item -> {
                String number = item.path("number").asText();
                String title = item.path("title").asText();
                String prBody = item.path("body").asText("");
                String url2 = item.path("html_url").asText();
                String headBranch = item.path("head").path("ref").asText();
                String baseBranch = item.path("base").path("ref").asText();
                String text = String.format(
                        "GitHub Pull Request #%s [OPEN] — %s\nRepository: %s  Branch: %s → %s\n\n%s",
                        number, title, repoFullName, headBranch, baseBranch,
                        prBody.length() > BODY_TRUNCATE ? prBody.substring(0, BODY_TRUNCATE) + "…" : prBody
                );
                out.add(Map.entry(text, issueMeta("pull_request", connectorId, repoFullName, "#" + number, url2)));
            });
        } catch (Exception e) {
            log.debug("Failed to fetch PRs for {}: {}", repoFullName, e.getMessage());
        }
    }

    private Map.Entry<String, Map<String, Object>> repoSummaryEntry(JsonNode repo, String connectorId) {
        String fullName = repo.path("full_name").asText();
        String text = String.format(
                "GitHub Repository: %s\nDescription: %s\nLanguage: %s | Stars: %s | Forks: %s | Open Issues: %s\nDefault Branch: %s\nURL: %s",
                fullName,
                repo.path("description").asText("No description"),
                repo.path("language").asText("Unknown"),
                repo.path("stargazers_count").asText("0"),
                repo.path("forks_count").asText("0"),
                repo.path("open_issues_count").asText("0"),
                repo.path("default_branch").asText("main"),
                repo.path("html_url").asText()
        );
        return Map.entry(text, repoMeta("repo_summary", connectorId, fullName, repo.path("html_url").asText()));
    }

    private Map<String, Object> repoMeta(String contentType, String connectorId, String repo, String url) {
        return Map.of(
                "source_type", "GITHUB",
                "connector_id", connectorId,
                "content_type", contentType,
                "repo", repo,
                "url", url
        );
    }

    private Map<String, Object> issueMeta(String contentType, String connectorId, String repo,
                                           String number, String url) {
        return Map.of(
                "source_type", "GITHUB",
                "connector_id", connectorId,
                "content_type", contentType,
                "repo", repo,
                "number", number,
                "url", url
        );
    }

    private List<Map.Entry<String, Map<String, Object>>> parseIssueSearchResults(String json, String connectorId) {
        List<Map.Entry<String, Map<String, Object>>> results = new ArrayList<>();
        try {
            JsonNode items = objectMapper.readTree(json).get("items");
            if (items == null) return results;
            items.forEach(item -> {
                String number = item.path("number").asText();
                String title = item.path("title").asText();
                String body = item.path("body").asText("");
                String url = item.path("html_url").asText();
                String state = item.path("state").asText();
                String repoName = item.path("repository_url").asText()
                        .replace("https://api.github.com/repos/", "");
                String text = String.format("[GitHub #%s] %s\nRepo: %s | State: %s\n%s",
                        number, title, repoName, state,
                        body.length() > BODY_TRUNCATE ? body.substring(0, BODY_TRUNCATE) + "…" : body);
                results.add(Map.entry(text,
                        issueMeta("issue", connectorId != null ? connectorId : "unknown",
                                repoName, "#" + number, url)));
            });
        } catch (Exception ex) {
            log.error("Failed to parse GitHub search response: {}", ex.getMessage());
        }
        return results;
    }

    private String formatLabels(JsonNode labels) {
        if (!labels.isArray() || labels.isEmpty()) return "none";
        List<String> names = new ArrayList<>();
        labels.forEach(l -> names.add(l.path("name").asText()));
        return String.join(", ", names);
    }

    private WebClient buildClient(Map<String, String> creds) {
        // Support both OAuth access token and personal access token
        String token = creds.getOrDefault("accessToken",
                creds.getOrDefault("personalAccessToken", ""));
        return WebClient.builder()
                .baseUrl(GITHUB_API)
                .defaultHeader("Authorization", "Bearer " + token)
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
}
