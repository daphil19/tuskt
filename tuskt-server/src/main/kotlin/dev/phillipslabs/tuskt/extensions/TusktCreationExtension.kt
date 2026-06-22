package dev.phillipslabs.tuskt.extensions

import dev.phillipslabs.tuskt.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

// TODO add an option that allows for defer length to be enabled or disabled
public class TusktCreationExtension(
    private val deferLength: Boolean = false,
) : TusExtension {
    override val names: Set<String> =
        buildSet {
            add(TUS_CREATION_EXTENSION)
            if (deferLength) {
                add(TUS_CREATION_DEFER_LENGTH_EXTENSION)
            }
        }

    context(baseUrl: Route)
    override fun configure(tusktConfig: TusktConfig) {
        baseUrl.post {
            val uploadLengthRaw = call.request.headers[TusHeaders.UPLOAD_LENGTH]?.toLongOrNull()
            val uploadDeferLength = call.request.headers[TusHeaders.UPLOAD_DEFER_LENGTH]?.toIntOrNull()

            if (uploadLengthRaw == null && uploadDeferLength == null) {
                // one of these MUST be present
                call.respond(HttpStatusCode.BadRequest)
            }

            val uploadLength =
                when {
                    uploadDeferLength != null -> {
                        if (uploadDeferLength != 1) {
                            call.respond(HttpStatusCode.BadRequest)
                        }
                        null
                    }

                    uploadLengthRaw != null -> {
                        uploadLengthRaw
                    }

                    else -> {
                        throw MissingRequestParameterException(
                            "${TusHeaders.UPLOAD_LENGTH} or ${TusHeaders.UPLOAD_DEFER_LENGTH}",
                            "header",
                        )
                    }
                }

            // TODO do we need to worry if both headers are present here?

            // TODO handle this somehow:
            //  If the length was deferred using Upload-Defer-Length: 1,
            //  the Client MUST set the Upload-Length header in the next PATCH request, once the length is known.

            val headerValues =
                call
                    .requireHeader(TusHeaders.UPLOAD_METADATA)
                    .split(",")
                    .map { it.split(" ") }
                    .associate { (key, value) ->
                        // TODO do we need to do anything to keep the metadata safe?
                        // the key SHOULD be ascii-encoded, and the value MUST be base64-encoded
                        key to value.takeUnless { it.isEmpty() }?.let { Base64.decode(it).decodeToString() }
                    }

            val uploadId =
                tusktConfig.tusktStore.create(
                    TusktUploadMetadata(
                        Uuid.random().toHexDashString(),
                        0,
                        uploadLength,
                        headerValues,
                    ),
                )

            // response location is absolute URI given the base url
            // TODO check if a different value is needed
            call.response.header(HttpHeaders.Location, "/${tusktConfig.basePath.trimEnd('/')}/$uploadId")
            call.respond(HttpStatusCode.Created)
        }
    }
}
