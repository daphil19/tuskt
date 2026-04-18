# Tuskt

Tuskt is a Kotlin implementation of the [tus resumable upload protocol](https://tus.io/).

The repository currently contains:

- `:tuskt-client`: a Kotlin Multiplatform tus client
- `:tuskt-server`: a Ktor-based tus server library
- `:tuskt-server-standalone`: a runnable server fat jar
- `:shared`: shared tus protocol constants and primitives
- `:integration-tests`: end-to-end client/server interoperability tests

## Using Tuskt In Your Build

Published library modules:

- `dev.phillipslabs:tuskt-client`
- `dev.phillipslabs:tuskt-server`
- `dev.phillipslabs:tuskt-shared`

Choose the module based on how you want to consume the project:

- `tuskt-client`: add a multiplatform tus client to your application
- `tuskt-server`: embed tus endpoints in a Ktor server
- `tuskt-shared`: use protocol constants and shared primitives directly
- `tuskt-server-standalone`: build and run the fat jar instead of adding a library dependency

Gradle Kotlin DSL examples:

```kotlin
dependencies {
    implementation("dev.phillipslabs:tuskt-client:<version>")
    implementation("dev.phillipslabs:tuskt-server:<version>")
    implementation("dev.phillipslabs:tuskt-shared:<version>")
}
```

For Kotlin Multiplatform, the client dependency typically belongs in `commonMain`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.phillipslabs:tuskt-client:<version>")
        }
    }
}
```

## Status

Tuskt is early-stage and currently implements the core resumable upload flow around:

- `OPTIONS` for server capability discovery
- `HEAD` for upload offset lookup
- `PATCH` for resuming/appending upload bytes

The current server does not yet implement tus extensions such as creation. You can see that reflected in the ignored server tests and TODOs in the codebase.

## Current Capabilities

### Client

The multiplatform client currently supports:

- initializing against a tus server via `OPTIONS`
- discovering advertised tus versions, extensions, and max size
- retrieving upload offsets with `HEAD`
- uploading byte arrays with `PATCH`

Targets currently configured in Gradle:

- JVM
- Android
- JS
- Wasm JS
- Apple targets: macOS, iOS, watchOS, tvOS
- Linux: `linuxX64`, `linuxArm64`
- Windows: `mingwX64`

### Server

The Ktor server library currently supports:

- `OPTIONS {basePath}` returning `Tus-Version`
- `HEAD {basePath}/{id}` returning `Upload-Offset`
- `PATCH {basePath}/{id}` with `application/offset+octet-stream`
- `Tus-Resumable` version enforcement
- path normalization checks to prevent escaping the configured storage directory
- `X-HTTP-Method-Override` support via Ktor's method override plugin

### Shared Module

The shared module exposes protocol headers and constants used by both the client and server, including `TusHeaders`, `TUS_VERSION`, and `TUS_RESUME_VERSION`.

## Requirements

- JDK 17
- Gradle wrapper included in the repository

The project currently uses:

- Kotlin `2.3.20`
- Ktor `3.4.2`

## Build

From the repository root:

```bash
./gradlew build
```

Useful module-level commands:

```bash
./gradlew :tuskt-client:build
./gradlew :tuskt-server:build
./gradlew :shared:build
./gradlew :integration-tests:build
```

## Test And Lint

Run the full verification suite:

```bash
./gradlew check test
```

Common targeted commands:

```bash
./gradlew :tuskt-server:test
./gradlew :tuskt-client:jvmTest
./gradlew :shared:jvmTest
./gradlew :integration-tests:test
./gradlew ktlintCheck detekt
```

## Using The Client

`TusktClient` wraps a Ktor `HttpClient` and configures the required tus headers after initialization:

```kotlin
import dev.phillipslabs.tuskt.client.TusktClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*

suspend fun example() {
    val client =
        TusktClient.initialize(
            client =
                HttpClient(CIO) {
                    expectSuccess = false
                },
            baseUrl = "http://localhost:8080/files",
        )

    client.use { tus ->
        val offset = tus.getUploadOffset("upload-123")
        val result = tus.uploadBytes("hello".encodeToByteArray(), id = "upload-123", offset = 0)
    }
}
```

Today, `uploadBytes` assumes the upload resource already exists on the server.

## Embedding The Server

You can embed the server in a Ktor engine using `embeddedTusktServer`:

```kotlin
import dev.phillipslabs.tuskt.TusktServerConfiguration
import dev.phillipslabs.tuskt.embeddedTusktServer
import io.ktor.server.netty.*
import kotlin.io.path.Path

fun main() {
    embeddedTusktServer(
        factory = Netty,
        configuration =
            TusktServerConfiguration(
                host = "127.0.0.1",
                port = 8080,
                basePath = "/files",
                storagePath = Path("files"),
            ),
    ).start(wait = true)
}
```

Defaults:

- host: `0.0.0.0`
- port: `8080`
- base path: `/files`
- storage path: `<repo-or-process-working-dir>/files`

## Running The Standalone Server

Build the fat jar:

```bash
./gradlew :tuskt-server-standalone:shadowJar
```

Run it:

```bash
java -jar tuskt-server-standalone/build/libs/tuskt-server-standalone-0.1.0-SNAPSHOT.jar
```

Configuration is available through either system properties or environment variables:

- `tuskt.host` or `TUSKT_HOST`
- `tuskt.port` or `TUSKT_PORT`
- `tuskt.basePath` or `TUSKT_BASE_PATH`
- `tuskt.storagePath` or `TUSKT_STORAGE_PATH`

Example:

```bash
TUSKT_STORAGE_PATH=/tmp/tuskt-files TUSKT_PORT=9000 \
java -jar tuskt-server-standalone/build/libs/tuskt-server-standalone-0.1.0-SNAPSHOT.jar
```

## Published Artifacts

- `dev.phillipslabs:tuskt-client`: Kotlin Multiplatform tus client
- `dev.phillipslabs:tuskt-server`: Ktor server library
- `dev.phillipslabs:tuskt-shared`: shared protocol constants and primitives


## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for local setup, coding conventions, and test guidance.
