package dev.phillipslabs.tuskt

import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.routing.*

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
                // TODO find size of file and return it's position as an offset
            }
        }
    }
}
