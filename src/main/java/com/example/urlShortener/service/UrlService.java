package com.example.urlShortener.service;

import com.example.urlShortener.exception.UrlShortenerException;
import com.example.urlShortener.model.ClickLog;
import com.example.urlShortener.model.UrlMapping;
import com.example.urlShortener.repository.ClickLogRepository;
import com.example.urlShortener.repository.UrlRepository;
import com.example.urlShortener.util.Base62;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UrlService {

    private final UrlRepository urlRepository;
    private final ClickLogRepository clickLogRepository;
    private static final String ALIAS_REGEX = "^[a-zA-Z0-9_-]{3,15}$";

    public UrlService(UrlRepository urlRepository, ClickLogRepository clickLogRepository) {
        this.urlRepository = urlRepository;
        this.clickLogRepository = clickLogRepository;
    }

    @Transactional
    public String shortenUrl(String longUrl, String customAlias, LocalDateTime expiresAt) {
        // Enforce expiration boundary cannot be set in the past
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw new UrlShortenerException(HttpStatus.BAD_REQUEST, "Expiration time cannot be set in the past.");
        }

        if (customAlias != null && !customAlias.trim().isEmpty()) {
            String trimmedAlias = customAlias.trim();
            
            if (!trimmedAlias.matches(ALIAS_REGEX)) {
                throw new UrlShortenerException(HttpStatus.BAD_REQUEST, "Invalid alias. Must be 3-15 characters long and contain alphanumeric characters, hyphens, or underscores.");
            }
            if (urlRepository.findByShortCode(trimmedAlias).isPresent()) {
                throw new UrlShortenerException(HttpStatus.BAD_REQUEST, "Custom alias '" + trimmedAlias + "' is already in use.");
            }

            UrlMapping mapping = new UrlMapping();
            mapping.setLongUrl(longUrl);
            mapping.setShortCode(trimmedAlias);
            mapping.setCustomAlias(true);
            mapping.setExpiresAt(expiresAt);
            
            urlRepository.save(mapping);
            return trimmedAlias;
        }

        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl(longUrl);
        mapping.setShortCode("TEMP_HOLD_" + System.nanoTime());
        mapping.setExpiresAt(expiresAt);
        
        UrlMapping savedMapping = urlRepository.save(mapping);
        String shortCode = Base62.encode(savedMapping.getId());
        savedMapping.setShortCode(shortCode);
        urlRepository.save(savedMapping);
        
        return shortCode;
    }

    @Transactional
    public String getAndTrackLongUrl(String shortCode, String referrer, String userAgent) {
        UrlMapping mapping = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlShortenerException(HttpStatus.NOT_FOUND, "Short code does not exist."));

        // Check for runtime expiration during passive routing access
        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
            mapping.setActive(false);
            urlRepository.save(mapping);
        }

        if (!mapping.isActive()) {
            throw new UrlShortenerException(HttpStatus.GONE, "This short link has been deactivated.");
        }

        // Production audit pattern: Log contextual visit metadata information
        clickLogRepository.save(new ClickLog(mapping, referrer, userAgent));
        return mapping.getLongUrl();
    }

    @Transactional(readOnly = true)
    public UrlMapping getMetadata(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlShortenerException(HttpStatus.NOT_FOUND, "Short code does not exist."));
    }

    @Transactional(readOnly = true)
    public List<ClickLog> getClickLogs(Long mappingId) {
        return clickLogRepository.findByUrlMappingId(mappingId);
    }

    @Transactional(readOnly = true)
    public long getHitCount(Long mappingId) {
        return clickLogRepository.countByUrlMappingId(mappingId);
    }

    @Transactional
    public void updateStatus(String shortCode, boolean active) {
        UrlMapping mapping = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlShortenerException(HttpStatus.NOT_FOUND, "Short code does not exist."));
        mapping.setActive(active);
        urlRepository.save(mapping);
    }

    // Background asynchronous sweeper task to deactivate expired links every hour
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanExpiredUrls() {
        urlRepository.deactivateExpiredUrls(LocalDateTime.now());
    }
}
