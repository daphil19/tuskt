package dev.phillipslabs.tuskt

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

fun main() {
    println("Hello, tuskt!")
}

fun Application.module() {
    routing {
        install(DefaultHeaders) {
            header(TusHeaders.TUS_RESUMABLE, TUS_RESUME_VERSION)
        }
        // TODO is there a way that we can make this work with a directory that the files should live in?
        //  (safely normalize it to avoid back-pathing out of the parent dir)
        route("/files/{filename}") {
            head {
                call.response.header(TusHeaders.UPLOAD_OFFSET, getPathFromFilename().toFile().length().toString())
                call.respond(HttpStatusCode.OK)
            }

            patch {
                val offset = call.request.headers[TusHeaders.UPLOAD_OFFSET]?.toLongOrNull() ?: 0L
                val bytes = call.receive<ByteArray>()

                // TODO would kotlinx io make sense here?
                java.io.RandomAccessFile(getPathFromFilename().toFile(), "rw").use { file ->
                    file.seek(offset)
                    file.write(bytes)
                    call.response.header(TusHeaders.UPLOAD_OFFSET, file.length().toString())
                }

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

// TODO use kotlinx io path instead?
private val parentDir = Path("files").toRealPath()

private fun RoutingContext.getPathFromFilename(): Path {
    val filename = getFilename() ?: throw IllegalArgumentException("Filename is required")
    return Path(parentDir.absolutePathString(), filename).toRealPath().takeIf { parentDir.contains(it) }
        ?: throw IllegalArgumentException("Filename $filename is outside of parent directory $parentDir")
}

// TODO this needs to eventually support safely normalizing (e.g avoiding pack-pathing out of the parent dir
private fun RoutingContext.getFilename() = call.parameters["filename"]
