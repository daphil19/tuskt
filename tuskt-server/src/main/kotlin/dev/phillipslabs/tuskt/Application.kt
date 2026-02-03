package dev.phillipslabs.tuskt

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun main() {
    println("Hello, tuskt!")
}

fun Application.module() {
    routing {
        install(DefaultHeaders) {
            header("Tus-Resumable:", "1.0.0")
        }
        // TODO is there a way that we can make this work with a directory that the files should live in?
        //  (safely normalize it to avoid back-pathing out of the parent dir)
        route("/files/{filename}") {
            head {
                call.response.header(TusHeaders.UPLOAD_OFFSET, File(getFilename()).length().toString())
                call.respond(HttpStatusCode.OK)
            }

            patch {
                val filename = getFilename()
                val offset = call.request.headers[TusHeaders.UPLOAD_OFFSET]?.toLongOrNull() ?: 0L
                val bytes = call.receive<ByteArray>()

                // TODO would kotlinx io make sense here?
                java.io.RandomAccessFile(File(filename), "rw").use { file ->
                    file.seek(offset)
                    file.write(bytes)
                }

                call.response.header(TusHeaders.UPLOAD_OFFSET, File(filename).length().toString())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

// TODO this needs to eventually support safely normalizing (e.g avoiding pack-pathing out of the parent dir
private fun RoutingContext.getFilename() = call.parameters["filename"]
