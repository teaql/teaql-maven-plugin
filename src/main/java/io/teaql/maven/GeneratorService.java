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
 *   <li>POST to {@code service_url} as multipart/form-data with {@code file}, optional {@code scope},
 *       and set {@code Authorization: Bearer <apiKey>} header.</li>
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
     * @param scope  {@code null} → gen-lib, {@code "doc"} → gen-doc,
     *               {@code "frontend"} → gen-model
     * @param config resolved configuration
     */
    public void generate(File input, String scope, ResolvedConfig config) throws Exception {
        generateInternal(input, scope, config, config.getBuildDir());
    }

    /**
     * Workspace generation pipeline.
     *
     * <p>Same as {@link #generate} but extracts the ZIP into {@code workspaceDir}
     * instead of the build dir, and uses the build dir only for the archive.
     *
     * @param input        model file or directory
     * @param scope        scope value sent to the service (e.g. {@code "java-workspace"})
     * @param config       resolved configuration
     * @param workspaceDir target directory for extracted workspace files
     */
    public void generateWorkspace(File input, String scope,
                                  ResolvedConfig config, File workspaceDir) throws Exception {
        generateInternal(input, scope, config, workspaceDir);
    }

    /**
     * Internal generation pipeline shared by {@link #generate} and {@link #generateWorkspace}.
     *
     * @param input     model file or directory
     * @param scope     scope parameter for the remote service
     * @param config    resolved configuration
     * @param outputDir directory where the ZIP will be extracted
     */
    private void generateInternal(File input, String scope,
                                  ResolvedConfig config, File outputDir) throws Exception {
        // 1. Validate input
        if (!input.exists()) {
            throw new IllegalArgumentException("input does not exist: " + input.getAbsolutePath());
        }

        // 4. Create output dir (and build dir for archive)
        Files.createDirectories(outputDir.toPath());
        Files.createDirectories(config.getBuildDir().toPath());

        // 5. Prepare upload (zip directory if needed)
        File uploadFile = prepareUpload(input);

        log.info("model input: " + input.getAbsolutePath());

        try {
            // 6. Call remote service
            log.info("using " + TeaQLService.endpointUrl(config.getEndpointPrefix(), "generate"));
            byte[] zipBytes = requestGeneration(uploadFile, scope, config.getApiKey(), config);

            // 7. Write archive (always into build dir)
            File archivePath = new File(config.getBuildDir(), "domain.zip");
            Files.write(archivePath.toPath(), zipBytes);

            // 8. Extract into outputDir
            extractZip(zipBytes, outputDir);

            // 9. Check for error.txt (in outputDir)
            File errorFile = new File(outputDir, "error.txt");
            if (errorFile.exists()) {
                String errorContent = new String(
                        Files.readAllBytes(errorFile.toPath()), StandardCharsets.UTF_8).trim();
                throw new RuntimeException("TeaQL service error: " + errorContent);
            }

            log.info("generated output in " + outputDir.getAbsolutePath());
            log.info("archive saved to " + archivePath.getAbsolutePath());
        } finally {
            // Clean up temp zip if we created one
            if (uploadFile != input) {
                if (!uploadFile.delete()) {
                    log.warn("Failed to delete temp upload file: " + uploadFile.getAbsolutePath());
                }
            }
        }
    }



    // ── prepareUpload ────────────────────────────────────────────────────────

    public File prepareUpload(File input) throws IOException {
        if (input.isFile()) {
            return input;
        }

        File mainXml = new File(input, "main.xml");
        if (mainXml.exists() && mainXml.isFile()) {
            File temp = File.createTempFile("teaql-upload-", ".zip");
            zipDirectory(input, temp);
            return temp;
        }

        File[] files = input.listFiles();
        List<File> modelFiles = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".xml") || name.endsWith(".ksml")) {
                        modelFiles.add(f);
                    }
                }
            }
        }

        if (modelFiles.size() == 1) {
            log.info("note: uploading " + modelFiles.get(0).getName() + " directly since no main.xml was found in directory");
            return modelFiles.get(0);
        } else if (modelFiles.isEmpty()) {
            throw new IOException("no model files (.xml or .ksml) found in directory " + input.getAbsolutePath());
        } else {
            throw new IOException("multiple model files found in " + input.getAbsolutePath() + " but no main.xml. Please rename your entry point to main.xml");
        }
    }

    // ── requestGeneration ────────────────────────────────────────────────────

    private byte[] requestGeneration(File uploadFile, String scope,
                                     String apiKey, ResolvedConfig config) throws IOException {
        int timeoutMs = (int) (config.getTimeoutSeconds() * 1000L);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            String requestPath = "generate";
            if (scope != null && scope.contains("-assist-")) {
                requestPath = scope;
            }

            HttpPost post = new HttpPost(TeaQLService.endpointUrl(config.getEndpointPrefix(), requestPath));

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addPart("file", new FileBody(uploadFile));
            if (apiKey != null) {
                post.setHeader("Authorization", "Bearer " + apiKey);
            }
            if (scope != null && !scope.contains("-assist-")) {
                builder.addTextBody("scope", scope,
                        org.apache.http.entity.ContentType.TEXT_PLAIN);
            }

            post.setEntity(builder.build());

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                byte[] body = EntityUtils.toByteArray(entity);
                org.apache.http.Header contentTypeHeader = response.getFirstHeader("Content-Type");
                String contentType = contentTypeHeader != null ? contentTypeHeader.getValue().toLowerCase(Locale.ROOT) : "";
                boolean isZip = contentType.contains("zip") || contentType.contains("octet-stream");

                if (!isZip) {
                    String textBody = new String(body, java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (statusCode >= 200 && statusCode < 300) {
                        System.out.println(textBody);
                        throw new IOException("Generation output text (not a zip archive). Execution complete.");
                    } else {
                        System.err.println(textBody);
                        throw new IOException("Generation failed due to server validation errors. Check the report above.");
                    }
                }

                if (statusCode < 200 || statusCode >= 300) {
                    String errorBody = new String(body, java.nio.charset.StandardCharsets.UTF_8).trim();
                    System.err.println(errorBody);
                    throw new IOException("Generation failed due to server validation errors. Check the Markdown report above.");
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
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        log.warn("Failed to create directory: " + outFile.getAbsolutePath());
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        log.warn("Failed to create parent directory for: " + outFile.getAbsolutePath());
                    }
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

            Files.walkFileTree(dirPath, new ZipDirectoryVisitor(dirPath, zos));
        }
    }

    private static class ZipDirectoryVisitor extends SimpleFileVisitor<Path> {
        private final Path dirPath;
        private final ZipOutputStream zos;

        public ZipDirectoryVisitor(Path dirPath, ZipOutputStream zos) {
            this.dirPath = dirPath;
            this.zos = zos;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Path relative = dirPath.relativize(file);
            String entryName = relative.toString().replace('\\', '/');
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zos);
            zos.closeEntry();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Path relative = dirPath.relativize(dir);
            if (!relative.toString().isEmpty()) {
                String dirEntry = relative.toString().replace('\\', '/') + "/";
                zos.putNextEntry(new ZipEntry(dirEntry));
                zos.closeEntry();
            }
            return FileVisitResult.CONTINUE;
        }
    }

}
