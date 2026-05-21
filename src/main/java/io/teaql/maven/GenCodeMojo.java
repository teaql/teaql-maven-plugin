package io.teaql.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Generates backend / domain code.
 *
 * <p>Equivalent to {@code cargo-teaql gen-code} — sends no {@code scope} parameter to the
 * TeaQL service.
 *
 * <pre>{@code
 * <plugin>
 *   <groupId>io.teaql</groupId>
 *   <artifactId>teaql-maven-plugin</artifactId>
 *   <version>0.1.0</version>
 *   <executions>
 *     <execution>
 *       <goals><goal>gen-code</goal></goals>
 *       <configuration>
 *         <input>${project.basedir}/model</input>
 *       </configuration>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 *
 * <p>Or run directly:
 * <pre>mvn teaql:gen-code -Dteaql.input=model</pre>
 */
@Mojo(name = "gen-code",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresProject = false,
      threadSafe = true)
public class GenCodeMojo extends AbstractGenerateMojo {

    @Override
    protected String getScope() {
        return null; // no scope → backend/domain code
    }
}
