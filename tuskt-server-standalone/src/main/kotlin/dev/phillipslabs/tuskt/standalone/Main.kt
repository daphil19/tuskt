package dev.phillipslabs.tuskt.standalone

import dev.phillipslabs.tuskt.DEFAULT_TUSKT_BASE_PATH
import dev.phillipslabs.tuskt.DEFAULT_TUSKT_HOST
import dev.phillipslabs.tuskt.DEFAULT_TUSKT_PORT
import dev.phillipslabs.tuskt.DEFAULT_TUSKT_STORAGE_PATH
import dev.phillipslabs.tuskt.TusktServerConfiguration
import dev.phillipslabs.tuskt.embeddedTusktServer
import io.ktor.server.netty.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

fun main() {
    val host = systemPropertyOrEnv("tuskt.host", "TUSKT_HOST") ?: DEFAULT_TUSKT_HOST
    val port = systemPropertyOrEnv("tuskt.port", "TUSKT_PORT")?.toIntOrNull() ?: DEFAULT_TUSKT_PORT
    val basePath = systemPropertyOrEnv("tuskt.basePath", "TUSKT_BASE_PATH") ?: DEFAULT_TUSKT_BASE_PATH
    val storagePath =
        (systemPropertyOrEnv("tuskt.storagePath", "TUSKT_STORAGE_PATH")?.let(::Path) ?: DEFAULT_TUSKT_STORAGE_PATH)
            .toAbsolutePath()

    val server =
        embeddedTusktServer(
            factory = Netty,
            configuration =
                TusktServerConfiguration(
                    host = host,
                    port = port,
                    basePath = basePath,
                    storagePath = storagePath,
                ),
        )

    println("Tuskt server listening on http://$host:$port$basePath using ${storagePath.absolutePathString()}")

    server.start(wait = true)
}

private fun systemPropertyOrEnv(
    propertyName: String,
    environmentVariableName: String,
): String? = System.getProperty(propertyName) ?: System.getenv(environmentVariableName)
