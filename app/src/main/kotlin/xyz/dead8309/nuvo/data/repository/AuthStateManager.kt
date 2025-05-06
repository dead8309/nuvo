package xyz.dead8309.nuvo.data.repository

import net.openid.appauth.AuthState

interface AuthStateManager {
    suspend fun getAuthState(serverId: Long): AuthState?
    suspend fun saveAuthState(serverId: Long, authState: AuthState)
    suspend fun clearAuthState(serverId: Long)
}