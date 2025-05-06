package xyz.dead8309.nuvo.data.remote.oauth

import android.net.Uri
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import xyz.dead8309.nuvo.core.model.AuthorizationServerMetadata
import xyz.dead8309.nuvo.core.model.ClientRegistrationRequest
import xyz.dead8309.nuvo.core.model.ClientRegistrationResponse
import xyz.dead8309.nuvo.di.IoDispatcher
import javax.inject.Inject

private const val TAG = "AuthorizationServiceImpl"

class OAuthServiceImpl @Inject constructor(
    private val httpClient: HttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : OAuthService {
    private suspend inline fun <reified T> safeGet(url: String): Result<T> =
        runCatching {
            withContext(ioDispatcher) {
                // not throwing on non 2xx
                val response = httpClient.get(url) { expectSuccess = false }
                logResponse(response)
                if (response.status.isSuccess()) {
                    response.body<T>()
                } else {
                    throw HttpRequestTimeoutException(
                        url,
                        null,
                    )
                }
            }
        }.onFailure { Log.e(TAG, "GET request to $url failed", it) }

    private suspend inline fun <reified Request, reified Response> safePost(
        url: String,
        requestBody: Request,
        expectedStatus: HttpStatusCode = HttpStatusCode.Created
    ) = runCatching {
        withContext(ioDispatcher) {
            Log.i(TAG, "POST request to $url")
            val response = httpClient.post(url) {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(requestBody)
                expectSuccess = false
            }
            logResponse(response)
            if (response.status == expectedStatus || (expectedStatus == HttpStatusCode.Created && response.status == HttpStatusCode.OK)) {
                response.body<Response>()
            } else {
                throw ResponseException(response, response.bodyAsText())
            }
        }
    }.onFailure { Log.e(TAG, "POST request to $url failed", it) }

    private suspend inline fun <reified T> safeSubmitForm(
        url: String,
        formParams: Parameters
    ): Result<T> = runCatching {
        withContext(ioDispatcher) {
            Log.i(TAG, "POST form to $url")
            Log.d(TAG, "Form Parameters: $formParams")
            val response = httpClient.submitForm(
                url = url,
                formParameters = formParams,
            ) { expectSuccess = false }
            logResponse(response)
            if (response.status.isSuccess()) {
                response.body<T>()
            } else {
                throw ResponseException(response, response.bodyAsText())
            }
        }
    }.onFailure { Log.e(TAG, "Form submission to $url failed", it) }

    private suspend fun logResponse(response: HttpResponse) {
        Log.d(TAG, "Response Status: ${response.status}")
        Log.v(TAG, "Response Headers: ${response.headers}")
        try {
            Log.v(TAG, "Response Body: ${response.bodyAsText()}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not read response body for logging: ${e.message}")
        }
    }

    // TODO: come back to this
    // no mcp sever currently supports this
//    override suspend fun getResourceServerMetadata(resourceUrl: String): Result<ResourceServerMetadata> {
//        val metadataUrl = Uri.parse(resourceUrl)
//            .buildUpon()
//            .path(".well-known/oauth-protected-resource")
//            // As per spec, query shouldn't be part .well-known
//            .clearQuery()
//            .build()
//            .toString()
//        Log.e(TAG, "Fetching Resource Server Metadata from: $metadataUrl")
//        return safeGet(metadataUrl)
//    }

    override suspend fun getAuthorizationServerMetadata(metadataUrl: String): Result<AuthorizationServerMetadata> {
        // AS metadata URL often already includes the .well-known part
        val url = if (!metadataUrl.contains("/.well-known/")) {
            Uri.parse(metadataUrl)
                .buildUpon()
                .path(".well-known/oauth-authorization-server")
                .clearQuery()
                .build()
                .toString()
        } else {
            metadataUrl
        }
        Log.d(TAG, "Fetching Authorization Server Metadata from: $url")
        return safeGet(url)
    }

    override suspend fun registerClient(
        registrationEndpoint: String,
        request: ClientRegistrationRequest
    ): Result<ClientRegistrationResponse> {
        Log.d(TAG, "Registering client at: $registrationEndpoint")
        Log.d(TAG, "Client Registration Request: $request")
        return safePost<ClientRegistrationRequest, ClientRegistrationResponse>(
            url = registrationEndpoint,
            requestBody = request,
            // As per RFC7591
            expectedStatus = HttpStatusCode.Created,
        )
    }
}