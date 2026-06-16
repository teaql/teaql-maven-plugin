package io.teaql.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;

/**
 * Lists available generation services from the TeaQL server.
 *
 * <p>Run from the command line:
 * <pre>mvn teaql:list-services</pre>
 */
@Mojo(name = "list-services", requiresProject = false, threadSafe = true)
public class ListServicesMojo extends AbstractMojo {

    @Parameter(property = "teaql.endpointPrefix")
    protected String endpointPrefix;

    @Parameter(property = "teaql.serviceUrl")
    protected String serviceUrl;

    @Parameter(property = "teaql.apiKey")
    protected String apiKey;

    @Parameter(property = "teaql.timeoutSeconds", defaultValue = "0")
    protected long timeoutSeconds;

    @Parameter(defaultValue = "${project}", readonly = true, required = false)
    protected MavenProject project;

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
                null,
                timeoutSeconds
        );

        File cwd = (project != null) ? project.getBasedir()
                : new File(System.getProperty("user.dir"));
        ResolvedConfig resolved = fileConfig.resolve(overrides, cwd);

        TeaQLService service = new TeaQLService(getLog());
        try {
            service.printServices(resolved);
        } catch (IOException e) {
            throw new MojoExecutionException("failed to list services: " + e.getMessage(), e);
        }
    }
}
