package io.teaql.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Dynamically calls a generation service specified by the user.
 * This makes the plugin flexible without needing to hardcode new goals.
 *
 * <p>Run directly:
 * <pre>mvn teaql:gen-service -Dteaql.input=model -Dteaql.service=java-web-spring-boot</pre>
 */
@Mojo(name = "gen-service",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresProject = false,
      threadSafe = true)
public class GenServiceMojo extends AbstractGenerateMojo {

    /** The name of the service/scope to invoke. */
    @Parameter(property = "teaql.service", required = true)
    protected String service;

    @Override
    protected String getScope() {
        return service;
    }
}
