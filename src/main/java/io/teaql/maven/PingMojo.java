package io.teaql.maven;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pings the TeaQL service using the built-in demo model.
 *
 * <p>Runs a full end-to-end smoke test with detailed step-by-step diagnostics
 * including timestamps, parameters, generated file listing, and error display.
 *
 * <p>Equivalent to {@code cargo-teaql ping}.
 *
 * <pre>mvn teaql:ping</pre>
 */
@Mojo(name = "ping",
      requiresProject = false,
      threadSafe = true)
public class PingMojo extends AbstractMojo {

    /** Override the TeaQL endpoint prefix, e.g. {@code https://api.teaql.io/latest/}. */
    @Parameter(property = "teaql.endpointPrefix")
    private String endpointPrefix;

    /** Override the TeaQL service URL. @deprecated use {@code teaql.endpointPrefix}. */
    @Deprecated
    @Parameter(property = "teaql.serviceUrl")
    private String serviceUrl;

    /** Override the API Key. */
    @Parameter(property = "teaql.apiKey")
    private String apiKey;

    /** Override the request timeout in seconds. */
    @Parameter(property = "teaql.timeoutSeconds", defaultValue = "0")
    private long timeoutSeconds;

    /** Injected Maven project (optional). */
    @Parameter(defaultValue = "${project}", readonly = true, required = false)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        long totalStart = System.currentTimeMillis();

        // ── load config ───────────────────────────────────────────────────────
        TeaqlConfig fileConfig;
        try {
            fileConfig = TeaqlConfig.load();
        } catch (IOException e) {
            throw new MojoExecutionException("failed to load TeaQL config", e);
        }

        ConfigOverrides overrides = new ConfigOverrides(
                endpointPrefix, serviceUrl, apiKey,
                System.getProperty("java.io.tmpdir") + "/teaql-ping",
                timeoutSeconds
        );

        File cwd = (project != null) ? project.getBasedir()
                : new File(System.getProperty("user.dir"));
        ResolvedConfig resolved = fileConfig.resolve(overrides, cwd);

        getLog().info(resolved.describeSources());

        String generateUrl = TeaQLService.endpointUrl(resolved.getEndpointPrefix(), "generate");

        // ── [1] Configuration ────────────────────────────────────────────────
        step(1, "Configuration");
        log("    endpoint_prefix : " + resolved.getEndpointPrefix());
        log("    generate url    : " + generateUrl);
        log("    timeout         : " + resolved.getTimeoutSeconds() + "s");
        log("    api_key         : " + (resolved.getApiKey() != null ? "********" : "null"));
        log("    build_dir       : " + resolved.getBuildDir().getAbsolutePath());

        // ── [2] Write demo model ─────────────────────────────────────────────
        step(2, "Writing built-in demo model to temp file");
        long t = System.currentTimeMillis();
        String demoModel = loadBundledDemoModel();
        File modelFile;
        try {
            modelFile = File.createTempFile("teaql-ping-model-", ".xml");
            modelFile.deleteOnExit();
            java.nio.file.Files.write(modelFile.toPath(), demoModel.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MojoExecutionException("failed to write demo model", e);
        }
        log("    written to      : " + modelFile.getAbsolutePath());
        log("    size            : " + demoModel.length() + " bytes");
        elapsed(t);



        // ── [4] Build HTTP client ────────────────────────────────────────────
        step(4, "Building HTTP client");
        t = System.currentTimeMillis();
        int timeoutMs = (int) (resolved.getTimeoutSeconds() * 1000L);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .build();
        elapsed(t);



        // ── [6] POST to service ──────────────────────────────────────────────
        step(6, "Sending request to TeaQL service");
        log("    POST            : " + generateUrl);
        log("    scope           : rust-lib");
        t = System.currentTimeMillis();

        byte[] modelBytes = demoModel.getBytes(StandardCharsets.UTF_8);

        int statusCode;
        byte[] responseBody;
        String statusLine;

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost post = new HttpPost(generateUrl);
            if (resolved.getApiKey() != null) {
                post.setHeader("Authorization", "Bearer " + resolved.getApiKey());
            }
            post.setEntity(MultipartEntityBuilder.create()
                    .addPart("file", new ByteArrayBody(modelBytes, "demo-service.xml"))
                    .addTextBody("scope", "rust-lib")
                    .build());

            try (CloseableHttpResponse response = client.execute(post)) {
                statusLine = response.getStatusLine().toString();
                statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                responseBody = EntityUtils.toByteArray(entity);
            }
        } catch (IOException e) {
            log("");
            log("  \u2717  PING FAILED \u2014 network error");
            log("     " + e.getMessage());
            log("     total elapsed: " + elapsedMs(totalStart) + "ms");
            throw new MojoExecutionException("PING FAILED: " + e.getMessage(), e);
        }

