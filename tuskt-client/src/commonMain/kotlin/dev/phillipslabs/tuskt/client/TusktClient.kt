package dev.phillipslabs.tuskt.client

import dev.phillipslabs.tuskt.OffsetOctetStream
import dev.phillipslabs.tuskt.TusHeaders
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

public class TusktClient(
    // TODO can we configure the client to use the base url?
    private val client: HttpClient,
    private val baseUrl: String,
) : Closeable {
    public suspend fun getTusServerInformation(): TusServerInformation = getTusServerInformation(client, baseUrl)

    public suspend fun getUploadOffset(id: String): TusUploadOffsetResponse? {
        val response = client.head("/$id")

        return when (response.status) {
            HttpStatusCode.OK, HttpStatusCode.NoContent -> {
                val uploadOffsetString = response.headers[TusHeaders.UPLOAD_OFFSET]
                // non-negative validation happens during construction
                TusUploadOffsetResponse(
                    uploadOffsetString?.toLongOrNull()
                        ?: throw TusktException(
                            "Invalid header value for ${TusHeaders.UPLOAD_OFFSET}: $uploadOffsetString",
                        ),
                    response.headers[TusHeaders.UPLOAD_LENGTH]?.toLongOrNull(),
                )
            }

            HttpStatusCode.Forbidden, HttpStatusCode.NotFound, HttpStatusCode.Gone -> {
                null
            }

            else -> {
                throw TusktException("Unexepected status code ${response.status}")
            }
        }
    }

    // TODO alternative that allows for streaming?
    // TODO accounting for max size!
    public suspend fun uploadBytes(
        bytes: ByteArray,
        id: String,
        offset: Long,
    ) {
        val response =
            client.patch("/$id") {
                contentType(ContentType.Application.OffsetOctetStream)
                setBody(bytes)
                header(TusHeaders.UPLOAD_OFFSET, offset.toString())
            }
        TODO()
    }

    public suspend fun uploadStream(
        stream: ByteReadChannel,
        id: String,
        offset: Long,
    ) {
        val response =
            client.patch("/$id") {
                contentType(ContentType.Application.OffsetOctetStream)
                setBody(stream)
                header(TusHeaders.UPLOAD_OFFSET, offset.toString())
            }
        TODO()
    }

    override fun close() {
        client.close()
    }

    public companion object {
        // TODO factory method that helps configure the client (e.g. calls server info and such)
        public suspend fun initialize(client: HttpClient) {
            // TODO use a client to get the server options first, then configure
//            val tusServerInformation = getTusServerInformation(client, "")
//            val newClient =
//                client.config {
//                    defaultRequest {
//                        header(TusHeaders.TUS_RESUMABLE, TODO("Version from server in options request!"))
//                    }
//                }
        }

        private suspend fun getTusServerInformation(
            client: HttpClient,
            url: String,
        ): TusServerInformation {
            val response =
                client.options(url) {
                    // we want to make sure we _don't_ send the Tus-Resumable header, per the spec
                    headers.remove(TusHeaders.TUS_RESUMABLE)
                }
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
    }
}
