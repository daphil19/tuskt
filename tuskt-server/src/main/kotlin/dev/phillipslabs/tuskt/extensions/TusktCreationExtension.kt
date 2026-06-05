package dev.phillipslabs.tuskt.extensions

import dev.phillipslabs.tuskt.TUS_CREATION_EXTENSION
import dev.phillipslabs.tuskt.TusHeaders
import dev.phillipslabs.tuskt.TusktConfig
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

public class TusktCreationExtension : TusExtension {
    override val names: Set<String> = setOf(TUS_CREATION_EXTENSION)

    context(baseUrl: Route)
    override fun configure(tusktConfig: TusktConfig) {
        baseUrl.post {
            // TODO handle upload defer length
            // make sure Upload-defer-length is correctly set
            val uploadDeferLength = call.request.headers[TusHeaders.UPLOAD_LENGTH]?.toIntOrNull()
            if (uploadDeferLength != null && uploadDeferLength != 1) {
                // invalid upload length
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}
