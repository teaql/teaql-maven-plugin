package io.teaql.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Base Mojo for all TeaQL generation goals.
 *
 * <p>Handles configuration loading and resolution, then delegates to
 * {@link GeneratorService} with a goal-specific {@code scope}.
 */
public abstract class AbstractGenerateMojo extends AbstractMojo {

    /** Model file or directory to upload. Falls back to the built-in demo model. */
    @Parameter(property = "teaql.input", required = false)
    protected File input;

    /** Override the TeaQL endpoint prefix, e.g. {@code https://api.teaql.io/latest/}. */
    @Parameter(property = "teaql.endpointPrefix")
    protected String endpointPrefix;

    /** Override the TeaQL service URL. @deprecated use {@code teaql.endpointPrefix}. */
    @Deprecated
    @Parameter(property = "teaql.serviceUrl")
    protected String serviceUrl;

    /** Override the API Key. */
    @Parameter(property = "teaql.apiKey")
    protected String apiKey;

    /** Override the output (build) directory. */
    @Parameter(property = "teaql.output",
               defaultValue = "generated-lib")
    protected String output;

    /** Override the request timeout in seconds. */
    @Parameter(property = "teaql.timeoutSeconds", defaultValue = "0")
    protected long timeoutSeconds;

    /** Injected Maven project, used to resolve the basedir. Optional — falls back to user.dir. */
    @Parameter(defaultValue = "${project}", readonly = true, required = false)
    protected MavenProject project;

    /**
     * Returns the {@code scope} parameter for the remote service.
     *
     * <ul>
     *   <li>{@code null}            → gen-lib (backend/domain library code)</li>
     *   <li>{@code "doc"}           → gen-doc</li>
     *   <li>{@code "frontend"}      → gen-model</li>
     *   <li>{@code "java-workspace"}→ gen-workspace</li>
     * </ul>
     */
    protected abstract String getScope();

    /**
     * Resolves the model input. If no input was provided, extracts the built-in
     * {@code demo-service.xml} to a temp file.
     */
    protected File resolveInput() throws MojoExecutionException {
        if (input != null) {
            return input;
        }
        getLog().info("no input provided, using built-in demo model");
        try (InputStream in = getClass().getResourceAsStream("/assets/demo-service.xml")) {
            if (in == null) {
                throw new MojoExecutionException(
                        "bundled demo-service.xml not found in plugin classpath");
            }
            File temp = File.createTempFile("teaql-demo-", ".xml");
            try (OutputStream out = new FileOutputStream(temp)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            return temp;
        } catch (IOException e) {
            throw new MojoExecutionException("failed to load built-in demo model", e);
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        TeaqlConfig fileConfig;
        try {
            fileConfig = TeaqlConfig.load();
        } catch (IOException e) {
            throw new MojoExecutionException("failed to load TeaQL config", e);
        }

        ConfigOverrides overrides = new ConfigOverrides(
                endpointPrefix,
                serviceUrl,
                apiKey,
                output,
                timeoutSeconds
        );

        File cwd = (project != null) ? project.getBasedir()
                : new File(System.getProperty("user.dir"));
        ResolvedConfig resolved = fileConfig.resolve(overrides, cwd);

        getLog().debug("resolved config: " + resolved);

        // Print where each config value came from
        getLog().info(resolved.describeSources());

        File resolvedInput = resolveInput();

        GeneratorService service = new GeneratorService(getLog());
        try {
            service.generate(resolvedInput, getScope(), resolved);
        } catch (Exception e) {
            throw new MojoExecutionException("TeaQL generation failed: " + e.getMessage(), e);
        } finally {
            // Clean up temp demo model if we created one
            if (input == null && resolvedInput.exists()) {
                resolvedInput.delete();
            }
        }
    }
}
