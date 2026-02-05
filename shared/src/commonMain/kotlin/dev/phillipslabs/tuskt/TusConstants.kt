package dev.phillipslabs.tuskt

import io.ktor.http.*

const val TUS_VERSION = "1.0.0"
const val TUS_RESUME_VERSION = "1.0.0"

// extension properties require a backing field, but I want to avoid having to re-create the object each time
private val offsetOctetStream = ContentType(ContentType.Application.TYPE, "offset+octet-stream")
val ContentType.Application.OffsetOctetStream: ContentType
    get() = offsetOctetStream
