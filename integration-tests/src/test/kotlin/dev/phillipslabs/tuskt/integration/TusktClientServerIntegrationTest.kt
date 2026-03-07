package dev.phillipslabs.tuskt.integration

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import dev.phillipslabs.tuskt.client.TusktClient
import dev.phillipslabs.tuskt.client.TusktUploadResult
import dev.phillipslabs.tuskt.tusktModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TusktClientServerIntegrationTest {
    @Test
    fun testInitializeAgainstRunningServer() =
        withRunningTusktServer { server ->
            runTest {
                withTusClient(server) { client ->
                    assertNotNull(client)
                }
            }
        }

    @Test
    fun testGetUploadOffsetAgainstRunningServer() =
        withRunningTusktServer { server ->
            server.seedUpload(id = "existing-upload", contents = "hello world")

            runTest {
                withTusClient(server) { client ->
                    val uploadOffset = client.getUploadOffset("existing-upload")

                    assertNotNull(uploadOffset)
                    assertEquals(11L, uploadOffset.uploadOffset)
                    assertNull(uploadOffset.uploadLength)
                }
            }
        }

    @Test
    fun testUploadBytesAgainstRunningServer() =
        withRunningTusktServer { server ->
            server.seedUpload(id = "new-upload")

            runTest {
                withTusClient(server) { client ->
                    val result = client.uploadBytes("hello".encodeToByteArray(), id = "new-upload", offset = 0)

                    val success = result as TusktUploadResult.Success
                    assertEquals(5L, success.offset)
                }
            }

            assertEquals("hello", server.readUpload("new-upload"))
        }

    @Test
    fun testMissingUploadReturnsNullAgainstRunningServer() =
        withRunningTusktServer { server ->
            runTest {
                withTusClient(server) { client ->
                    assertNull(client.getUploadOffset("missing-upload"))
                }
            }
        }

    private fun withRunningTusktServer(block: (RunningTusktServer) -> Unit) {
        RunningTusktServer().use(block)
    }

    private suspend fun withTusClient(
        server: RunningTusktServer,
        block: suspend (TusktClient) -> Unit,
    ) {
        val client =
            TusktClient.initialize(
                client =
                    HttpClient(CIO) {
                        expectSuccess = false
                    },
                baseUrl = server.baseUrl,
            )

        try {
            block(client)
        } finally {
            client.close()
        }
    }
}

private class RunningTusktServer : AutoCloseable {
    private val fileSystem: FileSystem = Jimfs.newFileSystem(Configuration.unix())
    private val storagePath: Path = fileSystem.getPath("/files").createDirectories()
    private val engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, host = HOST, port = 0) {
            tusktModule(storagePath = storagePath)
        }.start(wait = false)

    val baseUrl: String =
        runBlocking {
            val connector = engine.engine.resolvedConnectors().single()
            "http://${connector.host}:${connector.port}/files"
        }

    fun seedUpload(
        id: String,
        contents: String = "",
    ) {
        storagePath.resolve(id).writeText(contents)
    }

    fun readUpload(id: String): String = storagePath.resolve(id).readText()

    override fun close() {
        engine.stop(gracePeriodMillis = 0, timeoutMillis = 0)
        fileSystem.close()
    }

    private companion object {
        const val HOST = "127.0.0.1"
    }
}
