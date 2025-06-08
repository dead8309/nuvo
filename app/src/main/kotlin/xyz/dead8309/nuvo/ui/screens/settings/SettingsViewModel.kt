package xyz.dead8309.nuvo.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import xyz.dead8309.nuvo.core.database.entities.McpToolEntity
import xyz.dead8309.nuvo.core.model.AuthStatus
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.remote.mcp.McpToolExecutor
import xyz.dead8309.nuvo.data.remote.mcp.client.McpConnectionManager
import xyz.dead8309.nuvo.data.remote.oauth.OAuthService
import xyz.dead8309.nuvo.data.repository.SettingsRepository
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SettingsViewModel"

private const val OAUTH_TEMP_PREFS_NAME = "oauth_temp_state_prefs" // state -> serverId mapping
private const val KEY_SERVER_ID_PREFIX = "server_id_"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val settingsRepository: SettingsRepository,
    private val appAuthService: AuthorizationService,
    mcpConnectionManager: McpConnectionManager,
    private val mcpToolExecutor: McpToolExecutor
) : AndroidViewModel(application as Application) {

    private val _userMessage = MutableStateFlow<String?>(null)
    private val _openApiKeyInput = MutableStateFlow<String?>(null)
    private val _serverToolsMap = MutableStateFlow<Map<Long, List<McpToolEntity>>>(emptyMap())

    init {
        viewModelScope.launch {
            settingsRepository.getAllMcpServers()
                .collect { servers ->
                    val enabledServers = servers.filter { it.enabled }
                    Log.d(TAG, "Collecting tools for ${enabledServers.size} enabled servers")
                    for (server in enabledServers) {
                        launch {
                            settingsRepository.getToolsForServerSettings(server.id)
                                .collect { tools ->
                                    Log.d(
                                        TAG,
                                        "Received ${tools.size} tools for server ${server.id}: ${tools.map { it.originalToolName }}"
                                    )
                                    val currentMap = _serverToolsMap.value.toMutableMap()
                                    currentMap[server.id] = tools
                                    _serverToolsMap.value = currentMap
                                    Log.d(
                                        TAG,
                                        "Updated server tools map: ${_serverToolsMap.value.map { "${it.key} -> ${it.value.size} tools" }}"
                                    )
                                }
                        }
                    }
                }
        }
    }

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.appSettingsFlow,
        settingsRepository.getAllMcpServers(),
        _openApiKeyInput,
        _userMessage,
        mcpConnectionManager.connectionState
    ) { settings, mcpServers, currentApiKeyInput, message, connectionStates ->
        Quintuple(
            first = settings,
            second = mcpServers,
            third = currentApiKeyInput,
            fourth = message,
            fifth = connectionStates
        )
    }.combine(_serverToolsMap) { quintuple, serverTools ->
        val (settings, mcpServers, currentApiKeyInput, message, connectionStates) = quintuple
        val displayValue = currentApiKeyInput ?: settings.openaiApiKey ?: ""

        if (currentApiKeyInput == null && settings.openaiApiKey != null) {
            _openApiKeyInput.value = settings.openaiApiKey
        }

        SettingsUiState(
            openAiApiKey = displayValue,
            mcpServers = mcpServers,
            userMessage = message,
            connectionStates = connectionStates,
            serverTools = serverTools
        )
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
        initialValue = SettingsUiState()
    )

    // Standard Storage for State -> Server ID Mapping
    private val tempStatePrefs: SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences(
            OAUTH_TEMP_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    private val _authIntentChannel = Channel<Pair<Long, Intent>>(Channel.BUFFERED)
    val authIntentChannel = _authIntentChannel.receiveAsFlow()


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
                val serverId = if (config.id == 0L && savedId > 0) savedId else config.id
                if (config.requiresAuth && config.authStatus == AuthStatus.NOT_CHECKED) {
                    performDiscovery(serverId)
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
            val redirectUri = OAuthService.REDIRECT_URI.toUri()
            settingsRepository.updateAuthStatus(serverId, AuthStatus.REQUIRED_DISCOVERY)
            val detailsResult = settingsRepository.getAuthorizationRequestDetails(serverId)
            detailsResult.fold(
                onSuccess = { details ->
                    Log.d(TAG, "Got AuthRequestDetails for server $serverId")

                    val state = SecureRandom().nextInt(Int.MAX_VALUE).toString()
                    val serviceConfig = AuthorizationServiceConfiguration(
                        details.authorizationEndpointUri,
                        details.tokenEndpointUri,
                        details.registrationEndpointUri,
                    )

                    val authRequestBuilder = AuthorizationRequest.Builder(
                        serviceConfig,
                        details.clientId,
                        ResponseTypeValues.CODE,
                        redirectUri,
                    )
                        .setState(state)
                        .setScope(details.scopes?.joinToString(" "))
                    val authRequest = authRequestBuilder.build()

                    val initialAuthState = AuthState(serviceConfig)
                    try {
                        settingsRepository.saveInitialAuthState(serverId, initialAuthState)
                        Log.d(TAG, "Saved initial AuthState for server $serverId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save initial AuthState for server $serverId", e)
                        settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                        _userMessage.value = "Failed to save initial AuthState"
                        return@launch
                    }

                    // state -> serverId
                    storeTemporaryStateToServerIdMapping(state, serverId)

                    val authIntent = appAuthService.getAuthorizationRequestIntent(authRequest)
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
                        Log.e(TAG, "Failed to send auth intent for server $serverId", e)
                        settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                        retrieveAndClearTemporaryStateToServerIdMapping(state)
                        _userMessage.value = "Failed to start authorization flow"
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get authorization details for server $serverId", error)
                    // status already set to ERROR in repository
                    _userMessage.value =
                        "Failed to get authorization details: ${error.localizedMessage}"
                }
            )
        }
    }


    fun handleAuthorizationResponse(
        receivedServerId: Long?,
        response: AuthorizationResponse?,
        error: AuthorizationException?,
    ) {
        viewModelScope.launch {
            val state = response?.state ?: error?.error

            if (state == null) {
                Log.e(TAG, "Auth callback missing state for server $receivedServerId")
                receivedServerId?.let {
                    settingsRepository.updateAuthStatus(it, AuthStatus.ERROR)
                }
                _userMessage.value = "Auth error: Invalid callback response"
                return@launch
            }

            val serverId = retrieveAndClearTemporaryStateToServerIdMapping(state)

            if (serverId == null) {
                Log.e(TAG, "Could not find server ID mapping for received state: $state")
                _userMessage.value = "Auth error: Unknown callback origin"
                return@launch
            }

            val authState = settingsRepository.getAuthState(serverId)
            if (authState == null) {
                Log.e(TAG, "AuthState missing for server $serverId")
                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                _userMessage.value = "Internal auth state error"
                return@launch
            }

            authState.update(response, error)

            if (error != null) {
                Log.e(
                    TAG,
                    "Auth failed for server $receivedServerId. Error: ${error.error}",
                )
                val finalStatus = when (error.code) {
                    AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code -> AuthStatus.REQUIRED_USER_ACTION
                    AuthorizationException.GeneralErrors.PROGRAM_CANCELED_AUTH_FLOW.code -> AuthStatus.REQUIRED_USER_ACTION
                    AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED.code -> AuthStatus.REQUIRED_USER_ACTION
                    AuthorizationException.GeneralErrors.NETWORK_ERROR.code -> AuthStatus.ERROR
                    AuthorizationException.GeneralErrors.SERVER_ERROR.code -> AuthStatus.ERROR
                    AuthorizationException.TYPE_GENERAL_ERROR -> AuthStatus.ERROR
                    else -> AuthStatus.ERROR
                }
                settingsRepository.updateAuthStatus(serverId, finalStatus)
                settingsRepository.saveInitialAuthState(serverId, authState)
                _userMessage.value = "Auth failed: ${error.errorDescription ?: error.error}"
                return@launch
            }

            if (response?.authorizationCode != null) {
                Log.d(
                    TAG,
                    "Auth callback successful for server $receivedServerId, exchanging code..."
                )
                settingsRepository.updateAuthStatus(serverId, AuthStatus.REQUIRED_TOKEN_EXCHANGE)
                settingsRepository.saveInitialAuthState(serverId, authState)

                try {
                    val tokenRequest = response.createTokenExchangeRequest()

                    appAuthService.performTokenRequest(tokenRequest) { tokenResponse, tokenExError ->
                        viewModelScope.launch {
                            if (tokenExError != null) {
                                Log.e(
                                    TAG,
                                    "Token exchange failed vai AppAuth for server $receivedServerId",
                                    tokenExError
                                )
                                when (tokenExError.code) {
                                    AuthorizationException.TokenRequestErrors.INVALID_GRANT.code,
                                    AuthorizationException.TokenRequestErrors.INVALID_CLIENT.code -> AuthStatus.REQUIRED_USER_ACTION

                                    else -> AuthStatus.ERROR
                                }
                                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                                _userMessage.value = "Token exchange failed: ${tokenExError.error}"
                            } else if (tokenResponse != null) {
                                Log.i(
                                    TAG,
                                    "Token exchange successful (from callback) for server $receivedServerId"
                                )
                                settingsRepository.updateAuthStateWithTokenResponse(
                                    serverId,
                                    tokenResponse
                                )
                                /** NOTE: status set to AUTHORIZED in repository **/
                                _userMessage.value = "Authorization successful"
                                // Refresh this server's tools
                                mcpToolExecutor.refreshToolMapping(listOf(serverId))
                            } else {
                                Log.wtf(
                                    TAG,
                                    "AppAuth token exchange returned null response and null error for server $serverId"
                                )
                                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                                _userMessage.value = "Unknown exchange error"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during token exchange setup for server $serverId", e)
                    settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                    _userMessage.value = "Failed to start token exchange: ${e.localizedMessage}"
                }
            } else {
                Log.e(
                    TAG,
                    "Auth callback for server $serverId - no error, but no auth code in response."
                )
                settingsRepository.updateAuthStatus(serverId, AuthStatus.ERROR)
                // Save state even if no code
                settingsRepository.saveInitialAuthState(serverId, authState)
                _userMessage.value = "Auth flow did not return an authorization code."
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

    private fun storeTemporaryStateToServerIdMapping(state: String, serverId: Long) {
        val key = "$KEY_SERVER_ID_PREFIX$state"
        tempStatePrefs.edit { putLong(key, serverId) }
        Log.d(TAG, "Stored state mapping: $state -> $serverId")
    }

    private fun retrieveAndClearTemporaryStateToServerIdMapping(state: String): Long? {
        val key = "$KEY_SERVER_ID_PREFIX$state"
        val serverId = tempStatePrefs.getLong(key, -1)
        return if (serverId != -1L) {
            tempStatePrefs.edit { remove(key) }
            Log.d(TAG, "Retrieved and cleared state mapping: $state -> $serverId")
            serverId
        } else {
            Log.d(TAG, "No mapping found for state: $state")
            null
        }
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
