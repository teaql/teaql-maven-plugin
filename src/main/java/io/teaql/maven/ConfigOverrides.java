package io.teaql.maven;

/**
 * Mojo-level parameter overrides for {@link TeaqlConfig}.
 *
 * <p>Any field left {@code null} (or {@code 0} for numeric) means "no override — use config file
 * or built-in default instead."
 */
public class ConfigOverrides {

    private String serviceUrl;
    private String licenseFile;
    private String buildDir;
    private long timeoutSeconds;   // 0 → no override

    public ConfigOverrides() {}

    public ConfigOverrides(String serviceUrl, String licenseFile,
                           String buildDir, long timeoutSeconds) {
        this.serviceUrl = serviceUrl;
        this.licenseFile = licenseFile;
        this.buildDir = buildDir;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }

    public String getLicenseFile() { return licenseFile; }
    public void setLicenseFile(String licenseFile) { this.licenseFile = licenseFile; }

    public String getBuildDir() { return buildDir; }
    public void setBuildDir(String buildDir) { this.buildDir = buildDir; }

    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
