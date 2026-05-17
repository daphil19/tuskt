package dev.phillipslabs.tuskt

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
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
                tusktModule(storagePath = storagePath)
            }
            val response = client.options("/files")
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertNotNull(response.headers[TusHeaders.TUS_VERSION])
            assertEquals(TUS_VERSION, response.headers[TusHeaders.TUS_VERSION])
        }

    @Test
    fun testOptionsOmitsTusResumableHeader() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val response = client.options("/files")

            assertNull(response.headers[TusHeaders.TUS_RESUMABLE])
        }

    @Test
    fun testMissingTusResumableHeader() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val response = client.head("/files/some-id")
            assertEquals(HttpStatusCode.PreconditionFailed, response.status)
            assertEquals(TUS_VERSION, response.headers[TusHeaders.TUS_VERSION])
        }

    @Test
    fun testUnsupportedVersion() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val response =
                client.head("/files/some-id") {
                    header(TusHeaders.TUS_RESUMABLE, "2.0.0")
                }
            assertEquals(HttpStatusCode.PreconditionFailed, response.status)
            assertEquals(TUS_VERSION, response.headers[TusHeaders.TUS_VERSION])
        }

    @Test
    fun testHeadExistingFile() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val id = "test-file"
            createUpload(id, offset = 11)
            storagePath.resolve("$id.bin").writeText("hello world")

            val response =
                client.head("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals("11", response.headers[TusHeaders.UPLOAD_OFFSET])
            assertEquals(TUS_VERSION, response.headers[TusHeaders.TUS_RESUMABLE])
        }

    @Test
    fun testHeadNonExistentFile() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val response =
                client.head("/files/non-existent") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
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
                tusktModule(storagePath = storagePath)
            }
            val response =
                client.head("/files/..") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertNull(response.headers[TusHeaders.UPLOAD_OFFSET])
        }

    @Test
    fun testPatchExistingFile() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val id = "patch-file"
            createUpload(id)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                    header(TusHeaders.UPLOAD_OFFSET, "0")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("hello")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals("5", response.headers[TusHeaders.UPLOAD_OFFSET])
            assertEquals(TUS_VERSION, response.headers[TusHeaders.TUS_RESUMABLE])
            assertEquals("hello", Files.readString(storagePath.resolve("$id.bin")))
        }

    @Test
    fun testPatchConflict() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val id = "conflict-file"
            createUpload(id, offset = 16)
            storagePath.resolve("$id.bin").writeText("already has data")

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
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
                tusktModule(storagePath = storagePath)
            }
            val id = "invalid-ct-file"
            createUpload(id)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
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
                tusktModule(storagePath = storagePath)
            }
            val id = "missing-offset-file"
            createUpload(id)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
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
                tusktModule(storagePath = storagePath)
            }
            val id = "negative-offset-file"
            createUpload(id)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
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
                tusktModule(storagePath = storagePath)
            }

            val response =
                client.patch("/files/..") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                    header(TusHeaders.UPLOAD_OFFSET, "0")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("data")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    @Ignore("Test will fail until we implement the correct extension")
    fun testCreation() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val response =
                client.post("/files") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                    header(TusHeaders.UPLOAD_LENGTH, "100")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            val location = response.headers[HttpHeaders.Location]
            assertNotNull(location)
            val id = location.substringAfterLast("/")
            assertTrue(storagePath.resolve("$id.bin").exists())
            assertTrue(storagePath.resolve("$id.json").exists())
        }

    @Test
    @Ignore("Test will fail until we implement the correct extension")
    fun testTermination() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val id = "terminate-me"
            createUpload(id)

            val response =
                client.delete("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertFalse(storagePath.resolve("$id.bin").exists())
            assertFalse(storagePath.resolve("$id.json").exists())
        }

//    @Test
//    @Ignore("Test will fail until we implement the correct extension")
//    fun testCreationWithMetadata() =
//        testApplication {
//            application {
//                tusktModule(storagePath = storagePath)
//            }
//            // filename: world_domination_plan.pdf (Base64: d29ybGRfZG9taW5hdGlvbl9wbGFuLnBkZg==)
//            // is_confidential: (empty value)
//            val metadataHeader = "filename d29ybGRfZG9taW5hdGlvbl9wbGFuLnBkZg==,is_confidential"
//            val response =
//                client.post("/files") {
//                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
//                    header(TusHeaders.UPLOAD_LENGTH, "100")
//                    header(TusHeaders.UPLOAD_METADATA, metadataHeader)
//                }
//
//            assertEquals(HttpStatusCode.Created, response.status)
//            val location = response.headers[HttpHeaders.Location]
//            assertNotNull(location)
//            val id = location.substringAfterLast("/")
//
//            val metadataFile = storagePath.resolve("$id.json")
//            val metadataObj = Json.decodeFromString<TusktUploadMetadata>(metadataFile.readText())
//
//            assertEquals("world_domination_plan.pdf", metadataObj.metadata["filename"])
//            assertEquals("", metadataObj.metadata["is_confidential"])
//        }

    @Test
    fun testMethodOverride() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
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
