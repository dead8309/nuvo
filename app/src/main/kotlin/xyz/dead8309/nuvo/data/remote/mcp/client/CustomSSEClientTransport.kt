package xyz.dead8309.nuvo.data.remote.mcp.client

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.properties.Delegates
import kotlin.time.Duration

/**
 * Custom SSE client transport due to some issue with how the library's [SseClientTransport] handle
 * the endpoint URL.
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalSerializationApi::class)
class CustomSSEClientTransport(
    private val client: HttpClient,
    private val urlString: String?,
    private val reconnectionTime: Duration? = null,
    private val requestBuilder: HttpRequestBuilder.() -> Unit = {},
) : AbstractTransport() {
   private val McpJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        explicitNulls = false
    }

    private val scope by lazy {
        CoroutineScope(session.coroutineContext + SupervisorJob())
    }

    private val initialized: AtomicBoolean = AtomicBoolean(false)
    private var session: ClientSSESession by Delegates.notNull()
    private val endpoint = CompletableDeferred<String>()

    private var job: Job? = null

    private val baseUrl by lazy {
        session.call.request.url.toString().removeSuffix("/sse")
    }

    override suspend fun start() {
        if (!initialized.compareAndSet(false, true)) {
            error(
                "SSEClientTransport already started! " +
                        "If using Client class, note that connect() calls start() automatically.",
            )
        }

        session = urlString?.let {
            client.sseSession(
                urlString = it,
                reconnectionTime = reconnectionTime,
                block = requestBuilder,
            )
        } ?: client.sseSession(
            reconnectionTime = reconnectionTime,
            block = requestBuilder,
        )

        job = scope.launch(CoroutineName("SseMcpClientTransport.collect#${hashCode()}")) {
            session.incoming.collect { event ->
                when (event.event) {
                    "error" -> {
                        val e = IllegalStateException("SSE error: ${event.data}")
                        _onError(e)
                        throw e
                    }

                    "open" -> {
                        // The connection is open, but we need to wait for the endpoint to be received.
                    }

                    "endpoint" -> {
                        try {
                            val eventData = event.data ?: ""

                            // check url correctness
                            val maybeEndpoint = URLBuilder(baseUrl).takeFrom(eventData)

                            endpoint.complete(maybeEndpoint.toString())
                        } catch (e: Exception) {
                            _onError(e)
                            close()
                            error(e)
                        }
                    }

                    else -> {
                        try {
                            val message = McpJson.decodeFromString<JSONRPCMessage>(event.data ?: "")
                            _onMessage(message)
                        } catch (e: Exception) {
                            _onError(e)
                        }
                    }
                }
            }
        }

        endpoint.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun send(message: JSONRPCMessage) {
        if (!endpoint.isCompleted) {
            error("Not connected")
        }

        try {
            val response = client.post(endpoint.getCompleted()) {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(McpJson.encodeToString(message))
            }

            if (!response.status.isSuccess()) {
                val text = response.bodyAsText()
                error("Error POSTing to endpoint (HTTP ${response.status}): $text")
            }
        } catch (e: Exception) {
            _onError(e)
            throw e
        }
    }

    override suspend fun close() {
        if (!initialized.load()) {
            error("SSEClientTransport is not initialized!")
        }

        session.cancel()
        _onClose()
        job?.cancelAndJoin()
    }
}
