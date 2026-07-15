package io.teaql.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;

/**
 * Evaluates a KSML model input and reports diagnostics.
 *
 * <p>Equivalent to {@code cargo-teaql eval}.
 */
@Mojo(name = "eval",
      defaultPhase = LifecyclePhase.VALIDATE,
      requiresProject = false,
      threadSafe = true)
public class EvalMojo extends AbstractGenerateMojo {


    @Parameter(property = "teaql.eval.output")
    private File evalOutput;

    @Parameter(property = "teaql.eval.failOnWarning", defaultValue = "false")
    private boolean failOnWarning;

    @Override
    protected String getScope() {
        return null;
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
        getLog().info(resolved.describeSources());

        File resolvedInput = resolveInput();

        EvaluationService service = new EvaluationService(getLog());
        try {
            boolean success = service.evaluate(resolvedInput, evalOutput, failOnWarning, resolved);
            if (!success) {
                throw new MojoFailureException("KSML model evaluation failed (see diagnostics above).");
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("TeaQL evaluation execution failed: " + e.getMessage(), e);
        } finally {
            // Clean up temp demo model if we created one
            if (input == null && resolvedInput.exists()) {
                if (!resolvedInput.delete()) {
                    getLog().warn("Failed to delete temp demo model: " + resolvedInput.getAbsolutePath());
                }
            }
        }
    }
}
