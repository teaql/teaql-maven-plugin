package io.teaql.maven;

import java.io.File;

/**
 * Fully-resolved, ready-to-use TeaQL configuration.
 *
 * <p>All paths are absolute; no further resolution is needed downstream.
 */
public class ResolvedConfig {

    private final String serviceUrl;
    private final File licenseFile;
    private final File buildDir;
    private final long timeoutSeconds;

    public ResolvedConfig(String serviceUrl, File licenseFile,
                          File buildDir, long timeoutSeconds) {
        this.serviceUrl = serviceUrl;
        this.licenseFile = licenseFile;
        this.buildDir = buildDir;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getServiceUrl() { return serviceUrl; }
    public File getLicenseFile() { return licenseFile; }
    public File getBuildDir() { return buildDir; }
    public long getTimeoutSeconds() { return timeoutSeconds; }

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
