package dev.phillipslabs.tuskt

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.methodoverride.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

fun Application.tusktModule(basePath: String = "/files") {
    install(XHttpMethodOverride)
    install(TusResumablePlugin)

    routing {
        install(DefaultHeaders) {
            header(TusHeaders.TUS_RESUMABLE, TUS_RESUME_VERSION)
        }

        route(basePath) {
            route("/{id}") {
                // TODO this must include the upload length!!
                head {
                    // spec says we must return this
                    call.response.header(HttpHeaders.CacheControl, "no-store")

                    val filePath = getPathFromId()
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
                    val filePath = getPathFromId()

                    if (filePath.notExists()) {
                        call.respond(HttpStatusCode.NotFound)
                    }

                    @Suppress("MaxLineLength")
                    if (call.request.headers[HttpHeaders.ContentType] != ContentType.Application.OffsetOctetStream.toString()) {
                        // TODO response body?
                        call.respond(HttpStatusCode.UnsupportedMediaType)
                        return@patch
                    }

                    val offset = call.request.headers[TusHeaders.UPLOAD_OFFSET]?.toLongOrNull() ?: TODO("should bail")

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
                // NOTE: Tus-Resumable header is included in all responses by default, per spec
                // TODO other headers can go here as we add support
            }
        }
    }
}

val TusResumablePlugin =
    createApplicationPlugin(name = "TusResumablePlugin") {
        onCall { call ->
            if (call.request.httpMethod != HttpMethod.Options) {
                val version = call.request.headers[TusHeaders.TUS_RESUMABLE]
                if (version == null || version != TUS_VERSION) {
                    // TODO do we need to include the header here? Or will default headers pick it up correctly?
                    call.response.header(TusHeaders.TUS_VERSION, TUS_VERSION)
                    call.respond(HttpStatusCode.PreconditionFailed)
                }
            }
        }
    }

// TODO use kotlinx io path instead?
private val parentDir = Path("files").toRealPath()

private fun RoutingContext.getPathFromId(): Path {
    val filename = getFilename() ?: throw IllegalArgumentException("Filename is required")
    return Path(parentDir.absolutePathString(), filename).toRealPath().takeIf { parentDir.contains(it) }
        ?: throw IllegalArgumentException("Filename $filename is outside of parent directory $parentDir")
}

// TODO this needs to eventually support safely normalizing (e.g avoiding pack-pathing out of the parent dir
private fun RoutingContext.getFilename() = call.parameters["filename"]
