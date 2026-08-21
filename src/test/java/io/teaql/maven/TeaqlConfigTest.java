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

    @Test
    void defaultTimeoutIs300Seconds() {
        assertEquals(300L, TeaqlConfig.DEFAULT_TIMEOUT_SECONDS);
        TeaqlConfig config = new TeaqlConfig();
        assertEquals(300L, config.getTimeoutSeconds());
    }

    @Test
    void zipDirectoryExcludesNonXmlOrKsmlFiles() throws Exception {
        File tempDir = new File("target/test-model-dir");
        tempDir.mkdirs();
        File mainXml = new File(tempDir, "main.xml");
        File childKsml = new File(tempDir, "child.ksml");
        File garbageTxt = new File(tempDir, "garbage.txt");
        File garbagePng = new File(tempDir, "image.png");
        java.nio.file.Files.write(mainXml.toPath(), "<root/>".getBytes());
        java.nio.file.Files.write(childKsml.toPath(), "<child/>".getBytes());
        java.nio.file.Files.write(garbageTxt.toPath(), "test".getBytes());
        java.nio.file.Files.write(garbagePng.toPath(), new byte[]{1, 2, 3});

        GeneratorService genService = new GeneratorService(null);
        File zipFile = genService.prepareUpload(tempDir);

        java.util.Set<String> entries = new java.util.HashSet<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertTrue(entries.contains("main.xml"));
        assertTrue(entries.contains("child.ksml"));
        assertFalse(entries.contains("garbage.txt"));
        assertFalse(entries.contains("image.png"));
    }
}
