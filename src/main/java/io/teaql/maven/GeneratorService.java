package io.teaql.maven;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
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

    /** Fields suppressed when printing the default license banner. */
    private static final Set<String> SUPPRESSED_LICENSE_FIELDS =
            new HashSet<>(Arrays.asList("PUBLIC KEY", "SIGNATURE"));

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
        // 1. Validate input
        if (!input.exists()) {
            throw new IllegalArgumentException("input does not exist: " + input.getAbsolutePath());
        }

        // 2. Resolve license (may extract bundled resource to temp file)
        ResolvedLicense license = resolveLicenseFile(config.getLicenseFile());

        // 3. If using the default bundled license, print info + fair-use notice
        if (license.isDefault) {
            printDefaultLicenseInfo(license.file);
        }

        // 4. Create build dir
        Files.createDirectories(config.getBuildDir().toPath());

        // 5. Prepare upload (zip directory if needed)
        File uploadFile = prepareUpload(input);

        try {
            // 6. Call remote service
            log.info("using " + config.getServiceUrl());
            byte[] zipBytes = requestGeneration(uploadFile, scope, license.file, config);

            // 7. Write archive
            File archivePath = new File(config.getBuildDir(), "domain.zip");
            Files.write(archivePath.toPath(), zipBytes);

            // 8. Extract
            extractZip(zipBytes, config.getBuildDir());

            // 9. Check for error.txt
            File errorFile = new File(config.getBuildDir(), "error.txt");
            if (errorFile.exists()) {
                String errorContent = new String(
                        Files.readAllBytes(errorFile.toPath()), StandardCharsets.UTF_8).trim();
                throw new RuntimeException("TeaQL service error: " + errorContent);
            }

            log.info("generated output in " + config.getBuildDir().getAbsolutePath());
            log.info("archive saved to " + archivePath.getAbsolutePath());
        } finally {
            // Clean up temp zip if we created one
            if (uploadFile != input) {
                uploadFile.delete();
            }
            // Clean up extracted bundled license temp file
            if (license.isDefault) {
                license.file.delete();
            }
        }
    }

    // ── default license info ─────────────────────────────────────────────────

    /**
     * Parse the default license JSON and print all user-visible fields,
     * then emit a fair-use reminder.
     */
    @SuppressWarnings("unchecked")
    private void printDefaultLicenseInfo(File licenseFile) {
        try {
            String content = new String(Files.readAllBytes(licenseFile.toPath()),
                    StandardCharsets.UTF_8);
            // SnakeYAML parses JSON natively (JSON is valid YAML)
            Yaml yaml = new Yaml();
            Map<String, Object> fields = yaml.load(content);

            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            sb.append("┌─────────────────────────────────────────────────┐\n");
            sb.append("│              TeaQL License (default)            │\n");
            sb.append("├─────────────────────────────────────────────────┤\n");
            if (fields != null) {
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    if (SUPPRESSED_LICENSE_FIELDS.contains(entry.getKey())) {
                        continue;
                    }
                    sb.append(String.format("│  %-18s : %-27s│\n",
                            entry.getKey(), String.valueOf(entry.getValue())));
                }
            }
            sb.append("├─────────────────────────────────────────────────┤\n");
            sb.append("│  ⚠  Fair Use Notice                             │\n");
            sb.append("│  You are using the public free-tier license.    │\n");
            sb.append("│  Configure a personal license via:              │\n");
            sb.append("│    ~/.teaql/config.yml  →  license_file: ...    │\n");
            sb.append("│  or pass  -Dteaql.licenseFile=<path>            │\n");
            sb.append("└─────────────────────────────────────────────────┘\n");

            log.warn(sb.toString());
        } catch (Exception e) {
            log.warn("could not read default license file: " + e.getMessage());
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
                builder.addTextBody("scope", scope,
                        org.apache.http.entity.ContentType.TEXT_PLAIN);
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
     * Resolves the license file to use.
     *
     * <ul>
     *   <li>If the configured path exists on disk → use it directly ({@code isDefault=false}).</li>
     *   <li>Otherwise → extract the bundled classpath resource to a temp file
     *       ({@code isDefault=true}).</li>
     * </ul>
     */
    private ResolvedLicense resolveLicenseFile(File configured) throws IOException {
        if (configured != null && configured.exists()) {
            return new ResolvedLicense(configured, false);
        }

        // Fall back to bundled classpath resource
        InputStream resource = getClass().getResourceAsStream("/assets/public.LICENSE");
        if (resource == null) {
            throw new FileNotFoundException(
                    "bundled public.LICENSE not found in plugin classpath, "
                    + "and configured license file does not exist: " + configured);
        }
        File temp = File.createTempFile("teaql-license-", ".LICENSE");
        // Do NOT deleteOnExit here — we delete it ourselves in the finally block of generate()
        try (InputStream in = resource;
             OutputStream out = new FileOutputStream(temp)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        return new ResolvedLicense(temp, true);
    }

    // ── inner types ──────────────────────────────────────────────────────────

    private static final class ResolvedLicense {
        final File file;
        final boolean isDefault;

        ResolvedLicense(File file, boolean isDefault) {
            this.file = file;
            this.isDefault = isDefault;
        }
    }
}
