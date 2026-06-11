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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Handles communication with the remote '/evaluate' service endpoint and processes results.
 */
public class EvaluationService {

    private final Log log;

    public EvaluationService(Log log) {
        this.log = log;
    }

    /**
     * Executes the KSML model evaluation.
     *
     * @param input          model file or directory
     * @param outputFile     optional file to write raw Markdown report to
     * @param failOnWarning  fail if warnings exist
     * @param config         resolved configuration
     * @return true if evaluation passed, false if it failed (errors exist or failOnWarning triggered)
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(File input, File outputFile, boolean failOnWarning, ResolvedConfig config) throws Exception {
        if (!input.exists()) {
            log.error("input does not exist: " + input.getAbsolutePath());
            return false;
        }

        GeneratorService genService = new GeneratorService(log);
        File uploadFile = genService.prepareUpload(input);

        String requestUrl = TeaQLService.endpointUrl(config.getEndpointPrefix(), "evaluate");
        log.info("using " + requestUrl);

        int timeoutMs = (int) (config.getTimeoutSeconds() * 1000L);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .build();

        String responseText;
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpPost post = new HttpPost(requestUrl);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addPart("file", new FileBody(uploadFile));
            post.setEntity(builder.build());

            try (CloseableHttpResponse response = client.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                responseText = entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";

                if (statusCode == 404) {
                    log.error("Server does not support /evaluate. Please upgrade the TeaQL generator server or "
                            + "use a server URL that supports KSML evaluation.");
                    return false;
                }

                if (statusCode < 200 || statusCode >= 300) {
                    log.error("Server returned HTTP " + statusCode + " for " + requestUrl);
                    log.error("Raw response: " + responseText);
                    return false;
                }
            }
        } finally {
            if (uploadFile != input) {
                uploadFile.delete();
            }
        }

        // Write raw Markdown output if requested
        if (outputFile != null) {
            File parent = outputFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.write(outputFile.toPath(), responseText.getBytes(StandardCharsets.UTF_8));
            log.info("Raw Markdown report written to: " + outputFile.getAbsolutePath());
        }

        System.out.println(responseText);

        if (responseText.contains("## ❌ Errors")) {
            return false;
        }

        if (responseText.contains("## ⚠️ Warnings") && failOnWarning) {
            log.error("Evaluation failed due to warnings and failOnWarning=true.");
            return false;
        }

        return true;
    }
}
