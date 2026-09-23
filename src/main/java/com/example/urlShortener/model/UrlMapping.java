package com.example.urlShortener.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "url_mappings", indexes = {
    @Index(name = "idx_short_code", columnList = "shortCode")
})
public class UrlMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(nullable = false, length = 2048)
    @JsonProperty("url") 
    private String longUrl;

    @Column(unique = true, nullable = false, length = 50)
    @JsonProperty("alias") 
    private String shortCode;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private boolean isCustomAlias = false;

    @Column(nullable = false)
    @JsonProperty("active") 
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt = LocalDateTime.now();

    // New: Tracks optional explicit absolute expiration timestamp
    @Column
    @JsonProperty("expiresAt")
    private LocalDateTime expiresAt;

    // Getters and Setters
    public Long getId() { return id; }
    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public boolean isCustomAlias() { return isCustomAlias; }
    public void setCustomAlias(boolean customAlias) { this.isCustomAlias = customAlias; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
