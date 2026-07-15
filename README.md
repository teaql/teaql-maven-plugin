# teaql-maven-plugin

[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/13619/badge)](https://www.bestpractices.dev/projects/13619)

Maven plugin for TeaQL code generation workflows.

## Goals

| Goal | Phase | Description |
|---|---|---|
| `teaql:generate` | `generate-sources` | Generate code for a specified dynamic service (e.g. `java-app-console`, `rust-lib-core`) |
| `teaql:ping` | — | Verify connectivity to the TeaQL service |
| `teaql:show-config` | — | Print effective config |

## Quick start

### 1. Initialize a new project

Generate a ready-to-run application from your model by specifying a `service` target:

```bash
mvn io.teaql:teaql-maven-plugin:1.1.0:generate \
  -Dservice=java-app-console \
  -Dinput=model
```

This generates code and places it into the configured output directory (defaults to `${project.basedir}/build`).

You can dynamically call any service endpoint defined by your remote TeaQL generator, meaning the Maven plugin never needs an update when a new code generation framework is supported on the backend!

### 2. Use in a project

Add the plugin to `pom.xml` to generate code during the build:

```xml
<plugin>
  <groupId>io.teaql</groupId>
  <artifactId>teaql-maven-plugin</artifactId>
  <version>1.1.0</version>
  <executions>
    <execution>
      <goals><goal>generate</goal></goals>
      <configuration>
        <service>java-app-console</service> <!-- Replace with your target service -->
        <input>${project.basedir}/model</input>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### 3. On-demand commands

```bash
mvn teaql:generate -Dservice=java-app-console -Dinput=model
mvn teaql:ping
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
| `service` | `service` | — | — | **(Required)** The target generator service (e.g., `java-app-console`) |
| `input` | `input` | — | _(optional)_ | Model file or directory to upload |
| `endpointPrefix` | `teaql.endpointPrefix` | `TEAQL_ENDPOINT_PREFIX` | `https://api.teaql.io/latest/` | Service endpoint URL |
| `apiKey` | `teaql.apiKey` | `TEAQL_API_KEY` | `********` | API Key for service access |
| `output` | `teaql.output` | `TEAQL_BUILD_DIR` | `${project.basedir}/build` | Output directory |
| `timeoutSeconds` | `teaql.timeoutSeconds` | `TEAQL_TIMEOUT_SECONDS` | `300` | HTTP timeout in seconds |

### Config file

Local config lives in `~/.teaql/config.yml`:

```yaml
endpoint_prefix: https://api.teaql.io/latest/
api_key: YOUR_API_KEY
build_dir: build
timeout_seconds: 300
```

### Source tracking

At startup, the plugin logs where each effective config value came from:

```
[INFO]   config (precedence: mojo/env > config.yml > default):
[INFO]     endpoint_prefix = https://api.teaql.io/latest/        (from: ~/.teaql/config.yml (or built-in default))
[INFO]     api_key         = ********                      (from: built-in default)
[INFO]     build_dir       = /workspace/project/build      (from: mojo parameter (-Dteaql.output))
[INFO]     timeout_seconds = 300                           (from: ~/.teaql/config.yml (or built-in default))
```

## Build

```bash
mvn install
```
