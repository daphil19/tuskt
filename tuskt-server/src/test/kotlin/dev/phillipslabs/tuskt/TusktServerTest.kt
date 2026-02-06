package dev.phillipslabs.tuskt

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
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
            val file = storagePath.resolve(id)
            file.writeText("hello world")

            val response =
                client.head("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertEquals("11", response.headers[TusHeaders.UPLOAD_OFFSET])
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
    fun testPatchExistingFile() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val id = "patch-file"
            val file = storagePath.resolve(id)
            Files.createFile(file)

            val response =
                client.patch("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                    header(TusHeaders.UPLOAD_OFFSET, "0")
                    header(HttpHeaders.ContentType, "application/offset+octet-stream")
                    setBody("hello")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals("5", response.headers[TusHeaders.UPLOAD_OFFSET])
            assertEquals("hello", Files.readString(file))
        }

    @Test
    fun testPatchConflict() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val id = "conflict-file"
            val file = storagePath.resolve(id)
            file.writeText("already has data")

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
            val file = storagePath.resolve(id)
            Files.createFile(file)

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
            assertNotNull(response.headers[HttpHeaders.Location])
        }

    @Test
    @Ignore("Test will fail until we implement the correct extension")
    fun testTermination() =
        testApplication {
            application {
                tusktModule(storagePath = storagePath)
            }
            val id = "terminate-me"
            val file = storagePath.resolve(id)
            Files.createFile(file)

            val response =
                client.delete("/files/$id") {
                    header(TusHeaders.TUS_RESUMABLE, TUS_VERSION)
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertFalse(file.exists())
        }

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
        }
}
