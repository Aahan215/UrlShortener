package com.example.urlShortener.repository;

import com.example.urlShortener.model.ClickLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClickLogRepository extends JpaRepository<ClickLog, Long> {
    List<ClickLog> findByUrlMappingId(Long mappingId);
    long countByUrlMappingId(Long mappingId);
}
