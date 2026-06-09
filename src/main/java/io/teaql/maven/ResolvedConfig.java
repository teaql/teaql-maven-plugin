package io.teaql.maven;

import java.io.File;

/**
 * Fully-resolved, ready-to-use TeaQL configuration.
 *
 * <p>All paths are absolute; no further resolution is needed downstream.
 * Each value also carries its source for diagnostic logging.
 */
public class ResolvedConfig {

    private final String endpointPrefix;
    private final String apiKey;
    private final File buildDir;
    private final long timeoutSeconds;

    private final String endpointPrefixSource;
    private final String apiKeySource;
    private final String buildDirSource;
    private final String timeoutSource;

    public ResolvedConfig(String endpointPrefix, String apiKey,
                          File buildDir, long timeoutSeconds,
                          String endpointPrefixSource, String apiKeySource,
                          String buildDirSource, String timeoutSource) {
        this.endpointPrefix = endpointPrefix;
        this.apiKey = apiKey;
        this.buildDir = buildDir;
        this.timeoutSeconds = timeoutSeconds;
        this.endpointPrefixSource = endpointPrefixSource;
        this.apiKeySource = apiKeySource;
        this.buildDirSource = buildDirSource;
        this.timeoutSource = timeoutSource;
    }

    public String getEndpointPrefix() { return endpointPrefix; }

    /** @deprecated use {@link #getEndpointPrefix()} */
    @Deprecated
    public String getServiceUrl() { return endpointPrefix; }

    public String getApiKey() { return apiKey; }
    public File getBuildDir() { return buildDir; }
    public long getTimeoutSeconds() { return timeoutSeconds; }

    /**
     * Returns a multi-line string describing where each config value came from,
     * suitable for Maven log output.
     */
    public String describeSources() {
        return "\n" +
            "  config (precedence: mojo > env > config.yml > default):\n" +
            "    endpoint_prefix  = " + endpointPrefix + "  (from: " + endpointPrefixSource + ")\n" +
            "    api_key          = " + (apiKey != null ? "********" : "null") + "  (from: " + apiKeySource + ")\n" +
            "    build_dir        = " + buildDir + "  (from: " + buildDirSource + ")\n" +
            "    timeout_seconds  = " + timeoutSeconds + "  (from: " + timeoutSource + ")\n";
    }

    @Override
    public String toString() {
        return "ResolvedConfig{" +
                "endpointPrefix='" + endpointPrefix + '\'' +
                ", apiKey=" + (apiKey != null ? "'********'" : "null") +
                ", buildDir=" + buildDir +
                ", timeoutSeconds=" + timeoutSeconds +
                '}';
    }
}
