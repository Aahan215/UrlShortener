package com.example.urlShortener.repository;

import com.example.urlShortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByShortCode(String shortCode);

    @Modifying
    @Query("UPDATE UrlMapping u SET u.active = false WHERE u.active = true AND u.expiresAt IS NOT NULL AND u.expiresAt <= :now")
    int deactivateExpiredUrls(@Param("now") LocalDateTime now);
}
