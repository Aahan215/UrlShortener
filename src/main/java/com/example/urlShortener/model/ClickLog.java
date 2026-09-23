package com.example.urlShortener.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "click_logs", indexes = {
    @Index(name = "idx_mapping_id", columnList = "url_mapping_id")
})
public class ClickLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_mapping_id", nullable = false)
    private UrlMapping urlMapping;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column(length = 1024)
    private String referrer;

    @Column(length = 1024)
    private String userAgent;

    public ClickLog() {}

    public ClickLog(UrlMapping urlMapping, String referrer, String userAgent) {
        this.urlMapping = urlMapping;
        this.referrer = referrer;
        this.userAgent = userAgent;
    }

    // Getters
    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getReferrer() { return referrer; }
    public String getUserAgent() { return userAgent; }
}
