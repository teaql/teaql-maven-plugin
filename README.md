# teaql-maven-plugin

Maven plugin for TeaQL code generation workflows.

## Goals

| Goal | Phase | Description |
|---|---|---|
| `teaql:gen-code` | `generate-sources` | Generate backend / domain code |
| `teaql:gen-doc` | `generate-sources` | Generate documentation |
| `teaql:gen-model` | `generate-sources` | Generate frontend model |
| `teaql:gen-workspace` | `generate-sources` | Generate a complete workspace skeleton (Gradle + Spring Boot) |
| `teaql:show-config` | — | Print effective config |

## Quick start

### 1. Initialize a new project

Generate a ready-to-run Gradle + Spring Boot workspace from your model:

```bash
mvn io.teaql:teaql-maven-plugin:0.1.6:gen-workspace \
  -Dteaql.input=src/main/model
```

This downloads and extracts a complete workspace skeleton into `${project.basedir}/model`. You can also specify a custom target directory:

```bash
mvn io.teaql:teaql-maven-plugin:0.1.6:gen-workspace \
  -Dteaql.input=my-model.xml \
  -Dteaql.workspaceDir=./my-project
```

Without `-Dteaql.input`, the built-in demo model is used — handy for a smoke test:

```bash
mvn io.teaql:teaql-maven-plugin:0.1.6:gen-workspace
# → extracts into ./model
cd model
./gradlew bootRun
# → http://localhost:8080/version
```

### 2. Use in a project

Add the plugin to `pom.xml` to generate code during the build:

```xml
<plugin>
  <groupId>io.teaql</groupId>
  <artifactId>teaql-maven-plugin</artifactId>
  <version>0.1.6</version>
  <executions>
    <execution>
      <goals><goal>gen-code</goal></goals>
      <configuration>
        <input>${project.basedir}/src/main/model</input>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### 3. On-demand commands

```bash
mvn teaql:gen-code     -Dteaql.input=src/main/model
mvn teaql:gen-doc      -Dteaql.input=src/main/model
mvn teaql:gen-model    -Dteaql.input=src/main/model
mvn teaql:show-config
```

## Configuration

### Precedence (highest wins)

```
Mojo parameter / -D flag  >  Environment variable  >  config.yml  >  Built-in default
```

### Parameters

All parameters can be set via `<configuration>`, `-D` system properties, or environment variables.

| Parameter | Property | Env var | Default | Description |
|---|---|---|---|---|
| `input` | `teaql.input` | — | _(optional)_ | Model file or directory to upload |
| `serviceUrl` | `teaql.serviceUrl` | `TEAQL_SERVICE_URL` | `https://api.teaql.io/latest/` | Service endpoint URL |
| `licenseFile` | `teaql.licenseFile` | `TEAQL_LICENSE_FILE` | bundled `public.LICENSE` | License file path |
| `output` | `teaql.output` | `TEAQL_BUILD_DIR` | `${project.basedir}/build` | Output directory (gen-code/doc/model) |
| `workspaceDir` | `teaql.workspaceDir` | — | `${project.basedir}/model` | Workspace extraction directory (gen-workspace) |
| `timeoutSeconds` | `teaql.timeoutSeconds` | `TEAQL_TIMEOUT_SECONDS` | `300` | HTTP timeout in seconds |

### Config file

Local config lives in `~/.teaql/config.yml`:

```yaml
service_url: https://api.teaql.io/latest/
license_file: /path/to/your.LICENSE   # optional — bundled public.LICENSE used if omitted
build_dir: build
timeout_seconds: 300
```

### Source tracking

At startup, the plugin logs where each effective config value came from:

```
[INFO]   config (precedence: mojo/env > config.yml > default):
[INFO]     service_url     = https://api.teaql.io/latest/  (from: ~/.teaql/config.yml (or built-in default))
[INFO]     license_file    = /home/user/.teaql/license       (from: env TEAQL_LICENSE_FILE)
[INFO]     build_dir       = /workspace/project/build        (from: mojo parameter (-Dteaql.output))
[INFO]     timeout_seconds = 300                             (from: ~/.teaql/config.yml (or built-in default))
```

## Build

```bash
mvn install
```
