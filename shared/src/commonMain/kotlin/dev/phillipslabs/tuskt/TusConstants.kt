package dev.phillipslabs.tuskt

import io.ktor.http.*

public const val TUS_VERSION: String = "1.0.0"
public const val TUS_RESUME_VERSION: String = "1.0.0"
public const val TUS_EXTENSIONS: String = "creation"

// extension properties require a backing field, but I want to avoid having to re-create the object each time
private val offsetOctetStream = ContentType(ContentType.Application.TYPE, "offset+octet-stream")
public val ContentType.Application.OffsetOctetStream: ContentType
    get() = offsetOctetStream
