package io.teaql.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Generates frontend model output.
 *
 * <p>Equivalent to {@code cargo-teaql gen-model} — sends {@code scope=frontend} to the TeaQL
 * service. Follows the same behaviour as the original {@code generateModel.sh}.
 *
 * <pre>mvn teaql:gen-model -Dteaql.input=src/main/model</pre>
 */
@Mojo(name = "gen-model",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresProject = false,
      threadSafe = true)
public class GenModelMojo extends AbstractGenerateMojo {

    @Override
    protected String getScope() {
        return "frontend";
    }
}
