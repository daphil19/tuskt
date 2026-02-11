package dev.phillipslabs.tuskt.client

import dev.phillipslabs.tuskt.TusHeaders
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.core.*

public class TusktClient(
    // TODO can we configure the client to use the base url?
    private val client: HttpClient,
    private val baseUrl: String,
) : Closeable {
    init {
//        getTusServerInformation()
    }

    private suspend fun getTusServerInformation(): TusServerInformation {
        val response = client.options(baseUrl)
        // TODO handle 4xx and 5xx errors more gracefully
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.NoContent) {
            @Suppress("MaxLineLength")
            throw TusktException("Server information (options) call returend unexpected status code: ${response.status}")
        }

        return TusServerInformation(
            versions =
                response.headers[TusHeaders.TUS_VERSION]?.split(",")
                    ?: throw TusktException("Server did not return a Tus-Version header"),
            resumableVersion =
                response.headers[TusHeaders.TUS_RESUMABLE]
                    ?: throw TusktException("Server did not return a Tus-Resumable header"),
            // TODO extensions and max size
            extensions = response.headers[TusHeaders.TUS_EXTENSION]?.split(",") ?: emptyList(),
            maxSize = response.headers[TusHeaders.TUS_MAX_SIZE]?.toLongOrNull(),
        )
    }

    override fun close() {
        client.close()
    }
}