        elapsed(t);
        log("    HTTP status     : " + statusLine);

        // ── [7] Read response ────────────────────────────────────────────────
        step(7, "Reading response body");
        t = System.currentTimeMillis();
        log("    body size       : " + responseBody.length + " bytes");
        elapsed(t);

        if (statusCode < 200 || statusCode >= 300) {
            String body = new String(responseBody, StandardCharsets.UTF_8).trim();
            log("");
            log("  \u2717  PING FAILED \u2014 service returned HTTP " + statusCode);
            log("     " + body);
            log("     total elapsed: " + elapsedMs(totalStart) + "ms");
            throw new MojoExecutionException("PING FAILED: HTTP " + statusCode + "\n" + body);
        }

        // ── [8] Inspect zip ──────────────────────────────────────────────────
        step(8, "Inspecting generated zip archive");
        t = System.currentTimeMillis();

        List<String> fileLines = new ArrayList<>();
        String errorContent = null;

        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(responseBody))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("error.txt".equals(entry.getName())) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                    }
                    errorContent = bos.toString(StandardCharsets.UTF_8.name()).trim();
                } else if (!entry.isDirectory()) {
                    // Read the entry fully to get actual size (getSize() may be -1)
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        bos.write(buf, 0, n);
                    }
                    fileLines.add(String.format("    %8d bytes  %s",
                            bos.size(), entry.getName()));
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            log("  \u2717  PING FAILED \u2014 response is not a valid zip: " + e.getMessage());
            throw new MojoExecutionException("invalid zip response", e);
        }

        log("    files in archive: " + fileLines.size());
        for (String line : fileLines) {
            log(line);
        }
        elapsed(t);

        // ── [9] Result ───────────────────────────────────────────────────────
        step(9, "Result");
        long totalMs = elapsedMs(totalStart);

        if (errorContent != null) {
            log("");
            log("  \u2717  PING FAILED \u2014 service returned error.txt");
            log("");
            for (String line : errorContent.split("\\n")) {
                log("     " + line);
            }
            log("");
            log("     total elapsed: " + totalMs + "ms");
            throw new MojoExecutionException("PING FAILED: " + errorContent);
        }

        log("");
        log("  \u2713  PING OK");
        log("     endpoint      : " + generateUrl);
        log("     files         : " + fileLines.size());
        log("     total elapsed : " + totalMs + "ms");
        log("");

        // Cleanup
        modelFile.delete();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void step(int n, String label) {
        log("");
        log("  [" + n + "] " + label + " \u2014 " + utcNow());
    }

    private void log(String msg) {
        getLog().info(msg);
    }

    private void elapsed(long startMs) {
        log("    elapsed         : " + (System.currentTimeMillis() - startMs) + "ms");
    }

    private static long elapsedMs(long startMs) {
        return System.currentTimeMillis() - startMs;
    }

    private static String utcNow() {
        long secs = System.currentTimeMillis() / 1000;
        long h = (secs / 3600) % 24;
        long m = (secs / 60) % 60;
        long s = secs % 60;
        return String.format("%02d:%02d:%02d UTC", h, m, s);
    }

    /**
     * Loads the bundled demo-service.xml from the plugin classpath.
     */
    private String loadBundledDemoModel() throws MojoExecutionException {
        try (InputStream in = getClass().getResourceAsStream("/assets/demo-service.xml")) {
            if (in == null) {
                throw new MojoExecutionException(
                        "bundled demo-service.xml not found in plugin classpath");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new MojoExecutionException("failed to read bundled demo model", e);
        }
    }
}
