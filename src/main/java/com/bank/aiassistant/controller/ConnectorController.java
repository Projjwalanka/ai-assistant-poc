package com.bank.aiassistant.controller;

import com.bank.aiassistant.model.dto.connector.ConnectorConfigDto;
import com.bank.aiassistant.model.entity.ConnectorConfig;
import com.bank.aiassistant.repository.ConnectorConfigRepository;
import com.bank.aiassistant.repository.UserRepository;
import com.bank.aiassistant.service.connector.ConnectorCredentialService;
import com.bank.aiassistant.service.connector.ConnectorRegistry;
import com.bank.aiassistant.service.connector.spi.ConnectorHealth;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connectors")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorConfigRepository connectorConfigRepository;
    private final ConnectorCredentialService credentialService;
    private final ConnectorRegistry connectorRegistry;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /** List all connectors for the current user */
    @GetMapping
    public ResponseEntity<List<ConnectorConfigDto>> listConnectors(
            @AuthenticationPrincipal UserDetails principal) {
        var user = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        var configs = connectorConfigRepository.findByOwnerId(user.getId());
        return ResponseEntity.ok(configs.stream().map(this::toDto).toList());
    }

    /** Get a single connector */
    @GetMapping("/{id}")
    public ResponseEntity<ConnectorConfigDto> getConnector(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails principal) {
        var user = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        return connectorConfigRepository.findByIdAndOwnerId(id, user.getId())
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create a new connector */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<ConnectorConfigDto> createConnector(
            @Valid @RequestBody ConnectorConfigDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        var user = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        ConnectorConfig config = ConnectorConfig.builder()
                .connectorType(dto.connectorType())
                .name(dto.name())
                .owner(user)
                .enabled(dto.enabled())
                .build();
        if (dto.credentials() != null && !dto.credentials().isEmpty()) {
            config.setEncryptedCredentials(credentialService.encrypt(dto.credentials()));
        }
        if (dto.config() != null) {
            try { config.setConfig(objectMapper.writeValueAsString(dto.config())); }
            catch (Exception ignored) {}
        }
        ConnectorConfig saved = connectorConfigRepository.save(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    /** Update connector config */
    @PutMapping("/{id}")
    public ResponseEntity<ConnectorConfigDto> updateConnector(
            @PathVariable String id,
            @Valid @RequestBody ConnectorConfigDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        var user = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        return connectorConfigRepository.findByIdAndOwnerId(id, user.getId())
                .map(config -> {
                    config.setName(dto.name());
                    config.setEnabled(dto.enabled());
                    if (dto.credentials() != null && !dto.credentials().isEmpty()) {
                        config.setEncryptedCredentials(credentialService.encrypt(dto.credentials()));
                        config.setVerified(false); // Re-verify after credential change
                    }
                    if (dto.config() != null) {
                        try { config.setConfig(objectMapper.writeValueAsString(dto.config())); }
                        catch (Exception ignored) {}
                    }
                    return ResponseEntity.ok(toDto(connectorConfigRepository.save(config)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Delete connector */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConnector(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails principal) {
        var user = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        connectorConfigRepository.findByIdAndOwnerId(id, user.getId())
                .ifPresent(connectorConfigRepository::delete);
        return ResponseEntity.noContent().build();
    }

    /** Test connector connectivity */
    @PostMapping("/{id}/health")
    public ResponseEntity<ConnectorHealth> healthCheck(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails principal) {
        var user = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        connectorConfigRepository.findByIdAndOwnerId(id, user.getId())
                .orElseThrow(() -> new RuntimeException("Connector not found"));
        ConnectorHealth health = connectorRegistry.healthCheck(id);
        return ResponseEntity.ok(health);
    }

    private ConnectorConfigDto toDto(ConnectorConfig config) {
        Map<String, Object> configMap = null;
        try {
            if (config.getConfig() != null)
                configMap = objectMapper.readValue(config.getConfig(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {}
        return new ConnectorConfigDto(
                config.getId(), config.getConnectorType(), config.getName(),
                null, // never return credentials
                configMap, config.isEnabled(), config.isVerified(),
                config.getLastSyncAt(), config.getLastError());
    }
}
