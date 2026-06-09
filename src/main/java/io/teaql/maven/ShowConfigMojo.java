package io.teaql.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Displays the effective TeaQL configuration (path + contents).
 *
 * <p>Equivalent to {@code cargo-teaql show-config}.
 *
 * <pre>mvn teaql:show-config</pre>
 */
@Mojo(name = "show-config",
      requiresProject = false,
      threadSafe = true)
public class ShowConfigMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File configFile = TeaqlConfig.configFilePath();
        getLog().info("config_path: " + configFile.getAbsolutePath());

        TeaqlConfig config;
        try {
            config = TeaqlConfig.load();
        } catch (IOException e) {
            throw new MojoExecutionException("failed to load TeaQL config", e);
        }

        // Render as YAML (block style, matching Rust serde_yaml output)
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("endpoint_prefix", config.getEndpointPrefix());
        data.put("api_key", config.getApiKey());
        data.put("build_dir", config.getBuildDir());
        data.put("timeout_seconds", config.getTimeoutSeconds());

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);
        getLog().info("\n" + yaml.dump(data));
    }
}
