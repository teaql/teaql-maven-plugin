package io.teaql.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;

/**
 * Base Mojo for all TeaQL generation goals.
 *
 * <p>Handles configuration loading and resolution, then delegates to
 * {@link GeneratorService} with a goal-specific {@code scope}.
 */
public abstract class AbstractGenerateMojo extends AbstractMojo {

    /** Model file or directory to upload. */
    @Parameter(property = "teaql.input", required = true)
    protected File input;

    /** Override the TeaQL endpoint prefix, e.g. {@code https://api.teaql.io/latest/}. */
    @Parameter(property = "teaql.endpointPrefix")
    protected String endpointPrefix;

    /** Override the TeaQL service URL. @deprecated use {@code teaql.endpointPrefix}. */
    @Deprecated
    @Parameter(property = "teaql.serviceUrl")
    protected String serviceUrl;

    /** Override the license file path. */
    @Parameter(property = "teaql.licenseFile")
    protected String licenseFile;

    /** Override the output (build) directory. */
    @Parameter(property = "teaql.output",
               defaultValue = "${project.basedir}/build")
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
     *   <li>{@code null}        → gen-code (backend/domain code)</li>
     *   <li>{@code "doc"}       → gen-doc</li>
     *   <li>{@code "frontend"}  → gen-model</li>
     * </ul>
     */
    protected abstract String getScope();

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
                licenseFile,
                output,
                timeoutSeconds
        );

        File cwd = (project != null) ? project.getBasedir()
                : new File(System.getProperty("user.dir"));
        ResolvedConfig resolved = fileConfig.resolve(overrides, cwd);

        getLog().debug("resolved config: " + resolved);

        // Print where each config value came from
        getLog().info(resolved.describeSources());

        GeneratorService service = new GeneratorService(getLog());
        try {
            service.generate(input, getScope(), resolved);
        } catch (Exception e) {
            throw new MojoExecutionException("TeaQL generation failed: " + e.getMessage(), e);
        }
    }
}
