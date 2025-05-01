package xyz.dead8309.nuvo.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import xyz.dead8309.nuvo.core.database.dao.McpServerDao
import xyz.dead8309.nuvo.core.datastore.PreferenceDataStore
import xyz.dead8309.nuvo.core.model.AppSettings
import xyz.dead8309.nuvo.core.model.McpServer
import xyz.dead8309.nuvo.data.model.asDomainModel
import xyz.dead8309.nuvo.data.model.asEntity
import xyz.dead8309.nuvo.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val preferenceDataStore: PreferenceDataStore,
    private val mcpServerDao: McpServerDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SettingsRepository {
    override val appSettingsFlow: Flow<AppSettings> =
        preferenceDataStore.appSettingsFlow.distinctUntilChanged()

    override suspend fun setOpenaiAPIKey(apiKey: String?) {
        preferenceDataStore.setOpenaiAPIKey(apiKey)
    }

    override suspend fun getAllMcpServers(): Flow<List<McpServer>> {
        return mcpServerDao.getAllServers()
            .map { entities -> entities.map { it.asDomainModel() } }
            .distinctUntilChanged()
    }

    override suspend fun saveMcpSever(config: McpServer) {
        withContext(ioDispatcher) {
            mcpServerDao.upsertServer(config.asEntity())
        }
    }

    override suspend fun deleteMcpServer(id: Long) {
        withContext(ioDispatcher) {
            mcpServerDao.deleteServer(id)
        }
    }

    override suspend fun setActiveMcpServer(id: Long, enabled: Boolean) {
        withContext(ioDispatcher) {
            mcpServerDao.setServerEnabled(id, enabled)
        }
    }
}