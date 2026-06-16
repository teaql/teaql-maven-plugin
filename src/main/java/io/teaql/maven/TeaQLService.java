package io.teaql.maven;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.yaml.snakeyaml.Yaml;

/**
 * TeaQL remote service utilities, mirroring {@code service.rs} from the Cargo CLI.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link #endpointUrl(String, String)} – joins an endpoint prefix with a method path.</li>
 *   <li>{@link #printVersion(ResolvedConfig)} – calls {@code GET /version} and prints the result.</li>
 * </ul>
 */
public class TeaQLService {

    private final Log log;

    public TeaQLService(Log log) {
        this.log = log;
    }

    /**
     * Joins an endpoint prefix and a method path into a full URL.
     *
     * <p>Mirrors {@code endpoint_url()} in {@code service.rs}.
     *
     * <p>Examples:
     * <pre>
     *   endpointUrl("https://api.teaql.io/latest/", "version")  → "https://api.teaql.io/latest/version"
     *   endpointUrl("https://api.teaql.io/latest",  "/generate") → "https://api.teaql.io/latest/generate"
     * </pre>
     */
    public static String endpointUrl(String endpointPrefix, String method) {
        String prefix = endpointPrefix.endsWith("/") ? endpointPrefix : endpointPrefix + "/";
        String m = method.startsWith("/") ? method.substring(1) : method;
        return prefix + m;
    }

    /**
     * Calls {@code GET /version} on the configured endpoint and prints the result
     * as a formatted key-value table.
     *
     * <p>Mirrors {@code print_version()} in {@code service.rs}.
     *
     * @param config resolved configuration
     * @throws IOException if the HTTP request fails
     */
    public void printVersion(ResolvedConfig config) throws IOException {
        String requestUrl = endpointUrl(config.getEndpointPrefix(), "version");
        log.info("using " + requestUrl);

        int timeoutMs = (int) (config.getTimeoutSeconds() * 1000L);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .build();

        String body;
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpGet get = new HttpGet(requestUrl);
            try (CloseableHttpResponse response = client.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (statusCode < 200 || statusCode >= 300) {
                    throw new IOException("service returned HTTP " + statusCode
                            + " for " + requestUrl + ": " + body);
                }
            }
        }

        log.info("\n" + formatKeyValueTable(body));
    }

    /**
     * Calls {@code GET /services} on the configured endpoint and prints the result
     * as a formatted key-value table containing available services and their descriptions.
     *
     * @param config resolved configuration
     * @throws IOException if the HTTP request fails
     */
    public void printServices(ResolvedConfig config) throws IOException {
        String requestUrl = endpointUrl(config.getEndpointPrefix(), "services");
        log.info("using " + requestUrl);

        int timeoutMs = (int) (config.getTimeoutSeconds() * 1000L);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .build();

        String body;
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpGet get = new HttpGet(requestUrl);
            try (CloseableHttpResponse response = client.execute(get)) {
                int statusCode = response.getStatusLine().getStatusCode();
                body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (statusCode < 200 || statusCode >= 300) {
                    throw new IOException("service returned HTTP " + statusCode
                            + " for " + requestUrl + ": " + body);
                }
            }
        }

        log.info("\n" + formatServicesTable(body));
    }

    /**
     * Formats the /services JSON response containing a services array and metadata.
     */
    @SuppressWarnings("unchecked")
    static String formatServicesTable(String json) {
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try {
            data = yaml.load(json);
        } catch (Exception e) {
            return json; // fallback to raw string if parsing fails
        }

        StringBuilder sb = new StringBuilder();
        if (data.containsKey("version")) {
            sb.append("Server Version: ").append(data.get("version")).append("\n");
        }
        if (data.containsKey("updateTime")) {
            sb.append("Update Time: ").append(data.get("updateTime")).append("\n");
        }
        
        List<Map<String, String>> services = (List<Map<String, String>>) data.get("services");
        if (services == null || services.isEmpty()) {
            sb.append("No services found.\n");
            return sb.toString();
        }

        int keyWidth = services.stream().map(s -> s.get("name")).mapToInt(String::length).max().orElse(3);
        keyWidth = Math.max(keyWidth, "Service".length());
        int valueWidth = services.stream().map(s -> s.get("description")).mapToInt(String::length).max().orElse(11);
        valueWidth = Math.max(valueWidth, "Description".length());

        String border = "+-" + "-".repeat(keyWidth) + "-+-" + "-".repeat(valueWidth) + "-+";
        sb.append(border).append("\n");
        sb.append(String.format("| %-" + keyWidth + "s | %-" + valueWidth + "s |\n", "Service", "Description"));
        sb.append(border).append("\n");
        for (Map<String, String> service : services) {
            sb.append(String.format("| %-" + keyWidth + "s | %-" + valueWidth + "s |\n",
                    service.get("name"), service.get("description")));
        }
        sb.append(border);
        return sb.toString();
    }

    /**
     * Formats a flat JSON object as a padded key-value table.
     * Mirrors {@code format_key_value_table()} in {@code service.rs}.
     */
    static String formatKeyValueTable(String json) {
        // Simple JSON object parser (relies on well-formed service response)
        Map<String, String> rows = parseSimpleJsonObject(json);
        if (rows.isEmpty()) {
            return json;
        }

        // Use TreeMap for sorted keys (same as BTreeMap in Rust)
        TreeMap<String, String> sorted = new TreeMap<>(rows);

        int keyWidth = sorted.keySet().stream().mapToInt(String::length).max().orElse(3);
        keyWidth = Math.max(keyWidth, "Key".length());
        int valueWidth = sorted.values().stream().mapToInt(String::length).max().orElse(5);
        valueWidth = Math.max(valueWidth, "Value".length());

        String border = "+-" + "-".repeat(keyWidth) + "-+-" + "-".repeat(valueWidth) + "-+";
        StringBuilder sb = new StringBuilder();
        sb.append(border).append("\n");
        sb.append(String.format("| %-" + keyWidth + "s | %-" + valueWidth + "s |\n", "Key", "Value"));
        sb.append(border).append("\n");
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            sb.append(String.format("| %-" + keyWidth + "s | %-" + valueWidth + "s |\n",
                    entry.getKey(), entry.getValue()));
        }
        sb.append(border);
        return sb.toString();
    }

    /**
     * Minimal JSON object parser for flat string/number/boolean values.
     * Sufficient for the {@code /version} endpoint response.
     */
    private static Map<String, String> parseSimpleJsonObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return result;
        }
        // Strip braces and split on top-level commas
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        // Split key: value pairs (naive but sufficient for flat objects)
        for (String pair : inner.split(",(?=\\s*\")")) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim()
                    .replaceAll("^\"|\"$", "");
            String value = pair.substring(colon + 1).trim()
                    .replaceAll("^\"|\"$", "");
            result.put(key, value);
        }
        return result;
    }
}
