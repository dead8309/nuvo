package xyz.dead8309.nuvo.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.core.model.TokenResponse
import xyz.dead8309.nuvo.data.remote.oauth.AuthorizationService
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import net.openid.appauth.AuthorizationService as AppAuthService

private const val TAG = "SettingsViewModel"

private const val OAUTH_TEMP_SECURE_PREFS_NAME = "oauth_temp_secure_prefs"
private const val OAUTH_TEMP_PREFS_NAME = "oauth_temp_state_prefs" // state -> serverId mapping
private const val KEY_PKCE_VERIFIER_PREFIX = "pkce_verifier_"
private const val KEY_SERVER_ID_PREFIX = "server_id_"
private const val KEY_OAUTH_STATE_PREFIX = "oauth_state_" // Can reuse this if needed

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application as Application) {

    private val _userMessage = MutableStateFlow<String?>(null)

    private val _openApiKeyInput = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.appSettingsFlow,
        settingsRepository.getAllMcpServers(),
        _openApiKeyInput,
        _userMessage
    ) { settings, mcpServers, currentApiKeyInput, message ->
        val displayValue = currentApiKeyInput ?: settings.openaiApiKey ?: ""

        if (currentApiKeyInput == null && settings.openaiApiKey != null) {
            _openApiKeyInput.value = settings.openaiApiKey
        }

        SettingsUiState(
            openAiApiKey = displayValue,
            mcpServers = mcpServers,
            userMessage = message
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            initialValue = SettingsUiState()
        )

    private val appAuthService: AppAuthService = AppAuthService(getApplication())

    private val masterKey = MasterKey.Builder(application)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val secureTempPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            application,
            OAUTH_TEMP_SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // Standard Storage for State -> Server ID Mapping
    private val tempStatePrefs: SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences(
            OAUTH_TEMP_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    fun updateOpenAiApiKey(apiKey: String) {
        _openApiKeyInput.value = apiKey
        val keyToSave = apiKey.trim().ifBlank { null }
        viewModelScope.launch {
            settingsRepository.setOpenaiAPIKey(keyToSave)
            Log.d("SettingsViewModel", "OpenAI API key updated to: $keyToSave")
        }
    }

    fun addOrUpdateMcpServer(config: McpServer) {
        viewModelScope.launch {
            try {
                val savedId = settingsRepository.saveMcpSever(config)
                if (config.id == 0L && savedId > 0) savedId else config.id
                if (config.requiresAuth && config.authStatus == AuthStatus.NOT_CHECKED) {
                    performDiscovery(config.id)
                }
                _userMessage.value = "MCP server saved successfully"
            } catch (e: Exception) {
                Log.e(TAG, "Error saving MCP server: ${e.message}", e)
                _userMessage.value = "Failed to save MCP server"
            }
        }
    }

    fun deleteMcpServer(id: Long) {
        viewModelScope.launch {
            try {
                settingsRepository.deleteMcpServer(id)
                _userMessage.value = "MCP server deleted successfully"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete MCP server $id", e)
                _userMessage.value = "Failed to delete MCP server"
            }
        }
    }

    fun setMcpServerEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setActiveMcpServer(id, enabled)
            if (enabled) {
                val server = settingsRepository.getMcpServer(id)
                if (server?.requiresAuth == true && server.authStatus == AuthStatus.NOT_CHECKED) {
                    performDiscovery(id)
                }
            }
        }
    }

    fun prepareAuthorizationIntent(serverId: Long) {
        viewModelScope.launch {
            val redirectUri = Uri.parse(AuthorizationService.REDIRECT_URI)
            settingsRepository.updateAuthStatus(serverId, AuthStatus.REQUIRED_DISCOVERY)
            val detailsResult = settingsRepository.getAuthorizationRequestDetails(serverId)
            detailsResult.fold(
                onSuccess = { details ->
                    Log.d(TAG, "Got AuthRequestDetails for server $serverId")

//                    val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
//                    val codeChallenge = CodeVerifierUtil.deriveCodeVerifierChallenge(codeVerifier)
                    val state = SecureRandom().nextInt(Int.MAX_VALUE).toString()

                    // Securely store verifier keyed by state, map state to serverId
//                    storeTemporaryPkceState(state, codeVerifier)
                    storeTemporaryStateToServerIdMapping(state, serverId)


                    val authRequestBuilder = AuthorizationRequest.Builder(
                        AuthorizationServiceConfiguration(
                            details.authorizationEndpointUri,
                            details.tokenEndpointUri,
                            details.registrationEndpointUri,
                        ),
                        details.clientId,
                        ResponseTypeValues.CODE,
                        redirectUri,
                    )
//                        .setCodeVerifier(
//                            codeVerifier,
//                            codeChallenge,
//                            CodeVerifierUtil.deriveCodeVerifierChallenge(codeVerifier)
//                        )
                        .setState(state)
                        // Define required scopes - fetch from AS metadata or have a default set
                        .setScope(details.scopes?.joinToString(" "))

                    val authRequest = authRequestBuilder.build()
                    val authIntent = appAuthService.getAuthorizationRequestIntent(authRequest)
//                    authIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        Log.i(
                            TAG,
                            "Sending auth intent via Channel for server $serverId with state $state"
                        )
                        settingsRepository.updateAuthStatus(
                            serverId,
                            AuthStatus.REQUIRED_AWAITING_CALLBACK
                        )
                        _authIntentChannel.send(Pair(serverId, authIntent))
                        _userMessage.value = null
                    } catch (e: Exception) {
                        clearTemporaryStateToServerIdMapping(state)
                    }


//                    try {
//                        Log.i(
//                            TAG,
//                            "Launching AppAuth intent for server $serverId with state $state"
//                        )
//                        settingsRepository.updateAuthStatus(
//                            serverId,
//                            AuthStatus.REQUIRED_AWAITING_CALLBACK
//                        )
//                        getApplication<Application>().startActivity(authIntent)
//                        // Note: User message might be premature here, success depends on user action
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Failed to launch AppAuth intent for server $serverId", e)
//                        settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
//                        clearTemporaryPkceState(state)
//                        clearTemporaryStateToServerIdMapping(state)
//                        _userMessage.value = "Failed to launch authorization flow"
//                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get authorization details for server $serverId", error)
                    // Already done in repo
                    settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                    _userMessage.value = "Failed to get authorization details"
                }
            )
        }
    }

    private val _authIntentChannel = Channel<Pair<Long, Intent>>(Channel.BUFFERED)
    val authIntentChannel = _authIntentChannel.receiveAsFlow()

