package io.teaql.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Dynamically calls a generation service specified by the user.
 * This makes the plugin flexible without needing to hardcode new goals.
 *
 * <p>Run directly:
 * <pre>mvn teaql:generate -Dinput=model -Dservice=java-web-spring-boot</pre>
 */
@Mojo(name = "generate",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresProject = false,
      threadSafe = true)
public class GenerateMojo extends AbstractGenerateMojo {

    /** The name of the service/scope to invoke. */
    @Parameter(property = "service", required = true)
    protected String service;

    @Override
    protected String getScope() {
        return service;
    }
}
