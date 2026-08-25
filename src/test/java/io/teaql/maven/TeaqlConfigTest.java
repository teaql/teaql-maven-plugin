package io.teaql.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
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
    void zipDirectoryExcludesNonModelFilesWithoutMainXml() throws Exception {
        File tempDir = new File("target/test-model-dir");
        tempDir.mkdirs();
        File mainXml = new File(tempDir, "main.xml");
        File childKsml = new File(tempDir, "child.ksml");
        File garbageTxt = new File(tempDir, "garbage.txt");
        File garbagePng = new File(tempDir, "image.png");
        // Remove main.xml so fallback filter applies
        if (mainXml.exists()) mainXml.delete();
        java.nio.file.Files.write(childKsml.toPath(), "<child/>".getBytes());
        java.nio.file.Files.write(garbageTxt.toPath(), "test".getBytes());
        java.nio.file.Files.write(garbagePng.toPath(), new byte[]{1, 2, 3});

        // prepareUpload should return the single .ksml file directly
        GeneratorService genService = new GeneratorService(null);
        File result = genService.prepareUpload(tempDir);
        assertEquals("child.ksml", result.getName());

        // Clean up
        childKsml.delete();
        garbageTxt.delete();
        garbagePng.delete();
    }

    @Test
    void zipDirectoryWithMainXmlOnlyIncludesReferencedFiles() throws Exception {
        File tempDir = new File("target/test-model-graph");
        deleteRecursively(tempDir);
        tempDir.mkdirs();
        new File(tempDir, "nested").mkdirs();

        java.nio.file.Files.write(new File(tempDir, "main.xml").toPath(),
                "<_include file=\"root.xml\" />\n<_include file=\"nested/child.xml\" />".getBytes());
        java.nio.file.Files.write(new File(tempDir, "root.xml").toPath(),
                "<root/>".getBytes());
        java.nio.file.Files.write(new File(tempDir, "nested/child.xml").toPath(),
                "<child/>".getBytes());
        java.nio.file.Files.write(new File(tempDir, "garbage.xml").toPath(),
                "<garbage/>".getBytes());

        GeneratorService genService = new GeneratorService(null);
        File zipFile = genService.prepareUpload(tempDir);

        java.util.Set<String> entries = readZipEntryNames(zipFile);

        assertTrue(entries.contains("main.xml"));
        assertTrue(entries.contains("root.xml"));
        assertTrue(entries.contains("nested/child.xml"));
        assertFalse(entries.contains("garbage.xml"), "unreferenced file should be excluded");

        deleteRecursively(tempDir);
    }

    @Test
    void zipDirectoryResolvesRecursiveIncludes() throws Exception {
        File tempDir = new File("target/test-model-recursive");
        deleteRecursively(tempDir);
        tempDir.mkdirs();
        new File(tempDir, "sub").mkdirs();

        java.nio.file.Files.write(new File(tempDir, "main.xml").toPath(),
                "<_include file=\"level1.xml\" />".getBytes());
        java.nio.file.Files.write(new File(tempDir, "level1.xml").toPath(),
                "<_include file=\"sub/level2.xml\" />\n<data/>".getBytes());
        java.nio.file.Files.write(new File(tempDir, "sub/level2.xml").toPath(),
                "<leaf/>".getBytes());
        java.nio.file.Files.write(new File(tempDir, "orphan.xml").toPath(),
                "<orphan/>".getBytes());

        GeneratorService genService = new GeneratorService(null);
        File zipFile = genService.prepareUpload(tempDir);

        java.util.Set<String> entries = readZipEntryNames(zipFile);

        assertTrue(entries.contains("main.xml"));
        assertTrue(entries.contains("level1.xml"));
        assertTrue(entries.contains("sub/level2.xml"));
        assertFalse(entries.contains("orphan.xml"), "orphan file not in include graph should be excluded");

        deleteRecursively(tempDir);
    }

    @Test
    void fallbackFilterExcludesYmlFiles() throws Exception {
        File tempDir = new File("target/test-model-yaml");
        deleteRecursively(tempDir);
        tempDir.mkdirs();

        java.nio.file.Files.write(new File(tempDir, "config.yml").toPath(), "key: value".getBytes());
        java.nio.file.Files.write(new File(tempDir, "readme.txt").toPath(), "hello".getBytes());

        GeneratorService genService = new GeneratorService(null);
        try {
            genService.prepareUpload(tempDir);
            throw new AssertionError("should have thrown IOException for no model files");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("no model files"));
        }

        deleteRecursively(tempDir);
    }

    @Test
    void isModelFileAcceptsOnlyXmlAndKsml() {
        assertTrue(GeneratorService.isModelFile("model.xml"));
        assertTrue(GeneratorService.isModelFile("domain.KSML"));
        assertTrue(GeneratorService.isModelFile("path/to/nested.XML"));
        assertFalse(GeneratorService.isModelFile("config.yml"));
        assertFalse(GeneratorService.isModelFile("settings.yaml"));
        assertFalse(GeneratorService.isModelFile("readme.txt"));
        assertFalse(GeneratorService.isModelFile("image.png"));
        assertFalse(GeneratorService.isModelFile("script.py"));
    }

    // ── test helpers ─────────────────────────────────────────────────────────

    private static java.util.Set<String> readZipEntryNames(File zipFile) throws IOException {
        java.util.Set<String> entries = new java.util.HashSet<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