//    fun handleAuthorizationResponse(
//        serverId: Long,
//        response: AuthorizationResponse?,
//        error: AuthorizationException?,
//        // plumbing for manual parsing
//        manuallyParsedCode: String? = null,
//        manuallyParsedState: String? = null,
//        manuallyParsedError: String? = null,
//        manuallyParsedErrorDesc: String? = null
//    ) {
//        viewModelScope.launch {
//            val receivedState = response?.state ?: error?.error ?: manuallyParsedState
//            val finalError = error ?: if (manuallyParsedError != null) {
//                AuthorizationException(
//                    AuthorizationException.TYPE_OAUTH_AUTHORIZATION_ERROR,
//                    AuthorizationException.fromTemplate(
//                        AuthorizationException.GeneralErrors.SERVER_ERROR,
//                        Exception(manuallyParsedError)
//                    ).code,
//                    manuallyParsedError,
//                    manuallyParsedErrorDesc,
//                    receivedState?.let { Uri.parse("?$it") },
//                    null
//                )
//            } else {
//                null
//            }
//            val finalCode = response?.authorizationCode ?: manuallyParsedCode
//
//            if (receivedState == null) {
//                Log.e(TAG, "Auth callback missing state for server $serverId")
//                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
//                _userMessage.value = "Auth error: Invalid callback response"
//                return@launch
//            }
//
//            // Retrieve and clear the verifier FIRST
//            val codeVerifier = retrieveAndClearTemporaryPkceState(receivedState)
//            // clear state->serverId mapping afterwards
//            clearTemporaryStateToServerIdMapping(receivedState)
//
//            if (finalError != null) {
//                Log.e(
//                    TAG,
//                    "Auth callback error for server $serverId: ${finalError.error} - ${finalError.errorDescription}",
//                    finalError
//                )
//                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
//                _userMessage.value = "Auth error: ${finalError.errorDescription ?: finalError.error}"
//                return@launch
//            }
//
//            if (finalCode != null) {
//                if (codeVerifier == null) {
//                    Log.e(
//                        TAG,
//                        "Auth callback success but PKCE verifier is null for state $receivedState, server $serverId"
//                    )
//                    settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
//                    _userMessage.value = "Internal error: verifier missing"
//                    return@launch
//                }
//
//                Log.d(TAG, "Auth callback successful for server $serverId, exchanging code...")
//                val exchangeResult = settingsRepository.handleAuthorizationCodeExchange(
//                    serverId = serverId,
//                    code = finalCode,
//                    codeVerifier = codeVerifier,
//                    redirectUri = AuthorizationService.REDIRECT_URI,
//                )
//
//                if (exchangeResult.isSuccess) {
//                    Log.i(TAG, "Token exchange successful (from callback) for server $serverId")
//                    // Status updated to AUTHORIZED in repository
//                    _userMessage.value = "Authorization successful"
//                } else {
//                    Log.e(
//                        TAG,
//                        "Token exchange failed (from callback) for server $serverId",
//                        exchangeResult.exceptionOrNull()
//                    )
//                    // Status updated to ERROR in repository
//                    _userMessage.value =
//                        "Token exchange failed: ${exchangeResult.exceptionOrNull()?.localizedMessage}"
//                }
//            } else {
//                Log.e(
//                    TAG,
//                    "Auth callback success but authorization code is missing for server $serverId"
//                )
//                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
//                _userMessage.value = "Auth error: Missing authorization code"
//            }
//        }
//    }

    fun handleAuthorizationResponse(
        serverId: Long,
        response: AuthorizationResponse?,
        error: AuthorizationException?,
    ) {
        viewModelScope.launch {
            val receivedState = response?.state ?: error?.error

            if (receivedState == null) {
                Log.e(TAG, "Auth callback missing state for server $serverId")
                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                _userMessage.value = "Auth error: Invalid callback response"
                return@launch
            }

            // Retrieve and clear the verifier FIRST
//            val codeVerifier = retrieveAndClearTemporaryPkceState(receivedState)
            // clear state->serverId mapping afterwards
            clearTemporaryStateToServerIdMapping(receivedState)

            if (error != null && error.error == null) {
                Log.e(
                    TAG,
                    "Auth callback error for server $serverId: ${error.error} - ${error.errorDescription}",
                )
                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                _userMessage.value = "Auth error: ${error.errorDescription ?: error.error}"
                return@launch
            }

            if (response?.authorizationCode != null) {
//                if (codeVerifier == null) {
//                    Log.e(
//                        TAG,
//                        "Auth callback success but PKCE verifier is null for state $receivedState, server $serverId"
//                    )
//                    settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
//                    _userMessage.value = "Internal error: verifier missing"
//                    return@launch
//                }

                Log.d(TAG, "Auth callback successful for server $serverId, exchanging code...")
                val tokenRequest = response.createTokenExchangeRequest()

                appAuthService.performTokenRequest(tokenRequest) { tokenResponse, tokenExError ->
                    viewModelScope.launch {
                        if (tokenExError != null) {
                            Log.e(
                                TAG,
                                "Token exchange failed vai AppAuth for server $serverId",
                                tokenExError
                            )
                            settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                            _userMessage.value = "Token exchange failed: ${tokenExError.error}"
                        } else if (tokenResponse != null) {
                            Log.i(
                                TAG,
                                "Token exchange successful (from callback) for server $serverId"
                            )
                            val domainTokenResponse = TokenResponse(
                                accessToken = tokenResponse.accessToken
                                    ?: throw Exception("This should not happen"),
                                tokenType = tokenResponse.tokenType
                                    ?: net.openid.appauth.TokenResponse.TOKEN_TYPE_BEARER,
                                expiresIn = tokenResponse.accessTokenExpirationTime?.let {
                                    (it - Clock.System.now().toEpochMilliseconds()) / 1000
                                }?.takeIf { it > 0 },
                                refreshToken = tokenResponse.refreshToken,
                                scope = tokenResponse.scope
                            )
                            settingsRepository.saveOAuthTokens(serverId, domainTokenResponse)
                            _userMessage.value = "Authorization successful"
                        } else {
                            Log.e(
                                TAG,
                                "AppAuth token exchange returned null response and null error for server $serverId"
                            )
                            settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                            _userMessage.value = "Unknown exchange error"
                        }
                    }
                }

//                val exchangeResult = settingsRepository.handleAuthorizationCodeExchange(
//                    serverId = serverId,
//                    code = response.authorizationCode!!,
//                    codeVerifier = codeVerifier,
//                    redirectUri = AuthorizationService.REDIRECT_URI,
//                )
//
//                if (exchangeResult.isSuccess) {
//                    Log.i(TAG, "Token exchange successful (from callback) for server $serverId")
//                    // Status updated to AUTHORIZED in repository
//                    _userMessage.value = "Authorization successful"
//                } else {
//                    Log.e(
//                        TAG,
//                        "Token exchange failed (from callback) for server $serverId",
//                        exchangeResult.exceptionOrNull()
//                    )
//                    // Status updated to ERROR in repository
//                    _userMessage.value =
//                        "Token exchange failed: ${exchangeResult.exceptionOrNull()?.localizedMessage}"
//                }
            } else {
                Log.e(
                    TAG,
                    "Auth callback success but authorization code is missing for server $serverId"
                )
                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                _userMessage.value = "Auth error: Missing authorization code"
            }
        }
    }

    fun checkAndTriggerDiscovery(serverId: Long) {
        viewModelScope.launch {
            val server = settingsRepository.getMcpServer(serverId)
            if (server?.enabled == true && server.authStatus == AuthStatus.NOT_CHECKED) {
                Log.d(TAG, "Triggering initial discovery for server $serverId")
                performDiscovery(serverId)
            }
        }
    }

    private suspend fun performDiscovery(serverId: Long) {
        settingsRepository.performInitialAuthDiscovery(serverId).onFailure { error ->
            Log.e(TAG, "Initial discovery failed for server $serverId", error)
            _userMessage.value = "Discovery failed: ${error.localizedMessage}"
            // Status is set to ERROR within the repository
        }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }


    fun clearOAuthDetails(serverId: Long) {
        viewModelScope.launch {
            try {
                settingsRepository.clearOAuthDetails(serverId)
                _userMessage.value = "MCP server OAuth details cleared"
                // TODO: come back to this
                // maybe not needed
                // performDiscovery(serverId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear OAuth details for server $serverId", e)
                _userMessage.value = "Failed to clear OAuth details"
            }
        }
    }

    private fun storeTemporaryPkceState(state: String, codeVerifier: String) {
        val key = "$KEY_PKCE_VERIFIER_PREFIX$state"
        secureTempPrefs.edit().putString(key, codeVerifier).apply()
        Log.d(TAG, "Stored PKCE verifier for state: $state")
    }

    private fun retrieveAndClearTemporaryPkceState(state: String): String? {
        val key = "$KEY_PKCE_VERIFIER_PREFIX$state"
        val verifier = secureTempPrefs.getString(key, null)
        if (verifier != null) {
            secureTempPrefs.edit().remove(key).apply()
            Log.d(TAG, "Retrieved and cleared PKCE verifier for state: $state")
        } else {
            Log.w(TAG, "PKCE verifier not found for state: $state")
        }
        return verifier
    }

    private fun storeTemporaryStateToServerIdMapping(state: String, serverId: Long) {
        val key = "$KEY_SERVER_ID_PREFIX$state"
        tempStatePrefs.edit().putLong(key, serverId).apply()
        Log.d(TAG, "Stored state mapping: $state -> $serverId")
    }

    private fun clearTemporaryStateToServerIdMapping(state: String) {
        val key = "$KEY_SERVER_ID_PREFIX$state"
        tempStatePrefs.edit().remove(key).apply()
        Log.d(TAG, "Cleared state->serverId mapping for state: $state from ViewModel")
    }


    private fun clearTemporaryPkceState(state: String) {
        val key = "$KEY_PKCE_VERIFIER_PREFIX$state"
        secureTempPrefs.edit().remove(key).apply()
        Log.d(TAG, "Cleared PKCE verifier for state: $state")
    }

    fun updateStatusTo(serverId: Long, authStatus: AuthStatus) {
        viewModelScope.launch {
            settingsRepository.updateAuthStatus(serverId, authStatus)
        }
    }

    override fun onCleared() {
        super.onCleared()
        appAuthService.dispose()
        Log.d(TAG, "AppAuthService disposed")
    }
}