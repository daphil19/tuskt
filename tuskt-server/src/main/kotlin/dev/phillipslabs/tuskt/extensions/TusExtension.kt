package dev.phillipslabs.tuskt.extensions

import io.ktor.server.routing.Route

interface TusExtension {
    public fun Route.install()
}
