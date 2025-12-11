# EventB Generator

Utility for composing Event-B artefacts from pattern XML bundles. It can run as a
CLI or via the built-in web UI.

## Prerequisites

- Java 17 or newer on your `PATH`.
- Apache Maven 3.8+.
- Git + GNU Make (optional, only needed for the convenience targets).
  - macOS includes `make`.
  - On Windows install `git` and a Make distribution (Chocolatey: `choco install make`,
    Scoop: `scoop install make`, or MSYS2). The provided `Makefile` detects Windows
    automatically, so the same commands work on both platforms.

## Build

```bash
mvn -DskipTests package
# or
make build
```

The build emits `target/eventb-generator-0.1.0-shaded.jar`.

## CLI Usage

```
java -jar target/eventb-generator-0.1.0-shaded.jar \
  -i node_Structure_2_xml/IActivate.xml,node_Structure_2_xml/ISensingUnit.xml \
  -p MyProject \
  -o generated
```

- `-i`: comma-separated list of pattern XML files.
- `-p`: Rodin project name.
- `-o`: workspace directory where the project should be written.

## Web UI

```bash
make serve
# or, explicitly:
java -jar target/eventb-generator-0.1.0-shaded.jar --server --port 8080 -o generated
```

Open <http://localhost:8080>, select patterns per refinement, and download the
generated Rodin project archive.

## Cleaning Output

```bash
make clean-generated   # remove generated Rodin projects
make clean             # also removes the Maven target directory
```
