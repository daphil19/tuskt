package dev.phillipslabs.tuskt.client.spec

import dev.phillipslabs.tuskt.TUS_RESUME_VERSION
import dev.phillipslabs.tuskt.TusHeaders
import dev.phillipslabs.tuskt.client.TusktClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*

internal const val WELL_KNOWN_TUS_BASE_URL = "https://tus.example.org/files"

internal data class MockTusResponse(
    val status: HttpStatusCode,
    val headers: Headers = Headers.Empty,
    val body: String = "",
)

internal class TusCoreProtocolHarness(
    responses: List<MockTusResponse>,
) {
    private val queuedResponses: ArrayDeque<MockTusResponse> = ArrayDeque(responses)

    internal val requests: MutableList<HttpRequestData> = mutableListOf()

    private val engine =
        MockEngine { request ->
            requests += request

            val response =
                if (queuedResponses.isEmpty()) {
                    error("No mocked response available for ${request.method.value} ${request.url}")
                } else {
                    queuedResponses.removeFirst()
                }

            respond(
                content = response.body,
                status = response.status,
                headers = response.headers,
            )
        }

    internal fun createHttpClient(): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
        }

    internal suspend fun initializeTusClient(baseUrl: String = WELL_KNOWN_TUS_BASE_URL): TusktClient =
        TusktClient.initialize(
            client = createHttpClient(),
            baseUrl = baseUrl,
        )

    internal fun assertAllResponsesConsumed() {
        check(queuedResponses.isEmpty()) {
            "Expected all mocked responses to be consumed but ${queuedResponses.size} remain"
        }
    }
}

internal fun tusHeaders(vararg values: Pair<String, String>): Headers =
    HeadersBuilder()
        .apply {
            values.forEach { (name, value) ->
                append(name, value)
            }
        }.build()

internal fun optionsResponse(
    status: HttpStatusCode = HttpStatusCode.NoContent,
    versions: String = TUS_RESUME_VERSION,
    extensions: String? = null,
    maxSize: Long? = null,
): MockTusResponse =
    MockTusResponse(
        status = status,
        headers =
            HeadersBuilder()
                .apply {
                    append(TusHeaders.TUS_VERSION, versions)
                    if (extensions != null) {
                        append(TusHeaders.TUS_EXTENSION, extensions)
                    }
                    if (maxSize != null) {
                        append(TusHeaders.TUS_MAX_SIZE, maxSize.toString())
                    }
                }.build(),
    )
