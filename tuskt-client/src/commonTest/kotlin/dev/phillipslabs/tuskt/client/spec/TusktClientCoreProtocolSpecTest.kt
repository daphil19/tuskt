package dev.phillipslabs.tuskt.client.spec

import dev.phillipslabs.tuskt.OffsetOctetStream
import dev.phillipslabs.tuskt.TUS_RESUME_VERSION
import dev.phillipslabs.tuskt.TusHeaders
import dev.phillipslabs.tuskt.client.TusServerInformation
import dev.phillipslabs.tuskt.client.TusktClient
import dev.phillipslabs.tuskt.client.TusktException
import dev.phillipslabs.tuskt.client.TusktUploadResult
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TusktClientCoreProtocolSpecTest {
    @Test
    fun testInitializeUsesKnownBaseUrlAndOmitsTusResumableOnOptions() =
        runTest {
            val harness = TusCoreProtocolHarness(listOf(optionsResponse()))

            val tusClient = harness.initializeTusClient()
            tusClient.close()

            val optionsRequest = harness.requests.single()
            assertEquals(HttpMethod.Options, optionsRequest.method)
            assertEquals(EXAMPLE_BASE_URL, optionsRequest.url.toString())
            assertNull(optionsRequest.headers[TusHeaders.TUS_RESUMABLE])
            harness.assertAllResponsesConsumed()
        }

    @Test
    fun testInitializeAcceptsOptions200And204() =
        runTest {
            val successStatuses = listOf(HttpStatusCode.OK, HttpStatusCode.NoContent)

            successStatuses.forEach { status ->
                val harness = TusCoreProtocolHarness(listOf(optionsResponse(status = status)))

                val tusClient = harness.initializeTusClient()
                tusClient.close()

                harness.assertAllResponsesConsumed()
            }
        }

    @Test
    fun testInitializeFailsWhenOptionsResponseIsUnexpected() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        MockTusResponse(
                            status = HttpStatusCode.BadRequest,
                        ),
                    ),
                )

            assertFailsWith<TusktException> {
                harness.initializeTusClient()
            }
        }

    @Test
    fun testInitializeFailsWhenTusVersionHeaderIsMissing() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        MockTusResponse(
                            status = HttpStatusCode.NoContent,
                        ),
                    ),
                )

            assertFailsWith<TusktException> {
                harness.initializeTusClient()
            }
        }

    @Test
    fun testInitializeFailsWhenServerDoesNotSupportClientTusVersion() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(
                            versions = "0.2.2,0.2.1",
                        ),
                    ),
                )

            assertFailsWith<TusktException> {
                harness.initializeTusClient()
            }
        }

    @Test
    fun testGetTusServerInformationParsesAdvertisedCoreHeaders() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        optionsResponse(
                            status = HttpStatusCode.OK,
                            versions = "1.0.0,0.2.2,0.2.1",
                            extensions = "creation,expiration",
                            maxSize = 1073741824,
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                val info: TusServerInformation = tusClient.getTusServerInformation()

                assertEquals(listOf("1.0.0", "0.2.2", "0.2.1"), info.versions)
                assertEquals(listOf("creation", "expiration"), info.extensions)
                assertEquals(1073741824L, info.maxSize)
            }

            val optionsRequests = harness.requests.filter { request -> request.method == HttpMethod.Options }
            assertEquals(2, optionsRequests.size)
            optionsRequests.forEach { request ->
                assertNull(request.headers[TusHeaders.TUS_RESUMABLE])
            }
        }

    @Test
    fun testHeadReturnsUploadOffsetAndOptionalUploadLength() =
        runTest {
            val uploadId = "upload-01"
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.OK,
                            headers =
                                tusHeaders(
                                    TusHeaders.UPLOAD_OFFSET to "0",
                                    TusHeaders.UPLOAD_LENGTH to "11",
                                ),
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                val response = tusClient.getUploadOffset(uploadId)
                assertNotNull(response)
                assertEquals(0L, response.uploadOffset)
                assertEquals(11L, response.uploadLength)
            }

            val headRequest = harness.requests.last()
            assertEquals(HttpMethod.Head, headRequest.method)
            assertEquals(TUS_RESUME_VERSION, headRequest.headers[TusHeaders.TUS_RESUMABLE])
            assertEquals("/files/$uploadId", headRequest.url.encodedPath)
        }

    @Test
    fun testHeadAcceptsNoContentForSuccessfulLookup() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.NoContent,
                            headers = tusHeaders(TusHeaders.UPLOAD_OFFSET to "12"),
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                val response = tusClient.getUploadOffset("upload-02")
                assertNotNull(response)
                assertEquals(12L, response.uploadOffset)
                assertNull(response.uploadLength)
            }
        }

    @Test
    fun testHeadReturnsNullForMissingOrUnavailableUploadResource() =
        runTest {
            val statuses = listOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound, HttpStatusCode.Gone)

            statuses.forEach { status ->
                val harness =
                    TusCoreProtocolHarness(
                        listOf(
                            optionsResponse(),
                            MockTusResponse(status = status),
                        ),
                    )

                withInitializedClient(harness) { tusClient ->
                    assertNull(tusClient.getUploadOffset("missing-upload"))
                }
            }
        }

    @Test
    fun testHeadFailsWhenUploadOffsetHeaderIsMissing() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.OK,
                            headers = tusHeaders(TusHeaders.UPLOAD_LENGTH to "6"),
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                assertFailsWith<TusktException> {
                    tusClient.getUploadOffset("upload-03")
                }
            }
        }

    @Test
    fun testHeadFailsWhenUploadOffsetHeaderIsMalformed() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.OK,
                            headers = tusHeaders(TusHeaders.UPLOAD_OFFSET to "abc"),
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                assertFailsWith<TusktException> {
                    tusClient.getUploadOffset("upload-04")
                }
            }
        }

    @Test
    fun testHeadRejectsNegativeUploadOffsetAndNegativeUploadLength() =
        runTest {
            val offsetHarness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.OK,
                            headers = tusHeaders(TusHeaders.UPLOAD_OFFSET to "-1"),
                        ),
                    ),
                )

            withInitializedClient(offsetHarness) { tusClient ->
                assertFailsWith<IllegalArgumentException> {
                    tusClient.getUploadOffset("upload-05")
                }
            }

            val lengthHarness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.OK,
                            headers =
                                tusHeaders(
                                    TusHeaders.UPLOAD_OFFSET to "0",
                                    TusHeaders.UPLOAD_LENGTH to "-42",
                                ),
                        ),
                    ),
                )

            withInitializedClient(lengthHarness) { tusClient ->
                assertFailsWith<IllegalArgumentException> {
                    tusClient.getUploadOffset("upload-06")
                }
            }
        }

    @Test
    fun testPatchSendsCoreRequiredHeadersAndContentType() =
        runTest {
            val uploadId = "upload-07"
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.NoContent,
                            headers = tusHeaders(TusHeaders.UPLOAD_OFFSET to "5"),
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                val result = tusClient.uploadBytes("hello".encodeToByteArray(), uploadId, offset = 0)
                val success = assertIs<TusktUploadResult.Success>(result)
                assertEquals(5L, success.offset)
            }

            val patchRequest = harness.requests.last()
            assertEquals(HttpMethod.Patch, patchRequest.method)
            assertEquals(TUS_RESUME_VERSION, patchRequest.headers[TusHeaders.TUS_RESUMABLE])
            assertEquals("0", patchRequest.headers[TusHeaders.UPLOAD_OFFSET])
            // MockEngine keeps request content type on OutgoingContent; real engines serialize this to HTTP Content-Type.
            val patchBody = assertIs<OutgoingContent>(patchRequest.body)
            assertEquals(ContentType.Application.OffsetOctetStream, patchBody.contentType)
            assertEquals("/files/$uploadId", patchRequest.url.encodedPath)
        }

    @Test
    fun testPatchSupportsMultipleSequentialChunksInCoreFlow() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.NoContent,
                            headers = tusHeaders(TusHeaders.UPLOAD_OFFSET to "2"),
                        ),
                        MockTusResponse(
                            status = HttpStatusCode.NoContent,
                            headers = tusHeaders(TusHeaders.UPLOAD_OFFSET to "5"),
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                val result = tusClient.uploadBytes("hello".encodeToByteArray(), "upload-08", offset = 0)
                val success = assertIs<TusktUploadResult.Success>(result)
                assertEquals(5L, success.offset)
            }

            val patchRequests = harness.requests.filter { request -> request.method == HttpMethod.Patch }
            assertEquals(2, patchRequests.size)
            assertEquals("0", patchRequests[0].headers[TusHeaders.UPLOAD_OFFSET])
            assertEquals("2", patchRequests[1].headers[TusHeaders.UPLOAD_OFFSET])
        }

    @Test
    fun testPatchReturnsMaxSizeReachedWhenServerAdvertisesLimit() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(maxSize = 5),
                        MockTusResponse(
                            status = HttpStatusCode.NoContent,
                            headers = tusHeaders(TusHeaders.UPLOAD_OFFSET to "5"),
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                val result = tusClient.uploadBytes("hello world".encodeToByteArray(), "upload-09", offset = 0)
                val maxSizeReached = assertIs<TusktUploadResult.MaxSizeReached>(result)
                assertEquals(5L, maxSizeReached.offset)
            }
        }

    @Test
    fun testPatchFailsWhenServerOmitsUploadOffsetInSuccessResponse() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(
                            status = HttpStatusCode.NoContent,
                        ),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                assertFailsWith<TusktException> {
                    tusClient.uploadBytes("data".encodeToByteArray(), "upload-10", offset = 0)
                }
            }
        }

    @Test
    fun testPatchFailsForConflictNotFoundPreconditionAndUnsupportedMediaType() =
        runTest {
            val scenarios =
                listOf(
                    MockTusResponse(status = HttpStatusCode.Conflict),
                    MockTusResponse(status = HttpStatusCode.NotFound),
                    MockTusResponse(
                        status = HttpStatusCode.PreconditionFailed,
                        headers = tusHeaders(TusHeaders.TUS_VERSION to "1.0.0"),
                    ),
                    MockTusResponse(status = HttpStatusCode.UnsupportedMediaType),
                )

            scenarios.forEach { patchResponse ->
                val harness =
                    TusCoreProtocolHarness(
                        listOf(
                            optionsResponse(),
                            patchResponse,
                        ),
                    )

                withInitializedClient(harness) { tusClient ->
                    assertFailsWith<TusktException> {
                        tusClient.uploadBytes("data".encodeToByteArray(), "upload-11", offset = 0)
                    }
                }
            }
        }

    @Test
    fun testPatchFailsForUnexpectedStatusCodeOutsideCoreContract() =
        runTest {
            val harness =
                TusCoreProtocolHarness(
                    listOf(
                        optionsResponse(),
                        MockTusResponse(status = HttpStatusCode.InternalServerError),
                    ),
                )

            withInitializedClient(harness) { tusClient ->
                assertFailsWith<TusktException> {
                    tusClient.uploadBytes("data".encodeToByteArray(), "upload-12", offset = 0)
                }
            }
        }

    @Test
    fun testUploadBytesFailsForEmptyPayloadEdgeCase() =
        runTest {
            val harness = TusCoreProtocolHarness(listOf(optionsResponse()))

            withInitializedClient(harness) { tusClient ->
                assertFailsWith<TusktException> {
                    tusClient.uploadBytes(byteArrayOf(), "upload-13", offset = 0)
                }
            }
        }

    private suspend fun withInitializedClient(
        harness: TusCoreProtocolHarness,
        block: suspend (TusktClient) -> Unit,
    ) {
        val tusClient = harness.initializeTusClient()
        try {
            block(tusClient)
        } finally {
            tusClient.close()
        }
        harness.assertAllResponsesConsumed()
    }
}
