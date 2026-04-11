package dev.phillipslabs.tuskt

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.methodoverride.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

@Suppress("LongMethod")
fun Application.tusktModule(
    basePath: String = "/files",
    storagePath: Path = Path("files").toAbsolutePath(),
) {
    install(XHttpMethodOverride)

    install(TusResumableVersionCheck)
    install(TusResponseHeaders)

    routing {
        route(basePath) {
            route("/{id}") {
                head {
                    // spec says we must return this
                    call.response.header(HttpHeaders.CacheControl, "no-store")

                    val filePath = getPathFromIdOrRespond(storagePath) ?: return@head
                    if (filePath.notExists()) {
                        // TODO response body?
                        call.respond(HttpStatusCode.NotFound)
                        return@head
                    }

                    call.response.header(TusHeaders.UPLOAD_OFFSET, filePath.fileSize())
                    // while the spec says that we SHOULD use a 200 or 204 for successful responses,
                    // the openAPI spec just uses 200
                    call.respond(HttpStatusCode.OK)
                }

                patch {
                    // make sure we can get a file path first
                    // TODO return 404 if the resource isn't found (whatever that means?)
                    val filePath = getPathFromIdOrRespond(storagePath) ?: return@patch

                    if (filePath.notExists()) {
                        call.respond(HttpStatusCode.NotFound)
                        return@patch
                    }

                    @Suppress("MaxLineLength")
                    if (call.request.headers[HttpHeaders.ContentType] != ContentType.Application.OffsetOctetStream.toString()) {
                        // TODO response body?
                        call.respond(HttpStatusCode.UnsupportedMediaType)
                        return@patch
                    }

                    val offset = call.request.headers[TusHeaders.UPLOAD_OFFSET]?.toLongOrNull()
                    if (offset == null || offset < 0) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@patch
                    }

                    if (filePath.fileSize() != offset) {
                        call.respond(HttpStatusCode.Conflict)
                        return@patch
                    }

                    // write the bytes to the file
                    call.receiveStream().use {
                        it.copyTo(
                            filePath.outputStream(
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.APPEND,
                            ),
                        )
                    }

                    // TODO header for new content size!
                    call.response.header(TusHeaders.UPLOAD_OFFSET, filePath.fileSize())

                    call.respond(HttpStatusCode.NoContent)
                }
            }

            options {
                // This could return a 200 or a 204. Electing a 204 here because of the vibe
                call.response.header(TusHeaders.TUS_VERSION, TUS_VERSION)
                // TODO other headers can go here as we add support
//                call.response.header(TusHeaders.TUS_EXTENSION, TUS_EXTENSIONS)
                call.respond(HttpStatusCode.NoContent)
            }

//            post {
//                // make sure Upload-defer-length is correctly set
//                val uploadDeferLength = call.request.headers[TusHeaders.UPLOAD_DEFER_LENGTH]?.toIntOrNull()
//                if (uploadDeferLength != null && uploadDeferLength != 1) {
//                    call.respond(HttpStatusCode.BadRequest)
//                }
//            }
        }
    }
}

val TusResumableVersionCheck =
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

val TusResponseHeaders =
    createApplicationPlugin(name = "TusResponseHeaders") {
        onCallRespond { call, _ ->
            if (call.request.httpMethod != HttpMethod.Options) {
                call.response.header(TusHeaders.TUS_RESUMABLE, TUS_RESUME_VERSION)
            }
        }
    }

private suspend fun RoutingContext.getPathFromIdOrRespond(storagePath: Path): Path? =
    try {
        getPathFromId(storagePath)
    } catch (_: IllegalArgumentException) {
        call.respond(HttpStatusCode.Forbidden)
        null
    }

// TODO we will eventually have to figure out a way to get an id back for a filename
//  (it probably shouldn't be what we use on disk?)
private fun RoutingContext.getPathFromId(storagePath: Path): Path {
    val filename = call.parameters["id"] ?: throw IllegalArgumentException("Filename is required")
    return storagePath.resolve(filename).normalize().takeIf { it.startsWith(storagePath) }
        ?: throw IllegalArgumentException("Filename $filename is outside of storage directory $storagePath")
}
