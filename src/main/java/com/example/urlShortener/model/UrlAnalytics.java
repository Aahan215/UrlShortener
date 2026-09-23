package com.example.urlShortener.model;

import jakarta.persistence.*;

@Entity
@Table(name = "url_analytics")
public class UrlAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Decoupled foreign key approach prevents Hibernate entity sequence blocks
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_mapping_id", nullable = false, unique = true)
    private UrlMapping urlMapping;

    @Column(nullable = false)
    private long hitCount = 0;

    public UrlAnalytics() {}

    public UrlAnalytics(UrlMapping urlMapping) {
        this.urlMapping = urlMapping;
    }

    public Long getId() { return id; }
    public UrlMapping getUrlMapping() { return urlMapping; }
    public long getHitCount() { return hitCount; }
    public void setHitCount(long hitCount) { this.hitCount = hitCount; }
}
