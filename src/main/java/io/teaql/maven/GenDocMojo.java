package io.teaql.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Generates documentation output.
 *
 * <p>Equivalent to {@code cargo-teaql gen-doc} — sends {@code scope=doc} to the TeaQL service.
 *
 * <pre>mvn teaql:gen-doc -Dteaql.input=model</pre>
 */
@Mojo(name = "gen-doc",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresProject = false,
      threadSafe = true)
public class GenDocMojo extends AbstractGenerateMojo {

    @Override
    protected String getScope() {
        return "doc";
    }
}
