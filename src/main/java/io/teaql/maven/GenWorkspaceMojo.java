package io.teaql.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;

/**
 * Generates a Java workspace skeleton.
 *
 * <p>Sends {@code scope=java-workspace} to the TeaQL service, then extracts
 * the returned ZIP into {@code workspaceDir} (instead of the normal build dir).
 *
 * <pre>{@code
 * mvn teaql:gen-workspace \
 *   -Dteaql.input=model \
 *   -Dteaql.workspaceDir=/path/to/workspace
 * }</pre>
 *
 * <p>Or in plugin config:
 * <pre>{@code
 * <plugin>
 *   <groupId>io.teaql</groupId>
 *   <artifactId>teaql-maven-plugin</artifactId>
 *   <executions>
 *     <execution>
 *       <goals><goal>gen-workspace</goal></goals>
 *       <configuration>
 *         <input>${project.basedir}/model</input>
 *         <workspaceDir>${project.basedir}/../my-workspace</workspaceDir>
 *       </configuration>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 */
@Mojo(name = "gen-workspace",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresProject = false,
      threadSafe = true)
public class GenWorkspaceMojo extends AbstractGenerateMojo {

    /**
     * Target directory where the workspace ZIP is extracted.
     * Defaults to {@code <project.basedir>/model} when running inside a Maven project,
     * or {@code ./model} otherwise.
     */
    @Parameter(property = "teaql.workspaceDir",
               defaultValue = "model")
    private File workspaceDir;

    @Override
    protected String getScope() {
        return "java-workspace";
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
        getLog().info("workspace dir  : " + workspaceDir.getAbsolutePath());

        File resolvedInput = resolveInput();

        GeneratorService service = new GeneratorService(getLog());
        try {
            service.generateWorkspace(resolvedInput, getScope(), resolved, workspaceDir);
        } catch (Exception e) {
            throw new MojoExecutionException("TeaQL workspace generation failed: " + e.getMessage(), e);
        } finally {
            // Clean up temp demo model if we created one
            if (input == null && resolvedInput.exists()) {
                resolvedInput.delete();
            }
        }
    }
}
