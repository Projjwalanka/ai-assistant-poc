package com.bank.aiassistant.repository;

import com.bank.aiassistant.model.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    List<Feedback> findByConversationId(String conversationId);

    @Query("SELECT f.category, COUNT(f) FROM Feedback f GROUP BY f.category")
    List<Object[]> countByCategory();

    @Query("SELECT AVG(f.rating) FROM Feedback f WHERE f.rating IS NOT NULL")
    Double averageRating();
}
