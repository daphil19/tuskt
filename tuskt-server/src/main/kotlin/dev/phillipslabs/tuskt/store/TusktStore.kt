package dev.phillipslabs.tuskt.store

import dev.phillipslabs.tuskt.TusktUploadMetadata
import io.ktor.utils.io.*

// TODO potentially separate interface for metadata
public interface TusktStore {
    public suspend fun create(tusktUploadMetadata: TusktUploadMetadata): String

    public suspend fun getInfo(tusktUploadId: String): TusktUploadMetadata?

    public suspend fun getOffset(tusktUploadId: String): Long?

    // TODO is it always going to be the case that an append updates metadata?
    public suspend fun append(
        tusktUploadMetadata: TusktUploadMetadata,
        bytes: ByteReadChannel,
    ): Long
    // TODO probably delete, others
}
