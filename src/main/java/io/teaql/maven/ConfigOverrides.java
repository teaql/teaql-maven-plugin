package io.teaql.maven;

/**
 * Mojo-level parameter overrides for {@link TeaqlConfig}.
 *
 * <p>Any field left {@code null} (or {@code 0} for numeric) means "no override — use config file
 * or built-in default instead."
 */
public class ConfigOverrides {

    /** TeaQL endpoint prefix, e.g. {@code https://api.teaql.io/latest/}. Preferred. */
    private String endpointPrefix;
    /** @deprecated use {@link #endpointPrefix}. Kept for backward compatibility. */
    @Deprecated
    private String serviceUrl;
    private String apiKey;
    private String buildDir;
    private long timeoutSeconds;   // 0 → no override

    public ConfigOverrides() {}

    public ConfigOverrides(String endpointPrefix, String serviceUrl,
                           String apiKey, String buildDir, long timeoutSeconds) {
        this.endpointPrefix = endpointPrefix;
        this.serviceUrl = serviceUrl;
        this.apiKey = apiKey;
        this.buildDir = buildDir;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getEndpointPrefix() { return endpointPrefix; }
    public void setEndpointPrefix(String endpointPrefix) { this.endpointPrefix = endpointPrefix; }

    /** @deprecated use {@link #getEndpointPrefix()} */
    @Deprecated
    public String getServiceUrl() { return serviceUrl; }
    /** @deprecated use {@link #setEndpointPrefix(String)} */
    @Deprecated
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBuildDir() { return buildDir; }
    public void setBuildDir(String buildDir) { this.buildDir = buildDir; }

    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
