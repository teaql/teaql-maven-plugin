package io.teaql.maven;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Core code-generation logic, mirroring {@code generator.rs} from the Cargo CLI.
 *
 * <p>Workflow:
 * <ol>
 *   <li>Validate input and license file exist.</li>
 *   <li>If input is a directory, zip it into a temp file.</li>
 *   <li>POST to {@code service_url} as multipart/form-data with {@code file}, {@code licenseFile},
 *       and optional {@code scope}.</li>
 *   <li>Write response to {@code build/domain.zip} and extract it.</li>
 *   <li>If {@code build/error.txt} exists, throw an exception with its contents.</li>
 * </ol>
 */
public class GeneratorService {

    private final Log log;

    public GeneratorService(Log log) {
        this.log = log;
    }

    /**
     * Run the generation pipeline.
     *
     * @param input  model file or directory
     * @param scope  {@code null} → gen-code, {@code "doc"} → gen-doc,
     *               {@code "frontend"} → gen-model
     * @param config resolved configuration
     */
    public void generate(File input, String scope, ResolvedConfig config) throws Exception {
        // 1. Validate inputs
        if (!input.exists()) {
            throw new IllegalArgumentException("input does not exist: " + input.getAbsolutePath());
        }

        // Resolve license: if it points to the placeholder, extract from classpath
        File licenseFile = resolveLicenseFile(config.getLicenseFile());

        // 2. Create build dir
        Files.createDirectories(config.getBuildDir().toPath());

        // 3. Prepare upload (zip directory if needed)
        File uploadFile = prepareUpload(input);

        try {
            // 4. Call remote service
            log.info("using " + config.getServiceUrl());
            byte[] zipBytes = requestGeneration(uploadFile, scope, licenseFile, config);

            // 5. Write archive
            File archivePath = new File(config.getBuildDir(), "domain.zip");
            Files.write(archivePath.toPath(), zipBytes);

            // 6. Extract
            extractZip(zipBytes, config.getBuildDir());

            // 7. Check for error.txt
            File errorFile = new File(config.getBuildDir(), "error.txt");
            if (errorFile.exists()) {
                String errorContent = new String(Files.readAllBytes(errorFile.toPath()),
                        StandardCharsets.UTF_8).trim();
                throw new RuntimeException("TeaQL service error: " + errorContent);
            }

            log.info("generated output in " + config.getBuildDir().getAbsolutePath());
            log.info("archive saved to " + archivePath.getAbsolutePath());
        } finally {
            // Clean up temp zip if we created one
            if (uploadFile != input) {
                uploadFile.delete();
            }
        }
    }

    // ── prepareUpload ────────────────────────────────────────────────────────

    private File prepareUpload(File input) throws IOException {
        if (input.isFile()) {
            return input;
        }
        // Directory → zip into temp file
        File temp = File.createTempFile("teaql-upload-", ".zip");
        zipDirectory(input, temp);
        return temp;
    }

    // ── requestGeneration ────────────────────────────────────────────────────

    private byte[] requestGeneration(File uploadFile, String scope,
                                     File licenseFile, ResolvedConfig config) throws IOException {
        int timeoutMs = (int) (config.getTimeoutSeconds() * 1000L);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost post = new HttpPost(config.getServiceUrl());

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addPart("file", new FileBody(uploadFile));
            builder.addPart("licenseFile", new FileBody(licenseFile));
            if (scope != null) {
                builder.addTextBody("scope", scope, org.apache.http.entity.ContentType.TEXT_PLAIN);
            }

            post.setEntity(builder.build());

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                byte[] body = EntityUtils.toByteArray(entity);
                if (statusCode < 200 || statusCode >= 300) {
                    throw new IOException("service returned HTTP " + statusCode
                            + " for " + config.getServiceUrl());
                }
                return body;
            }
        }
    }

    // ── extractZip ───────────────────────────────────────────────────────────

    private void extractZip(byte[] zipBytes, File outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(outputDir, entry.getName());

                // Guard against zip-slip
                if (!outFile.getCanonicalPath().startsWith(
                        outputDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("zip slip detected: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(outFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) != -1) {
                            os.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // ── zipDirectory ─────────────────────────────────────────────────────────

    private void zipDirectory(File directory, File destZip) throws IOException {
        Path dirPath = directory.toPath();
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(destZip)))) {

            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Path relative = dirPath.relativize(file);
                    String entryName = relative.toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    Path relative = dirPath.relativize(dir);
                    if (!relative.toString().isEmpty()) {
                        String dirEntry = relative.toString().replace('\\', '/') + "/";
                        zos.putNextEntry(new ZipEntry(dirEntry));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    // ── license resolution ───────────────────────────────────────────────────

    /**
     * If the configured license path is the internal placeholder, extract the bundled
     * {@code public.LICENSE} from the plugin's own jar into a temp file.
     */
    private File resolveLicenseFile(File configured) throws IOException {
        // If file exists on disk, use it directly
        if (configured != null && configured.exists()) {
            return configured;
        }

        // Fall back to bundled classpath resource
        InputStream resource = getClass().getResourceAsStream("/assets/public.LICENSE");
        if (resource == null) {
            throw new FileNotFoundException(
                    "bundled public.LICENSE not found in plugin classpath, "
                    + "and configured license file does not exist: " + configured);
        }
        File temp = File.createTempFile("teaql-license-", ".LICENSE");
        temp.deleteOnExit();
        try (InputStream in = resource;
             OutputStream out = new FileOutputStream(temp)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        return temp;
    }
}
