package dev.phillipslabs.tuskt

import kotlinx.serialization.Serializable

@Serializable
public data class TusktUploadMetadata(
    val id: String,
    val currentOffset: Long,
    // TODO is there a way that we can break out things that are needed for extensions?
    val expectedUploadLength: Long?,
    val metadata: Map<String, String?> = emptyMap(),
)
