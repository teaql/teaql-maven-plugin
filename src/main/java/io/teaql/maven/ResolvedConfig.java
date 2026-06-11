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
        StringBuilder sb = new StringBuilder();
        sb.append("\n  config (precedence: mojo > env > config.yml > default):\n");
        sb.append("    endpoint_prefix  = ").append(endpointPrefix).append("  (from: ").append(endpointPrefixSource).append(")\n");
        sb.append("    api_key          = ").append(apiKey != null ? "********" : "null").append("  (from: ").append(apiKeySource).append(")\n");
        sb.append("    build_dir        = ").append(buildDir).append("  (from: ").append(buildDirSource).append(")\n");
        sb.append("    timeout_seconds  = ").append(timeoutSeconds).append("  (from: ").append(timeoutSource).append(")\n");
        
        if (apiKey != null) {
            String[] parts = apiKey.split("\\.");
            if (parts.length == 3) {
                try {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    String sub = extract(payload, "\"sub\":\"([^\"]+)\"");
                    String plan = extract(payload, "\"plan\":\"([^\"]+)\"");
                    String expStr = extract(payload, "\"exp\":(\\d+)");
                    
                    String formattedExp = "never";
                    if (expStr != null) {
                        long expTime = Long.parseLong(expStr) * 1000;
                        long now = System.currentTimeMillis();
                        long diffMs = expTime - now;
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'");
                        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                        String dateStr = sdf.format(new java.util.Date(expTime));
                        
                        if (diffMs < 0) {
                            long daysAgo = (-diffMs) / (1000L * 3600 * 24);
                            formattedExp = dateStr + " (EXPIRED " + daysAgo + " days ago!)";
                        } else {
                            long daysLeft = diffMs / (1000L * 3600 * 24);
                            formattedExp = dateStr + " (" + daysLeft + " days remaining)";
                        }
                    }
                    
                    sb.append("\n  api_key permissions:\n");
                    sb.append("    subject = ").append(sub != null ? sub : "unknown").append("\n");
                    sb.append("    plan    = ").append(plan != null ? plan : "unknown").append("\n");
                    sb.append("    expires = ").append(formattedExp).append("\n");
                } catch (Exception e) {
                    sb.append("\n  api_key permissions: [Could not parse token]\n");
                }
            }
        }
        
        return sb.toString();
    }

    private String extract(String json, String regex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
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
