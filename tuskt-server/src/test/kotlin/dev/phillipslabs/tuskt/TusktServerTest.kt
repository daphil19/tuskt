package dev.phillipslabs.tuskt

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import dev.phillipslabs.tuskt.store.FileSystemUploadStore
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.*

class TusktServerTest {
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

    private fun createUpload(
        id: String,
        offset: Long = 0,
        length: Long? = null,
//        metadata: Map<String, String> = emptyMap(),
    ) {
        val metadataObj = TusktUploadMetadata(id, offset, length)
        storagePath.resolve("$id.json").writeText(Json.encodeToString(metadataObj))
        storagePath.resolve("$id.bin").writeText("") // Create empty data file
    }

    @Test
    fun testOptions() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val response = client.options("/files")
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertNotNull(response.headers[TusHeaders.TUS_VERSION])
            assertEquals(SUPPORTED_TUS_VERSIONS.joinToString(","), response.headers[TusHeaders.TUS_VERSION])
        }

    @Test
    fun testOptionsOmitsTusResumableHeader() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val response = client.options("/files")

            assertNull(response.headers[TusHeaders.TUS_RESUMABLE])
        }

    @Test
    fun testMissingTusResumableHeader() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val response = client.head("/files/some-id")
            assertEquals(HttpStatusCode.PreconditionFailed, response.status)
            assertEquals(SUPPORTED_TUS_VERSIONS.joinToString(","), response.headers[TusHeaders.TUS_VERSION])
        }

    @Test
    fun testUnsupportedVersion() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val response =
                client.head("/files/some-id") {
                    header(TusHeaders.TUS_RESUMABLE, "2.0.0")
                }
            assertEquals(HttpStatusCode.PreconditionFailed, response.status)
            assertEquals(SUPPORTED_TUS_VERSIONS.joinToString(","), response.headers[TusHeaders.TUS_VERSION])
        }

    @Test
    fun testHeadExistingFile() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val id = "test-file"
            createUpload(id, offset = 11)
            storagePath.resolve("$id.bin").writeText("hello world")

            val response =
                client.head("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals("11", response.headers[TusHeaders.UPLOAD_OFFSET])
            assertEquals(SUPPORTED_TUS_VERSIONS.first(), response.headers[TusHeaders.TUS_RESUMABLE])
        }

    @Test
    fun testHeadNonExistentFile() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val response =
                client.head("/files/non-existent") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                }
            // Protocol says: If the resource is not found, the Server SHOULD return either the 404 Not Found, 410 Gone,
            // or 403 Forbidden status without the Upload-Offset header.
            assertTrue(
                response.status == HttpStatusCode.NotFound ||
                    response.status == HttpStatusCode.Gone ||
                    response.status == HttpStatusCode.Forbidden,
            )
            assertNull(response.headers[TusHeaders.UPLOAD_OFFSET])
        }

    @Test
    @Ignore("Test will fail until we improve id hardening")
    fun testHeadInvalidFileIdReturnsForbidden() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val response =
                client.head("/files/..") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertNull(response.headers[TusHeaders.UPLOAD_OFFSET])
        }

    @Test
    fun testPatchExistingFile() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val id = "patch-file"
            createUpload(id)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_OFFSET, "0")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("hello")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals("5", response.headers[TusHeaders.UPLOAD_OFFSET])
            assertEquals(SUPPORTED_TUS_VERSIONS.first(), response.headers[TusHeaders.TUS_RESUMABLE])
            assertEquals("hello", Files.readString(storagePath.resolve("$id.bin")))
        }

    @Test
    fun testPatchConflict() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val id = "conflict-file"
            createUpload(id, offset = 16)
            storagePath.resolve("$id.bin").writeText("already has data")

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_OFFSET, "0") // Incorrect offset
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("new data")
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
        }

    @Test
    fun testPatchInvalidContentType() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val id = "invalid-ct-file"
            createUpload(id)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_OFFSET, "0")
                    header(HttpHeaders.ContentType, "application/octet-stream") // Missing offset+
                    setBody("data")
                }

            assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        }

    @Test
    fun testPatchMissingUploadOffsetReturnsBadRequest() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val id = "missing-offset-file"
            createUpload(id)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("data")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("", Files.readString(storagePath.resolve("$id.bin")))
        }

    @Test
    fun testPatchNegativeUploadOffsetReturnsBadRequest() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val id = "negative-offset-file"
            createUpload(id)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_OFFSET, "-1")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("data")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("", Files.readString(storagePath.resolve("$id.bin")))
        }

    @Test
    @Ignore("Test will fail until we improve id hardening")
    fun testPatchInvalidFileIdReturnsForbidden() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }

            val response =
                client.patch("/files/..") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                    header(TusHeaders.UPLOAD_OFFSET, "0")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("data")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    @Ignore("Test will fail until we implement the correct extension")
    fun testTermination() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            val id = "terminate-me"
            createUpload(id)

            val response =
                client.delete("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, SUPPORTED_TUS_VERSIONS.first())
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertFalse(storagePath.resolve("$id.bin").exists())
            assertFalse(storagePath.resolve("$id.json").exists())
        }

    @Test
    fun testMethodOverride() =
        testApplication {
            application {
                tusktModule(tusktStore = FileSystemUploadStore(storagePath))
            }
            // Use POST with X-HTTP-Method-Override: OPTIONS
            // Should behave like OPTIONS
            val response =
                client.post("/files") {
                    header("X-HTTP-Method-Override", "OPTIONS")
                }
            assertTrue(
                response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK,
                "Should return 204 or 200 for OPTIONS",
            )
            assertNotNull(response.headers[TusHeaders.TUS_VERSION])
            assertNull(response.headers[TusHeaders.TUS_RESUMABLE])
        }
}
