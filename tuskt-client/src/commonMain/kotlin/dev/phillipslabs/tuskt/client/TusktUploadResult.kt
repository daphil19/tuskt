package dev.phillipslabs.tuskt.client

public sealed interface TusktUploadResult {
    public data class Success(
        val offset: Long,
    ) : TusktUploadResult

    public data class OffsetMismatch(
        val expectedOffset: Long,
    ) : TusktUploadResult

    public data object UnsupportedMediaType : TusktUploadResult

    public data object NotFound : TusktUploadResult

//    public data object UnknownError : TusktUploadResult
}
