package dev.phillipslabs.tuskt.client

import dev.phillipslabs.tuskt.OffsetOctetStream
import dev.phillipslabs.tuskt.TusHeaders
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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

    public suspend fun uploadBytes(
        bytes: ByteArray,
        id: String,
        offset: Long,
    ): TusktUploadResult {
        // TODO add retry support?

        val expectedFinalOffset = offset + bytes.size
        var currentOffset = offset

        while (currentOffset < expectedFinalOffset) {
            val response =
                client.patch("/$id") {
                    contentType(ContentType.Application.OffsetOctetStream)
                    setBody(bytes.copyOfRange((currentOffset - offset).toInt(), bytes.size))
                    header(TusHeaders.UPLOAD_OFFSET, currentOffset.toString())
                }
            when (val tusResponse = handleUploadResponse(response)) {
                is TusktUploadResult.Success -> {
                    if (tusResponse.offset < expectedFinalOffset) {
                        currentOffset = tusResponse.offset
                    } else {
                        return tusResponse
                    }
                }

                else -> {
                    return tusResponse
                }
            }
        }

        // ending up here should never really happen unless we gave empty data or have something go wrong in the loop
        throw TusktException("Bad upload offset provided")
    }

    public suspend fun uploadStream(
        stream: ByteReadChannel,
        id: String,
        offset: Long,
    ) {
        // Instead of looking for a specific offset here, we read until either
        // 1. the channel is done
        // 2. we hit max retires
        // 3. we hit max upload of the server

        val response =
            client.patch("/$id") {
                contentType(ContentType.Application.OffsetOctetStream)
                setBody(stream)
                header(TusHeaders.UPLOAD_OFFSET, offset.toString())
            }

        val serverResponseOffset = handleUploadResponse(response)

        TODO()
    }

    private fun handleUploadResponse(response: HttpResponse): TusktUploadResult =
        when (response.status) {
            HttpStatusCode.NoContent -> {
                TusktUploadResult.Success(
                    response.headers[TusHeaders.UPLOAD_OFFSET]?.toLongOrNull()
                        ?: throw TusktException("Server did not set Upload-Offset header"),
                )
            }

            HttpStatusCode.UnsupportedMediaType -> {
                // TODO log this!
                throw TusktException("Unexpected UnsupportedMediaType response from server")
            }

            HttpStatusCode.Conflict -> {
                // TODO make a more specific exception?
                // do we want to try and recover from this one?
                throw TusktException(
                    @Suppress("MaxLineLength")
                    "Upload offset mismatch; client_offset=${response.request.headers[TusHeaders.UPLOAD_OFFSET]}, server_offset=${response.headers[TusHeaders.UPLOAD_OFFSET]}",
                )
            }

            HttpStatusCode.NotFound -> {
                throw TusktException("Upload id not found on server")
            }

            else -> {
                // TODO log!
                // status code was not expected from a protocol perspective. Maybe we should add some that help manage timeouts, etc.
                throw TusktException("Unexpected response from server: ${response.status}")
            }
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
                throw TusktException("Server information (options) call returned unexpected status code: ${response.status}")
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
