package com.bank.aiassistant.repository;

import com.bank.aiassistant.model.entity.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, String> {
    List<IngestionJob> findByStatusOrderByCreatedAtDesc(IngestionJob.JobStatus status);
    List<IngestionJob> findTop20ByOrderByCreatedAtDesc();
}
