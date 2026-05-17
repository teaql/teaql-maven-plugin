package io.teaql.maven;

import java.io.File;

/**
 * Fully-resolved, ready-to-use TeaQL configuration.
 *
 * <p>All paths are absolute; no further resolution is needed downstream.
 * Each value also carries its source for diagnostic logging.
 */
public class ResolvedConfig {

    private final String serviceUrl;
    private final File licenseFile;
    private final File buildDir;
    private final long timeoutSeconds;

    private final String serviceUrlSource;
    private final String licenseSource;
    private final String buildDirSource;
    private final String timeoutSource;

    public ResolvedConfig(String serviceUrl, File licenseFile,
                          File buildDir, long timeoutSeconds,
                          String serviceUrlSource, String licenseSource,
                          String buildDirSource, String timeoutSource) {
        this.serviceUrl = serviceUrl;
        this.licenseFile = licenseFile;
        this.buildDir = buildDir;
        this.timeoutSeconds = timeoutSeconds;
        this.serviceUrlSource = serviceUrlSource;
        this.licenseSource = licenseSource;
        this.buildDirSource = buildDirSource;
        this.timeoutSource = timeoutSource;
    }

    public String getServiceUrl() { return serviceUrl; }
    public File getLicenseFile() { return licenseFile; }
    public File getBuildDir() { return buildDir; }
    public long getTimeoutSeconds() { return timeoutSeconds; }

    /**
     * Returns a multi-line string describing where each config value came from,
     * suitable for Maven log output.
     */
    public String describeSources() {
        return "\n" +
            "  config (precedence: mojo/env > config.yml > default):\n" +
            "    service_url     = " + serviceUrl + "  (from: " + serviceUrlSource + ")\n" +
            "    license_file    = " + licenseFile + "  (from: " + licenseSource + ")\n" +
            "    build_dir       = " + buildDir + "  (from: " + buildDirSource + ")\n" +
            "    timeout_seconds = " + timeoutSeconds + "  (from: " + timeoutSource + ")\n";
    }

    @Override
    public String toString() {
        return "ResolvedConfig{" +
                "serviceUrl='" + serviceUrl + '\'' +
                ", licenseFile=" + licenseFile +
                ", buildDir=" + buildDir +
                ", timeoutSeconds=" + timeoutSeconds +
                '}';
    }
}
