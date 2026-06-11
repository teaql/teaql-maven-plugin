package io.teaql.maven;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TeaQL local configuration, mirroring {@code ~/.teaql/config.yml}.
 *
 * <p>Precedence (highest wins): Mojo parameter overrides → environment variables
 * → config file → built-in defaults.
 *
 * <p>The config file accepts both {@code endpoint_prefix} (preferred) and
 * the legacy {@code service_url} key for backwards compatibility.
 */
public class TeaqlConfig {

    public static final String DEFAULT_ENDPOINT_PREFIX =
            "https://api.teaql.io/latest/";
    public static final String DEFAULT_BUILD_DIR = "build";
    public static final long DEFAULT_TIMEOUT_SECONDS = 1200L;

    /** Stored as the raw value from config file; normalised during {@link #resolve}. */
    private String endpointPrefix = DEFAULT_ENDPOINT_PREFIX;
    private String apiKey;
    private String buildDir = DEFAULT_BUILD_DIR;
    private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    // ── constructors ────────────────────────────────────────────────────────

    public TeaqlConfig() {}

    // ── factory ─────────────────────────────────────────────────────────────

    /**
     * Loads config from {@code ~/.teaql/config.yml}.
     * Returns defaults if the file does not exist.
     * Accepts both {@code endpoint_prefix} and legacy {@code service_url}.
     */
    public static TeaqlConfig load() throws IOException {
        File configFile = configFilePath();
        if (!configFile.exists()) {
            return new TeaqlConfig();
        }

        try (FileReader reader = new FileReader(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);
            if (data == null) {
                return new TeaqlConfig();
            }
            TeaqlConfig cfg = new TeaqlConfig();
            // endpoint_prefix preferred; fall back to legacy service_url
            if (data.containsKey("endpoint_prefix") && data.get("endpoint_prefix") != null) {
                cfg.endpointPrefix = data.get("endpoint_prefix").toString();
            } else if (data.containsKey("service_url") && data.get("service_url") != null) {
                cfg.endpointPrefix = data.get("service_url").toString();
            }
            if (data.containsKey("api_key") && data.get("api_key") != null) {
                cfg.apiKey = data.get("api_key").toString();
            }
            if (data.containsKey("build_dir") && data.get("build_dir") != null) {
                cfg.buildDir = data.get("build_dir").toString();
            }
            if (data.containsKey("timeout_seconds") && data.get("timeout_seconds") != null) {
                cfg.timeoutSeconds = ((Number) data.get("timeout_seconds")).longValue();
            }
            return cfg;
        }
    }

