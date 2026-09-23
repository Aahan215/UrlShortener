package com.example.urlShortener.repository;

import com.example.urlShortener.model.UrlAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AnalyticsRepository extends JpaRepository<UrlAnalytics, Long> {
    
    @Modifying
    @Query("UPDATE UrlAnalytics a SET a.hitCount = a.hitCount + 1 WHERE a.urlMapping.id = :mappingId")
    void incrementHitCount(@Param("mappingId") Long mappingId);

    Optional<UrlAnalytics> findByUrlMappingId(Long mappingId);
}
