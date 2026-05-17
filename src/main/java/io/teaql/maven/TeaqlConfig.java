package io.teaql.maven;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TeaQL local configuration, mirroring ~/.teaql/config.yml.
 *
 * <p>Precedence (highest wins): Mojo parameter overrides → config file → built-in defaults.
 */
public class TeaqlConfig {

    public static final String DEFAULT_SERVICE_URL =
            "https://api.teaql.io/latest/";
    public static final String DEFAULT_BUILD_DIR = "build";
    public static final long DEFAULT_TIMEOUT_SECONDS = 300L;

    private String serviceUrl = DEFAULT_SERVICE_URL;
    private String licenseFile;          // null → fall back to bundled public.LICENSE
    private String buildDir = DEFAULT_BUILD_DIR;
    private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    // ── constructors ────────────────────────────────────────────────────────

    public TeaqlConfig() {}

    // ── factory ─────────────────────────────────────────────────────────────

    /**
     * Loads config from {@code ~/.teaql/config.yml}.
     * Returns defaults if the file does not exist.
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
            if (data.containsKey("service_url") && data.get("service_url") != null) {
                cfg.serviceUrl = data.get("service_url").toString();
            }
            if (data.containsKey("license_file") && data.get("license_file") != null) {
                cfg.licenseFile = data.get("license_file").toString();
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
        data.put("service_url", serviceUrl);
        if (licenseFile != null) {
            data.put("license_file", licenseFile);
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
     * Applies Mojo-level parameter overrides and resolves all paths relative to {@code cwd}.
     *
     * <p>Precedence (highest wins): Mojo parameter / env var → config file → built-in default.
     *
     * @param overrides  parameter overrides from the Mojo (any field may be null/0 = no override)
     * @param cwd        the Maven project's base directory
     * @return a fully-resolved, ready-to-use config
     */
    public ResolvedConfig resolve(ConfigOverrides overrides, File cwd) {
        // ── service_url: mojo/env > config.yml > default ──
        String resolvedServiceUrl;
        String serviceUrlSource;
        String envServiceUrl = System.getenv("TEAQL_SERVICE_URL");
        if (overrides.getServiceUrl() != null) {
            resolvedServiceUrl = overrides.getServiceUrl();
            serviceUrlSource = "mojo parameter (-Dteaql.serviceUrl)";
        } else if (envServiceUrl != null && !envServiceUrl.isBlank()) {
            resolvedServiceUrl = envServiceUrl;
            serviceUrlSource = "env TEAQL_SERVICE_URL";
        } else {
            resolvedServiceUrl = serviceUrl;
            serviceUrlSource = "~/.teaql/config.yml (or built-in default)";
        }

        // ── license_file: mojo/env > config.yml > default ──
        File resolvedLicenseFile;
        String licenseSource;
        String envLicense = System.getenv("TEAQL_LICENSE_FILE");
        if (overrides.getLicenseFile() != null) {
            resolvedLicenseFile = normalizePath(overrides.getLicenseFile(), cwd);
            licenseSource = "mojo parameter (-Dteaql.licenseFile)";
        } else if (envLicense != null && !envLicense.isBlank()) {
            resolvedLicenseFile = normalizePath(envLicense, cwd);
            licenseSource = "env TEAQL_LICENSE_FILE";
        } else if (licenseFile != null) {
            resolvedLicenseFile = normalizePath(licenseFile, cwd);
            licenseSource = "~/.teaql/config.yml";
        } else {
            resolvedLicenseFile = defaultLicensePath();
            licenseSource = "built-in default";
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

        return new ResolvedConfig(resolvedServiceUrl, resolvedLicenseFile,
                resolvedBuildDir, resolvedTimeout,
                serviceUrlSource, licenseSource, buildDirSource, timeoutSource);
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
     * Path to the bundled {@code public.LICENSE} that ships inside the plugin jar.
     * At runtime, this file is extracted to a temp location by {@link GeneratorService}.
     */
    public static File defaultLicensePath() {
        // Resolved at runtime via classpath; this placeholder is overridden in GeneratorService.
        return new File("assets/public.LICENSE");
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

    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }

    public String getLicenseFile() { return licenseFile; }
    public void setLicenseFile(String licenseFile) { this.licenseFile = licenseFile; }

    public String getBuildDir() { return buildDir; }
    public void setBuildDir(String buildDir) { this.buildDir = buildDir; }

    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    @Override
    public String toString() {
        return "TeaqlConfig{" +
                "serviceUrl='" + serviceUrl + '\'' +
                ", licenseFile='" + licenseFile + '\'' +
                ", buildDir='" + buildDir + '\'' +
                ", timeoutSeconds=" + timeoutSeconds +
                '}';
    }
}
