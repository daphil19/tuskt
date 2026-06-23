package dev.phillipslabs.tuskt

import dev.phillipslabs.tuskt.extensions.TusExtension
import dev.phillipslabs.tuskt.store.FileSystemUploadStore
import dev.phillipslabs.tuskt.store.TusktStore
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.methodoverride.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

public fun Application.tusktModule(
    basePath: String = "/files",
    tusktStore: TusktStore = FileSystemUploadStore(),
    extensions: List<TusExtension> = emptyList(),
): Unit =
    tusktModule(
        TusktConfig().apply {
            this.basePath = basePath
            this.tusktStore = tusktStore
        },
        extensions,
    )

@Suppress("LongMethod")
public fun Application.tusktModule(
    config: TusktConfig,
    extensions: List<TusExtension> = emptyList(),
) {
    install(XHttpMethodOverride)

    install(TusResumableVersionCheck)
    install(TusResponseHeaders)

    routing {
        route(config.basePath) {
            route("/{id}") {
                // core protocol - upload status/offset
                head {
                    // TODO if creation is enabled we need to check for upload-defer-length

                    // spec says we must return this
                    call.response.header(HttpHeaders.CacheControl, "no-store")

                    // this throws a bad request if id isn't found, but if id isn't found we shouldn't ever get here?
                    val id = call.requirePathParameter("id")
                    val tusktUploadMetadata =
                        config.tusktStore.getInfo(id) ?: run {
                            call.respond(HttpStatusCode.NotFound)
                            return@head
                        }

                    call.response.header(TusHeaders.UPLOAD_OFFSET, tusktUploadMetadata.currentOffset)
                    tusktUploadMetadata.expectedUploadLength?.let {
                        call.response.header(TusHeaders.UPLOAD_LENGTH, it)
                    }
                    // while the spec says that we SHOULD use a 200 or 204 for successful responses,
                    // the openAPI spec just uses 200
                    call.respond(HttpStatusCode.OK)
                }

                // core protocol - upload chunk
                patch {
                    // make sure we can get a file path first
                    // TODO return 404 if the resource isn't found (whatever that means?)
                    // TODO do we really need to get metadata here?
                    val id = call.requirePathParameter("id")
                    val tusktUploadMetadata =
                        config.tusktStore.getInfo(id) ?: run {
                            call.respond(HttpStatusCode.NotFound)
                            return@patch
                        }

                    val uploaded = handleUploadBytes(config.tusktStore, tusktUploadMetadata)
                    if (uploaded) {
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }

            // core protocol - server information
            options {
                // This could return a 200 or a 204. Electing a 204 here because of the vibe
                call.response.header(TusHeaders.TUS_VERSION, SUPPORTED_TUS_VERSIONS.joinToString(","))
                call.response.header(
                    TusHeaders.TUS_EXTENSION,
                    extensions
                        .flatMap { it.names }
                        .distinct()
                        .joinToString(","),
                )
                call.respond(HttpStatusCode.NoContent)
            }

            extensions.forEach { extension ->
                extension.configure(config)
            }
        }
    }
}

internal val TusResumableVersionCheck: ApplicationPlugin<Unit> =
    createApplicationPlugin(name = "TusResumableVersionCheck") {
        onCall { call ->
            if (call.request.httpMethod != HttpMethod.Options) {
                // don't use the newer requireHeader because we want to handle if the header isn't present here
                // (we send back the versions supported with a different status code)
                val version = call.request.headers[TusHeaders.TUS_RESUMABLE]
                if (version !in SUPPORTED_TUS_VERSIONS) {
                    call.response.header(TusHeaders.TUS_VERSION, SUPPORTED_TUS_VERSIONS.joinToString(","))
                    call.respond(HttpStatusCode.PreconditionFailed)
                }
            }
        }
    }

// make sure we always set the version unless options comes back
internal val TusResponseHeaders: ApplicationPlugin<Unit> =
    createApplicationPlugin(name = "TusResponseHeaders") {
        onCallRespond { call, _ ->
            if (call.request.httpMethod != HttpMethod.Options) {
                call.response.header(TusHeaders.TUS_RESUMABLE, TUS_RESUME_VERSION)
            }
        }
    }

// private suspend fun RoutingContext.getIdOrRespond() =
//    // TODO id hardening (drop path attempts in name, etc)!
//    // TODO it probably makes sense to have a uuid for the id, so we can just check for validity there
//    call.parameters["id"].also {
//        // TODO logging
//        if (it == null) {
//            call.respond(HttpStatusCode.Forbidden)
//        }
//    }

// TODO does it make sense to have any context parameters here?
@Suppress("ReturnCount")
internal suspend fun RoutingContext.handleUploadBytes(
    tusktStore: TusktStore,
    tusktUploadMetadata: TusktUploadMetadata,
    creating: Boolean = false,
): Boolean {
    if (
        call.request.headers[HttpHeaders.ContentType]
        != ContentType.Application.OffsetOctetStream.toString()
    ) {
        // TODO response body?
        call.respond(HttpStatusCode.UnsupportedMediaType)
        return false
    }

    val offset =
        if (creating) {
            0
        } else {
            // if we are not just creating a new upload we need to make sure we get the offset from the upload offset
            call.requireHeader(TusHeaders.UPLOAD_OFFSET).toLong().also {
                if (it < 0) {
                    call.respond(HttpStatusCode.BadRequest)
                    return false
                }
            }
        }

    if (tusktUploadMetadata.currentOffset != offset) {
        call.respond(HttpStatusCode.Conflict)
        return false
    }

    // TODO write and also update metadata!

    val newOffset = tusktStore.append(tusktUploadMetadata, call.receiveChannel())

    // write the bytes to the file
//                    filePath
//                        .outputStream(
//                            StandardOpenOption.CREATE,
//                            StandardOpenOption.WRITE,
//                            StandardOpenOption.APPEND,
//                        ).use { output ->
//                            val input = call.receiveChannel()
//                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
//                            while (true) {
//                                val bytesRead = input.readAvailable(buffer, 0, buffer.size)
//                                if (bytesRead == -1) break
//                                output.write(buffer, 0, bytesRead)
//                            }
//                        }

    // TODO header for new content size!
    call.response.header(TusHeaders.UPLOAD_OFFSET, newOffset)

    return true
}
