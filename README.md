# teaql-maven-plugin

Maven plugin for TeaQL code generation workflows.

## Goals

| Goal | Phase | Description |
|---|---|---|
| `teaql:gen-code` | `generate-sources` | Generate backend / domain code |
| `teaql:gen-doc` | `generate-sources` | Generate documentation |
| `teaql:gen-model` | `generate-sources` | Generate frontend model |
| `teaql:show-config` | — | Print effective config |

## Quick start

```xml
<plugin>
  <groupId>io.teaql</groupId>
  <artifactId>teaql-maven-plugin</artifactId>
  <version>0.1.0</version>
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

Run on-demand:

```bash
mvn teaql:gen-code  -Dteaql.input=src/main/model
mvn teaql:gen-doc   -Dteaql.input=src/main/model
mvn teaql:gen-model -Dteaql.input=src/main/model
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
| `input` | `teaql.input` | — | _(required)_ | Model file or directory to upload |
| `serviceUrl` | `teaql.serviceUrl` | `TEAQL_SERVICE_URL` | `https://api.teaql.io/latest/` | Service endpoint URL |
| `licenseFile` | `teaql.licenseFile` | `TEAQL_LICENSE_FILE` | bundled `public.LICENSE` | License file path |
| `output` | `teaql.output` | `TEAQL_BUILD_DIR` | `${project.basedir}/build` | Output directory |
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
