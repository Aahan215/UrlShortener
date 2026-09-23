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
        clickLogRepository.deleteAll();
        urlRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void testShortenUrl_Success_AutoGeneration_EnforcesMinimumLength() throws Exception {
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(matchesRegex("^[a-zA-Z0-9]{3,}$")))
                .andExpect(jsonPath("$.shortUrl").exists());
    }

    @Test
    void testShortenUrl_Success_CustomAlias() throws Exception {
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://github.com\", \"alias\": \"my-promo-link\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("my-promo-link"))
                .andExpect(jsonPath("$.shortUrl").value(containsString("/my-promo-link")));
    }

    @Test
    void testShortenUrl_Success_WithValidFutureExpirationTTL() throws Exception {
        String futureExpiration = LocalDateTime.now().plusDays(7).toString();

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\", \"expiresAt\": \"" + futureExpiration + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void testRedirect_Success_CapturesVisitMetadata_IncrementsHitCounters() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl("https://google.com");
        mapping.setShortCode("go-link");
        mapping.setCustomAlias(true);
        urlRepository.save(mapping);

        mockMvc.perform(get("/go-link")
                .header("User-Agent", "Mozilla/5.0 TestBrowser")
                .header("Referer", "https://test-source.com"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));

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

        UrlMapping updated = urlRepository.findByShortCode("fig-link").orElseThrow();
        assertFalse(updated.isActive());
    }

    @Test
    void testShortenUrl_Failure_MissingUrlParameter() throws Exception {
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alias\": \"broken-link\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("The field 'url' parameter is required"));
    }

    @Test
    void testShortenUrl_Failure_InvalidAliasCharacters() throws Exception {
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\", \"alias\": \"bad link!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid alias. Must be 3-15 characters long and contain alphanumeric characters, hyphens, or underscores."));
    }

    @Test
    void testShortenUrl_Failure_AliasTooShort() throws Exception {
        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://spring.io\", \"alias\": \"go\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid alias. Must be 3-15 characters long and contain alphanumeric characters, hyphens, or underscores."));
    }

    @Test
    void testShortenUrl_Failure_PastExpirationTTL() throws Exception {
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

        mockMvc.perform(post("/api/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\": \"https://yahoo.com\", \"alias\": \"taken\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Custom alias 'taken' is already in use."));
    }

    @Test
    void testRedirect_Failure_CodeDoesNotExist() throws Exception {
        mockMvc.perform(get("/missing-shortcut-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void testRedirect_Failure_LinkIsDeactivated() throws Exception {
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl("https://apple.com");
        mapping.setShortCode("expired-link");
        mapping.setActive(false); 
        urlRepository.save(mapping);

        mockMvc.perform(get("/expired-link"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("This short link has been deactivated."));
    }
}
