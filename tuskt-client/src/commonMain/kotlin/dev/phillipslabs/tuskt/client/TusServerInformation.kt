package dev.phillipslabs.tuskt.client

public data class TusServerInformation(
    val versions: List<String>,
    val resumableVersion: String,
    val extensions: List<String>,
    val maxSize: Long?,
)
