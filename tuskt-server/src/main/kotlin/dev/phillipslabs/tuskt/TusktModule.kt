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

@Suppress("LongMethod")
public fun Application.tusktModule(
    basePath: String = "/files",
    tusktStore: TusktStore = FileSystemUploadStore(),
    extensions: List<TusExtension> = emptyList(),
) {
    install(XHttpMethodOverride)

    install(TusResumableVersionCheck)
    install(TusResponseHeaders)

    routing {
        route(basePath) {
            route("/{id}") {
                // core protocol - upload status/offset
                head {
                    // spec says we must return this
                    call.response.header(HttpHeaders.CacheControl, "no-store")

                    // this throws a bad request if id isn't found, but if id isn't found we shouldn't ever get here?
                    val id = call.requirePathParameter("id")
                    val tusktUploadMetadata =
                        tusktStore.getInfo(id) ?: run {
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
                        tusktStore.getInfo(id) ?: run {
                            call.respond(HttpStatusCode.NotFound)
                            return@patch
                        }

                    if (call.request.headers[HttpHeaders.ContentType] != ContentType.Application.OffsetOctetStream.toString()) {
                        // TODO response body?
                        call.respond(HttpStatusCode.UnsupportedMediaType)
                        return@patch
                    }

                    val offset = call.requireHeader(TusHeaders.UPLOAD_OFFSET).toLong()
                    if (offset < 0) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@patch
                    }

                    if (tusktUploadMetadata.currentOffset != offset) {
                        call.respond(HttpStatusCode.Conflict)
                        return@patch
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

                    call.respond(HttpStatusCode.NoContent)
                }
            }

            // core protocol - server information
            options {
                // This could return a 200 or a 204. Electing a 204 here because of the vibe
                call.response.header(TusHeaders.TUS_VERSION, TUS_VERSION)
                call.response.header(TusHeaders.TUS_EXTENSION, extensions.flatMap { it.names }.distinct().joinToString(","))
                call.respond(HttpStatusCode.NoContent)
            }

            // TODO this should probably be passed in instead of constructed here?
            val config =
                TusktConfig().apply {
                    this.basePath = basePath
                    this.tusktStore = tusktStore
                }

            extensions.forEach { extension ->
                extension.configure(config)
            }
        }
    }
}

public val TusResumableVersionCheck: ApplicationPlugin<Unit> =
    createApplicationPlugin(name = "TusResumableVersionCheck") {
        onCall { call ->
            if (call.request.httpMethod != HttpMethod.Options) {
                val version = call.request.headers[TusHeaders.TUS_RESUMABLE]
                if (version == null || version != TUS_VERSION) {
                    call.response.header(TusHeaders.TUS_VERSION, TUS_VERSION)
                    call.respond(HttpStatusCode.PreconditionFailed)
                }
            }
        }
    }

public val TusResponseHeaders: ApplicationPlugin<Unit> =
    createApplicationPlugin(name = "TusResponseHeaders") {
        onCallRespond { call, _ ->
            if (call.request.httpMethod != HttpMethod.Options) {
                call.response.header(TusHeaders.TUS_RESUMABLE, TUS_RESUME_VERSION)
            }
        }
    }

private suspend fun RoutingContext.getIdOrRespond() =
    // TODO id hardening (drop path attempts in name, etc)!
    // TODO it probably makes sense to have a uuid for the id, so we can just check for validity there
    call.parameters["id"].also {
        // TODO logging
        if (it == null) {
            call.respond(HttpStatusCode.Forbidden)
        }
    }
