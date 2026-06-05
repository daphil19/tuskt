package dev.phillipslabs.tuskt.extensions

import dev.phillipslabs.tuskt.TusktConfig
import io.ktor.server.routing.*

public interface TusExtension {
    // TODO probably need to pass something like a config in
    public val names: Set<String>

    context(baseUrl: Route)
    public fun configure(tusktConfig: TusktConfig)
}
