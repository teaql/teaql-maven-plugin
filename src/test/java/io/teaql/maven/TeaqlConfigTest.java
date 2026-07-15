package io.teaql.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

class TeaqlConfigTest {

    @Test
    void normalizesEndpointPrefixes() {
        assertEquals(TeaqlConfig.DEFAULT_ENDPOINT_PREFIX,
                TeaqlConfig.normalizeEndpointPrefix(null));
        assertEquals(TeaqlConfig.DEFAULT_ENDPOINT_PREFIX,
                TeaqlConfig.normalizeEndpointPrefix("  "));
        assertEquals("https://example.test/api/",
                TeaqlConfig.normalizeEndpointPrefix("https://example.test/api"));
        assertEquals("https://example.test/api/",
                TeaqlConfig.normalizeEndpointPrefix("https://example.test/api/"));
    }

    @Test
    void buildsEndpointUrlsWithoutDuplicateSeparators() {
        assertEquals("https://example.test/api/generate",
                TeaqlConfig.endpointUrl("https://example.test/api", "generate"));
        assertEquals("https://example.test/api/generate",
                TeaqlConfig.endpointUrl("https://example.test/api/", "/generate"));
    }

    @Test
    void mojoOverridesTakePrecedenceAndRelativeBuildDirectoryUsesProjectDirectory() {
        TeaqlConfig config = new TeaqlConfig();
        ConfigOverrides overrides = new ConfigOverrides(
                "https://example.test/service",
                null,
                "test-api-key",
                "generated-output",
                45L);
        File projectDirectory = new File("target/test-project").getAbsoluteFile();

        ResolvedConfig resolved = config.resolve(overrides, projectDirectory);

        assertEquals("https://example.test/service/", resolved.getEndpointPrefix());
        assertEquals("test-api-key", resolved.getApiKey());
        assertEquals(new File(projectDirectory, "generated-output").getAbsoluteFile(),
                resolved.getBuildDir());
        assertEquals(45L, resolved.getTimeoutSeconds());
    }

    @Test
    void diagnosticsMaskApiKeys() {
        String secret = "header.payload.signature";
        ResolvedConfig resolved = new ResolvedConfig(
                "https://example.test/",
                secret,
                new File("target/output"),
                30L,
                "test endpoint",
                "test key",
                "test directory",
                "test timeout");

        String sources = resolved.describeSources();
        String summary = resolved.toString();

        assertTrue(sources.contains("api_key          = ********"));
        assertTrue(summary.contains("apiKey='********'"));
        assertFalse(sources.contains(secret));
        assertFalse(summary.contains(secret));
    }
}