    /**
     * Saves this config to {@code ~/.teaql/config.yml}.
     */
    public void save() throws IOException {
        File configFile = configFilePath();
        configFile.getParentFile().mkdirs();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("endpoint_prefix", endpointPrefix);
        if (apiKey != null) {
            data.put("api_key", apiKey);
        }
        data.put("build_dir", buildDir);
        data.put("timeout_seconds", timeoutSeconds);

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);
        try (FileWriter writer = new FileWriter(configFile)) {
            yaml.dump(data, writer);
        }
    }

    /**
     * Applies Mojo-level parameter overrides, environment variables, and resolves all
     * paths relative to {@code cwd}.
     *
     * <p>Precedence (highest wins):
     * CLI/Mojo parameter → environment variable → config file → built-in default.
     *
     * <p>Supported environment variables:
     * <ul>
     *   <li>{@code TEAQL_ENDPOINT_PREFIX} – endpoint prefix (preferred)</li>
     *   <li>{@code TEAQL_SERVICE_URL} – legacy alias for endpoint prefix</li>
     *   <li>{@code TEAQL_API_KEY}</li>
     *   <li>{@code TEAQL_BUILD_DIR}</li>
     *   <li>{@code TEAQL_TIMEOUT_SECONDS}</li>
     * </ul>
     *
     * @param overrides parameter overrides from the Mojo (any field may be null/0 = no override)
     * @param cwd       the Maven project's base directory
     * @return a fully-resolved, ready-to-use config
     */
    public ResolvedConfig resolve(ConfigOverrides overrides, File cwd) {
        // ── endpoint_prefix: mojo/env > config.yml > default ──
        String resolvedEndpointPrefix;
        String endpointPrefixSource;
        String envEndpointPrefix = System.getenv("TEAQL_ENDPOINT_PREFIX");
        String envServiceUrl = System.getenv("TEAQL_SERVICE_URL");

        if (overrides.getEndpointPrefix() != null) {
            resolvedEndpointPrefix = normalizeEndpointPrefix(overrides.getEndpointPrefix());
            endpointPrefixSource = "mojo parameter (-Dteaql.endpointPrefix)";
        } else if (overrides.getServiceUrl() != null) {
            resolvedEndpointPrefix = normalizeEndpointPrefix(overrides.getServiceUrl());
            endpointPrefixSource = "mojo parameter (-Dteaql.serviceUrl, deprecated)";
        } else if (envEndpointPrefix != null && !envEndpointPrefix.isBlank()) {
            resolvedEndpointPrefix = normalizeEndpointPrefix(envEndpointPrefix);
            endpointPrefixSource = "env TEAQL_ENDPOINT_PREFIX";
        } else if (envServiceUrl != null && !envServiceUrl.isBlank()) {
            resolvedEndpointPrefix = normalizeEndpointPrefix(envServiceUrl);
            endpointPrefixSource = "env TEAQL_SERVICE_URL (deprecated)";
        } else {
            resolvedEndpointPrefix = normalizeEndpointPrefix(endpointPrefix);
            endpointPrefixSource = "~/.teaql/config.yml (or built-in default)";
        }

        // ── api_key: mojo/env > config.yml > default ──
        String resolvedApiKey;
        String apiKeySource;
        String envApiKey = System.getenv("TEAQL_API_KEY");
        if (overrides.getApiKey() != null) {
            resolvedApiKey = overrides.getApiKey();
            apiKeySource = "mojo parameter (-Dteaql.apiKey)";
        } else if (envApiKey != null && !envApiKey.isBlank()) {
            resolvedApiKey = envApiKey;
            apiKeySource = "env TEAQL_API_KEY";
        } else if (apiKey != null) {
            resolvedApiKey = apiKey;
            apiKeySource = "~/.teaql/config.yml";
        } else {
            resolvedApiKey = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJkZWZhdWx0LXVzZXIiLCJwbGFuIjoiZnJlZSIsImV4cCI6MTc5NjcxNDU0NH0.Dc7PQbvOBIm0U1hZhj9KGsXKrTaQTpEvacbZdWBBwVoqe2H1yqi4DQD6AeXeETFBo8oFfAnSeGpqY592iYj36Q";
            apiKeySource = "built-in default OOTB key";
        }

        // ── build_dir: mojo/env > config.yml > default ──
        String rawBuildDir;
        String buildDirSource;
        String envBuildDir = System.getenv("TEAQL_BUILD_DIR");
        if (overrides.getBuildDir() != null) {
            rawBuildDir = overrides.getBuildDir();
            buildDirSource = "mojo parameter (-Dteaql.output)";
        } else if (envBuildDir != null && !envBuildDir.isBlank()) {
            rawBuildDir = envBuildDir;
            buildDirSource = "env TEAQL_BUILD_DIR";
        } else {
            rawBuildDir = buildDir;
            buildDirSource = "~/.teaql/config.yml (or built-in default)";
        }
        File resolvedBuildDir = normalizePath(rawBuildDir, cwd);

        // ── timeout_seconds: mojo/env > config.yml > default ──
        long resolvedTimeout;
        String timeoutSource;
        String envTimeout = System.getenv("TEAQL_TIMEOUT_SECONDS");
        long envTimeoutVal = 0;
        if (envTimeout != null && !envTimeout.isBlank()) {
            try { envTimeoutVal = Long.parseLong(envTimeout); } catch (NumberFormatException ignored) {}
        }
        if (overrides.getTimeoutSeconds() > 0) {
            resolvedTimeout = overrides.getTimeoutSeconds();
            timeoutSource = "mojo parameter (-Dteaql.timeoutSeconds)";
        } else if (envTimeoutVal > 0) {
            resolvedTimeout = envTimeoutVal;
            timeoutSource = "env TEAQL_TIMEOUT_SECONDS";
        } else {
            resolvedTimeout = timeoutSeconds;
            timeoutSource = "~/.teaql/config.yml (or built-in default)";
        }

        return new ResolvedConfig(resolvedEndpointPrefix, resolvedApiKey,
                resolvedBuildDir, resolvedTimeout,
                endpointPrefixSource, apiKeySource, buildDirSource, timeoutSource);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    public static File configFilePath() {
        String home = System.getenv("HOME");
        if (home == null) {
            home = System.getProperty("user.home");
        }
        return Paths.get(home, ".teaql", "config.yml").toFile();
    }

    /**
     * Ensures the endpoint prefix always ends with {@code /}.
     * Mirrors {@code normalize_endpoint_prefix()} in the Rust CLI.
     */
    public static String normalizeEndpointPrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ENDPOINT_PREFIX;
        }
        return raw.endsWith("/") ? raw : raw + "/";
    }

    /**
     * Builds a full URL from an endpoint prefix and a method path.
     * Mirrors {@code endpoint_url()} in {@code service.rs}.
     *
     * <p>Example: {@code endpointUrl("https://api.teaql.io/latest/", "generate")}
     * → {@code "https://api.teaql.io/latest/generate"}
     */
    public static String endpointUrl(String endpointPrefix, String method) {
        String prefix = endpointPrefix.endsWith("/") ? endpointPrefix
                : endpointPrefix + "/";
        String m = method.startsWith("/") ? method.substring(1) : method;
        return prefix + m;
    }



    private static File normalizePath(String rawPath, File cwd) {
        return normalizePath(new File(rawPath), cwd);
    }

    private static File normalizePath(File file, File cwd) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(cwd, file.getPath()).getAbsoluteFile();
    }

    // ── getters / setters ───────────────────────────────────────────────────

    public String getEndpointPrefix() { return endpointPrefix; }
    public void setEndpointPrefix(String endpointPrefix) { this.endpointPrefix = endpointPrefix; }

    /** @deprecated use {@link #getEndpointPrefix()} */
    @Deprecated
    public String getServiceUrl() { return endpointPrefix; }
    /** @deprecated use {@link #setEndpointPrefix(String)} */
    @Deprecated
    public void setServiceUrl(String serviceUrl) { this.endpointPrefix = serviceUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBuildDir() { return buildDir; }
    public void setBuildDir(String buildDir) { this.buildDir = buildDir; }

    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    @Override
    public String toString() {
        return "TeaqlConfig{" +
                "endpointPrefix='" + endpointPrefix + '\'' +
                ", apiKey='" + (apiKey != null ? "********" : "null") + '\'' +
                ", buildDir='" + buildDir + '\'' +
                ", timeoutSeconds=" + timeoutSeconds +
                '}';
    }
}
