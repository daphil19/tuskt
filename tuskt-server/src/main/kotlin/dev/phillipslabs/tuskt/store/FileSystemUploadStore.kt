package dev.phillipslabs.tuskt.store

import dev.phillipslabs.tuskt.TusktUploadMetadata
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

public class FileSystemUploadStore(
    private val storagePath: Path = Path("files").toAbsolutePath(),
) : TusktStore {
    // TODO should json live somewhere else?
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(tusktUploadMetadata: TusktUploadMetadata): String {
        TODO("Not yet implemented")
    }

    override suspend fun getInfo(tusktUploadId: String): TusktUploadMetadata? =
        withContext(Dispatchers.IO) {
            getMetadataPath(tusktUploadId).takeIf { it.exists() }?.let {
                json.decodeFromString(it.readText())
            }
        }

    override suspend fun getOffset(tusktUploadId: String): Long? = getInfo(tusktUploadId)?.currentOffset

    // TODO we need to be able to update metadata!
    override suspend fun append(
        tusktUploadMetadata: TusktUploadMetadata,
        bytes: ByteReadChannel,
    ): Long {
        val appendedBytes =
            withContext(Dispatchers.IO) {
                getUploadPath(tusktUploadMetadata.id)
                    .outputStream(
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND,
                    ).use { output ->
                        bytes.copyTo(output.asByteWriteChannel())
                    }
            }

        // TODO update metadata!
        val newMetadata = tusktUploadMetadata.copy(currentOffset = tusktUploadMetadata.currentOffset + appendedBytes)

        withContext(Dispatchers.IO) {
            getMetadataPath(tusktUploadMetadata.id).writeText(json.encodeToString(newMetadata))
        }

        return newMetadata.currentOffset
    }

    private fun getUploadPath(tusktUploadId: String): Path = storagePath / "$tusktUploadId.bin"

    private fun getMetadataPath(tusktUploadId: String): Path = storagePath / "$tusktUploadId.json"
}
