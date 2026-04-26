package com.bank.aiassistant.repository;

import com.bank.aiassistant.model.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    Page<Conversation> findByUserIdAndArchivedFalseOrderByUpdatedAtDesc(String userId, Pageable pageable);

    Optional<Conversation> findByIdAndUserId(String id, String userId);

    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages WHERE c.id = :id")
    Optional<Conversation> findByIdWithMessages(String id);

    long countByUserIdAndArchivedFalse(String userId);
}
