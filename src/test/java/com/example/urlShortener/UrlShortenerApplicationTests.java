package com.example.urlShortener;

import com.example.urlShortener.model.UrlMapping;
import com.example.urlShortener.repository.ClickLogRepository;
import com.example.urlShortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ClickLogRepository clickLogRepository;

    @BeforeEach
    void setUp() {
        // Enforce strict test data isolation across every test phase context execution run
        clickLogRepository.deleteAll();
        urlRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        // Core sanity test validating the framework container successfully bootstrapped
    }

    // =========================================================================
    //   HAPPY PATH / SUCCESS TEST SCENARIOS
    // =========================================================================

    @Test
    void testShortenUrl_Success_AutoGeneration_EnforcesMinimumLength() throws Exception {
        // Verify code auto-generation is at least 3 characters long due to Base62 padding rules
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(matchesRegex("^[a-zA-Z0-9]{3,}$")))
                .andExpect(jsonPath("$.shortUrl").exists());
    }

    @Test
    void testShortenUrl_Success_CustomAlias() throws Exception {
        // Verify valid custom alias can be assigned alongside the destination url mapping
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://github.com\", \"alias\": \"my-promo-link\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("my-promo-link"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/my-promo-link"));
    }

    @Test
    void testShortenUrl_Success_WithValidFutureExpirationTTL() throws Exception {
        // Verify custom links configured with valid future expiration times map successfully
        String futureExpiration = LocalDateTime.now().plusDays(7).toString();

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\", \"expiresAt\": \"" + futureExpiration + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void testRedirect_Success_CapturesVisitMetadata_IncrementsHitCounters() throws Exception {
        // 1. Seed base test model mapping rows directly into database context
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl("https://google.com");
        mapping.setShortCode("go-link");
        mapping.setCustomAlias(true);
        UrlMapping saved = urlRepository.save(mapping);

        // 2. Simulate browser traffic visits equipped with metadata headers
        mockMvc.perform(get("/go-link")
                .header("User-Agent", "Mozilla/5.0 TestBrowser")
                .header("Referer", "https://test-source.com"))
                .andExpect(status().isFound()) // Verifies it returns an HTTP 302
                .andExpect(header().string("Location", "https://google.com"));


        // 3. Query metadata payload endpoint to verify historical logs are cleanly indexed
        mockMvc.perform(get("/api/metadata/go-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hitCount").value(1))
                .andExpect(jsonPath("$.clicksInfo", hasSize(1)))
                .andExpect(jsonPath("$.clicksInfo[0].userAgent").value("Mozilla/5.0 TestBrowser"))
                .andExpect(jsonPath("$.clicksInfo[0].referrer").value("https://test-source.com"));
    }

    @Test
    void testToggleStatus_Success_Deactivation() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl("https://figma.com");
        mapping.setShortCode("fig-link");
        urlRepository.save(mapping);

        mockMvc.perform(patch("/api/status/fig-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Link state modified successfully"));

        // Prove row properties directly flipped active flag state cleanly to false
        UrlMapping updated = urlRepository.findByShortCode("fig-link").orElseThrow();
        assertFalse(updated.isActive());
    }

    // =========================================================================
    //   FAILURE / VALIDATION TEST SCENARIOS
    // =========================================================================

    @Test
    void testShortenUrl_Failure_MissingUrlParameter() throws Exception {
        // Enforce global handler maps missing url parameter requirements cleanly to 400 Bad Request
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\": \"broken-link\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("The field 'url' parameter is required"));
    }

    @Test
    void testShortenUrl_Failure_InvalidAliasCharacters() throws Exception {
        // Custom alias using forbidden symbols must instantly fail regex constraints
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\", \"alias\": \"bad link!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid alias. Must be 3-15 characters long and contain alphanumeric characters, hyphens, or underscores."));
    }

    @Test
    void testShortenUrl_Failure_AliasTooShort() throws Exception {
        // Custom alias using length shorter than 3 characters must fail validation boundaries
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\", \"alias\": \"go\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid alias. Must be 3-15 characters long and contain alphanumeric characters, hyphens, or underscores."));
    }

    @Test
    void testShortenUrl_Failure_PastExpirationTTL() throws Exception {
        // Creating links with absolute timestamps locked in the past is disallowed
        String pastExpiration = LocalDateTime.now().minusDays(2).toString();

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\", \"expiresAt\": \"" + pastExpiration + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Expiration time cannot be set in the past."));
    }

    @Test
    void testShortenUrl_Failure_DuplicateAliasConflict() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl("https://google.com");
        mapping.setShortCode("taken");
        urlRepository.save(mapping);

        // Intercept duplicate generation paths and handle structural name collisions gracefully
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://yahoo.com\", \"alias\": \"taken\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Custom alias 'taken' is already in use."));
    }

    @Test
    void testRedirect_Failure_CodeDoesNotExist() throws Exception {
        // Looking up unregistered shortcuts must return a standard HTTP 404 Not Found
        mockMvc.perform(get("/missing-shortcut-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void testRedirect_Failure_PassiveExpirationTrigger() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl("https://apple.com");
        mapping.setShortCode("timed-out");
        mapping.setExpiresAt(LocalDateTime.now().minusSeconds(10)); // Manually expire link row
        urlRepository.save(mapping);

        // Accessing an expired link should trigger a passive state change and return an HTTP 410 Gone status
        mockMvc.perform(get("/timed-out"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("This short link has been deactivated."));
    }
}
