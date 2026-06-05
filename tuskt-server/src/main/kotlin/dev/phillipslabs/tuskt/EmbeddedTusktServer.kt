package dev.phillipslabs.tuskt

import dev.phillipslabs.tuskt.store.FileSystemUploadStore
import io.ktor.server.engine.*
import java.nio.file.Path
import kotlin.io.path.Path

public const val DEFAULT_TUSKT_HOST: String = "0.0.0.0"
public const val DEFAULT_TUSKT_PORT: Int = 8080
public const val DEFAULT_TUSKT_BASE_PATH: String = "/files"
public val DEFAULT_TUSKT_STORAGE_PATH: Path = Path("files").toAbsolutePath()

public data class TusktServerConfiguration(
    val host: String = DEFAULT_TUSKT_HOST,
    val port: Int = DEFAULT_TUSKT_PORT,
    val basePath: String = DEFAULT_TUSKT_BASE_PATH,
    val storagePath: Path = DEFAULT_TUSKT_STORAGE_PATH,
)

public fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> embeddedTusktServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    configuration: TusktServerConfiguration = TusktServerConfiguration(),
): EmbeddedServer<TEngine, TConfiguration> =
    embeddedServer(
        factory,
        port = configuration.port,
        host = configuration.host,
        module = {
            tusktModule(
                basePath = configuration.basePath,
                tusktStore = FileSystemUploadStore(configuration.storagePath),
            )
        },
    )
