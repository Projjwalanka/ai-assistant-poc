package com.bank.aiassistant.repository;

import com.bank.aiassistant.model.entity.GithubContentIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GithubContentIndexRepository extends JpaRepository<GithubContentIndex, String> {

    @Modifying
    void deleteByConnectorId(String connectorId);

    @Query(value = """
            SELECT g.id,
                   g.connector_id,
                   g.user_id,
                   g.source_type,
                   g.repo,
                   g.url,
                   g.title,
                   g.body,
                   g.metadata,
                   g.source_updated_at,
                   g.ingested_at,
                   (
                     ts_rank_cd(g.search_vector, websearch_to_tsquery('english', :query)) * 0.75
                     + similarity(coalesce(g.title, '') || ' ' || g.body, :query) * 0.20
                     + CASE
                         WHEN g.source_updated_at IS NULL THEN 0.0
                         ELSE GREATEST(0.0, 1.0 - EXTRACT(EPOCH FROM (NOW() - g.source_updated_at)) / 864000.0) * 0.05
                       END
                   ) AS rank_score
            FROM github_content_index g
            WHERE g.user_id = :userId
              AND g.connector_id IN (:connectorIds)
              AND (
                    g.search_vector @@ websearch_to_tsquery('english', :query)
                    OR similarity(coalesce(g.title, '') || ' ' || g.body, :query) > 0.15
              )
            ORDER BY rank_score DESC, g.source_updated_at DESC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> searchRanked(@Param("userId") String userId,
                                @Param("connectorIds") List<String> connectorIds,
                                @Param("query") String query,
                                @Param("limit") int limit);
}
