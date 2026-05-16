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

## Parameters

All parameters can be set via `<configuration>` or `-D` system properties.

| Parameter | Property | Default | Description |
|---|---|---|---|
| `input` | `teaql.input` | _(required)_ | Model file or directory to upload |
| `serviceUrl` | `teaql.serviceUrl` | from config file | Override service URL |
| `licenseFile` | `teaql.licenseFile` | bundled `public.LICENSE` | Override license file |
| `output` | `teaql.output` | `${project.basedir}/build` | Output directory |
| `timeoutSeconds` | `teaql.timeoutSeconds` | `300` | HTTP timeout |

## Config file

Local config lives in `~/.teaql/config.yml`:

```yaml
service_url: http://springboot.teaql-gen-code.1496855407387739.cn-chengdu.fc.devsapp.net/generate
license_file: /path/to/your.LICENSE   # optional — bundled public.LICENSE used if omitted
build_dir: build
timeout_seconds: 300
```

Plugin parameters override the config file. Config file overrides built-in defaults.

## Build

```bash
mvn install
```
