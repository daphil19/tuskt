package dev.phillipslabs.tuskt.client

public data class TusUploadOffsetResponse(
    val uploadOffset: Long,
    val uploadLength: Long? = null,
) {
    init {
        require(uploadOffset >= 0) { "Upload offset must be non-negative" }
        require(uploadLength == null || uploadLength >= 0) { "Upload length must be non-negative" }
    }
}
