package com.bank.aiassistant.repository;

import com.bank.aiassistant.model.entity.ConnectorConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectorConfigRepository extends JpaRepository<ConnectorConfig, String> {

    List<ConnectorConfig> findByOwnerIdAndEnabledTrue(String ownerId);

    List<ConnectorConfig> findByOwnerId(String ownerId);

    Optional<ConnectorConfig> findByIdAndOwnerId(String id, String ownerId);

    boolean existsByOwnerIdAndConnectorTypeAndName(String ownerId, String connectorType, String name);
}
