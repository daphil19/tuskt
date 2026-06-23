package dev.phillipslabs.tuskt

import io.ktor.http.*

public val SUPPORTED_TUS_VERSIONS: List<String> = listOf("1.0.0").distinct()
public const val TUS_RESUME_VERSION: String = "1.0.0"

// Creation extension headers
public const val TUS_CREATION_EXTENSION: String = "creation"
public const val TUS_CREATION_DEFER_LENGTH_EXTENSION: String = "creation-defer-length"

// Creation with upload extension
public const val TUS_CREATION_WITH_UPLOAD_EXTENSION: String = "creation-with-upload"

// extension properties require a backing field, but I want to avoid having to re-create the object each time
private val offsetOctetStream = ContentType(ContentType.Application.TYPE, "offset+octet-stream")
public val ContentType.Application.OffsetOctetStream: ContentType
    get() = offsetOctetStream
