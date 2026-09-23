package com.example.urlShortener.controller;

import com.example.urlShortener.exception.UrlShortenerException;
import com.example.urlShortener.model.UrlMapping;
import com.example.urlShortener.service.UrlService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.headers.Header;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/api/shorten")
    public ResponseEntity<?> shorten(@RequestBody UrlMapping request, HttpServletRequest servletRequest) {
        String longUrl = request.getLongUrl();
        String customAlias = request.getShortCode(); 

        if (longUrl == null || longUrl.isEmpty()) {
            throw new UrlShortenerException(HttpStatus.BAD_REQUEST, "The field 'url' parameter is required");
        }

        String shortCode = urlService.shortenUrl(longUrl, customAlias, request.getExpiresAt());
        
        // Dynamically detects if the request came from localhost or a live DigitalOcean domain link
        String scheme = servletRequest.getScheme();             
        String serverName = servletRequest.getServerName();     
        int serverPort = servletRequest.getServerPort();
        
        String baseDomainUrl = scheme + "://" + serverName;
        
        // Append port extension settings if running locally on localhost:8080 environment contexts
        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            baseDomainUrl += ":" + serverPort;
        }
        
        String fullShortUrl = baseDomainUrl + "/" + shortCode;
        return ResponseEntity.ok(Map.of("shortUrl", fullShortUrl, "code", shortCode));
    }

    @GetMapping("/{shortCode}")
    @Operation(
        summary = "Resolve short code and redirect",
        description = "Performs a standard browser-level HTTP 302 redirect to the original destination URL.",
        responses = {
            @ApiResponse(
                responseCode = "302", 
                description = "Redirect successful. Target URL provided in the Location header.",
                content = @Content
            ),
            @ApiResponse(responseCode = "404", description = "Short code does not exist."),
            @ApiResponse(responseCode = "410", description = "This short link has been deactivated or expired.")
        }
    )
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
            
        String longUrl = urlService.getAndTrackLongUrl(shortCode, referrer, userAgent);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(longUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/api/metadata/{shortCode}")
    public ResponseEntity<?> getMetadata(@PathVariable String shortCode) {
        UrlMapping mapping = urlService.getMetadata(shortCode);
        long hits = urlService.getHitCount(mapping.getId());
        
        List<Map<String, String>> logs = urlService.getClickLogs(mapping.getId()).stream()
            .map(log -> Map.of(
                "timestamp", log.getTimestamp().toString(),
                "referrer", log.getReferrer() != null ? log.getReferrer() : "DIRECT",
                "userAgent", log.getUserAgent() != null ? log.getUserAgent() : "UNKNOWN"
            )).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "originalUrl", mapping.getLongUrl(),
            "createdAt", mapping.getCreatedAt().toString(),
            "expiresAt", mapping.getExpiresAt() != null ? mapping.getExpiresAt().toString() : "NEVER",
            "hitCount", hits,
            "status", mapping.isActive() ? "ACTIVE" : "INACTIVE",
            "clicksInfo", logs
        ));
    }

    @PatchMapping("/api/status/{shortCode}")
    public ResponseEntity<?> toggleStatus(@PathVariable String shortCode, @RequestBody UrlMapping request) {
        urlService.updateStatus(shortCode, request.isActive());
        return ResponseEntity.ok(Map.of("message", "Link state modified successfully"));
    }
}
