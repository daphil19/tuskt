package dev.phillipslabs.tuskt

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import dev.phillipslabs.tuskt.extensions.TusktCreationExtension
import dev.phillipslabs.tuskt.store.FileSystemUploadStore
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.*

// @Ignore
class TusktCreationExtensionTest {
    private lateinit var fs: FileSystem
    private lateinit var storagePath: Path

    @BeforeTest
    fun setup() {
        fs = Jimfs.newFileSystem(Configuration.unix())
        storagePath = fs.getPath("/files").createDirectories()
    }

    @AfterTest
    fun tearDown() {
        fs.close()
    }

    private fun Application.creationModule(
        deferLength: Boolean = false,
        enableUpload: Boolean = false,
    ) {
        tusktModule(
            tusktStore = FileSystemUploadStore(storagePath),
            extensions = listOf(TusktCreationExtension(deferLength, enableUpload)),
        )
    }

    private fun getMetadata(id: String): TusktUploadMetadata {
        val metadataFile = storagePath.resolve("$id.json")
        return Json.decodeFromString(Files.readString(metadataFile))
    }

    private fun extractId(response: HttpResponse): String {
        val location = response.headers[HttpHeaders.Location]
        return location?.substringAfterLast("/") ?: ""
    }

    @Test
    fun testCreationWithUploadLength() =
        testApplication {
            application { creationModule() }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_LENGTH, "100")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val id = extractId(response)
            assertNotEquals("", id)
            assertTrue(storagePath.resolve("$id.bin").exists())
            assertTrue(storagePath.resolve("$id.json").exists())

            val metadata = getMetadata(id)
            assertEquals(100L, metadata.expectedUploadLength)
        }

    @Test
    fun testCreationWithZeroLength() =
        testApplication {
            application { creationModule() }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_LENGTH, "0")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val id = extractId(response)
            assertNotEquals("", id)

            val metadata = getMetadata(id)
            assertEquals(0L, metadata.expectedUploadLength)
        }

    @Test
    fun testCreationMissingLengthHeaders() =
        testApplication {
            application { creationModule() }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun testCreationMissingTusResumable() =
        testApplication {
            application { creationModule() }
            val response =
                client.post("/files") {
                    header(TusHeaders.UPLOAD_LENGTH, "100")
                }
            assertEquals(HttpStatusCode.PreconditionFailed, response.status)
        }

    @Test
    fun testCreationWithDeferLength() =
        testApplication {
            application { creationModule(deferLength = true) }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_DEFER_LENGTH, "1")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val id = extractId(response)
            assertNotEquals("", id)

            val metadata = getMetadata(id)
            assertNull(metadata.expectedUploadLength)
        }

    @Test
    fun testCreationWithInvalidDeferLength() =
        testApplication {
            application { creationModule(deferLength = true) }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_DEFER_LENGTH, "0")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    @Ignore("Implementation gap: HEAD on deferred resource should echo Upload-Defer-Length: 1")
    fun testHeadDeferredResourceEchoesHeader() =
        testApplication {
            application { creationModule(deferLength = true) }
            val createResponse =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_DEFER_LENGTH, "1")
                }
            val id = extractId(createResponse)

            val headResponse =
                client.head("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                }
            assertEquals("1", headResponse.headers[TusHeaders.UPLOAD_DEFER_LENGTH])
        }

    @Test
    fun testCreationWithMetadata() =
        testApplication {
            application { creationModule() }
            // filename: world_domination_plan.pdf (Base64: d29ybGRfZG9taW5hdGlvbl9wbGFuLnBkZg==)
            // is_confidential: (empty value)
            val metadataHeader = "filename d29ybGRfZG9taW5hdGlvbl9wbGFuLnBkZg==,is_confidential"
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_LENGTH, "100")
                    header(TusHeaders.UPLOAD_METADATA, metadataHeader)
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val id = extractId(response)
            val metadata = getMetadata(id)

            assertEquals("world_domination_plan.pdf", metadata.metadata["filename"])
            // Protocol allows key-only metadata. Expecting it to be present in the map.
            assertTrue(metadata.metadata.containsKey("is_confidential"))
        }

    @Test
    fun testCreationWithUpload() =
        testApplication {
            application { creationModule(enableUpload = true) }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_LENGTH, "10")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("hello")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("5", response.headers[TusHeaders.UPLOAD_OFFSET])
            val id = extractId(response)
            assertEquals("hello", Files.readString(storagePath.resolve("$id.bin")))
        }

    @Test
    fun testCreationWithUploadPartial() =
        testApplication {
            application { creationModule(enableUpload = true) }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_LENGTH, "100")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("partial chunk")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val expectedOffset = "partial chunk".length.toString()
            assertEquals(expectedOffset, response.headers[TusHeaders.UPLOAD_OFFSET])
        }

    @Test
    fun testCreationWithUploadDisabled() =
        testApplication {
            application { creationModule(enableUpload = false) }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_LENGTH, "10")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("hello")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            assertNull(response.headers[TusHeaders.UPLOAD_OFFSET])
            val id = extractId(response)
            assertEquals("", Files.readString(storagePath.resolve("$id.bin")))
        }

    @Test
    @Ignore("Implementation gap: handleUploadBytes might respond with 415 and then 201 is attempted")
    fun testCreationWithUploadWrongContentType() =
        testApplication {
            application { creationModule(enableUpload = true) }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_LENGTH, "10")
                    header(HttpHeaders.ContentType, "text/plain")
                    setBody("hello")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("0", response.headers[TusHeaders.UPLOAD_OFFSET])
            val id = extractId(response)
            assertEquals("", Files.readString(storagePath.resolve("$id.bin")))
        }

    @Test
    fun testOptionsAdvertisesCreation() =
        testApplication {
            application { creationModule(deferLength = false, enableUpload = false) }
            val response = client.options("/files")
            val extensions = response.headers[TusHeaders.TUS_EXTENSION]?.split(",") ?: emptyList()
            assertTrue(extensions.contains(TUS_CREATION_EXTENSION))
            assertFalse(extensions.contains(TUS_CREATION_DEFER_LENGTH_EXTENSION))
            assertFalse(extensions.contains(TUS_CREATION_WITH_UPLOAD_EXTENSION))
        }

    @Test
    fun testOptionsAdvertisesDeferLength() =
        testApplication {
            application { creationModule(deferLength = true, enableUpload = false) }
            val response = client.options("/files")
            val extensions = response.headers[TusHeaders.TUS_EXTENSION]?.split(",") ?: emptyList()
            assertTrue(extensions.contains(TUS_CREATION_EXTENSION))
            assertTrue(extensions.contains(TUS_CREATION_DEFER_LENGTH_EXTENSION))
            assertFalse(extensions.contains(TUS_CREATION_WITH_UPLOAD_EXTENSION))
        }

    @Test
    fun testOptionsAdvertisesCreationWithUpload() =
        testApplication {
            application { creationModule(deferLength = false, enableUpload = true) }
            val response = client.options("/files")
            val extensions = response.headers[TusHeaders.TUS_EXTENSION]?.split(",") ?: emptyList()
            assertTrue(extensions.contains(TUS_CREATION_EXTENSION))
            assertFalse(extensions.contains(TUS_CREATION_DEFER_LENGTH_EXTENSION))
            assertTrue(extensions.contains(TUS_CREATION_WITH_UPLOAD_EXTENSION))
        }
}
