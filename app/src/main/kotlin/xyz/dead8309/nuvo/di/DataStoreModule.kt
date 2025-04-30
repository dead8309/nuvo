package xyz.dead8309.nuvo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import xyz.dead8309.nuvo.core.datastore.AppSettingsSerializer
import xyz.dead8309.nuvo.datastore.proto.AppSettingsProto
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providesPreferenceDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
        settingsSerializer: AppSettingsSerializer
    ): DataStore<AppSettingsProto> = DataStoreFactory.create(
        serializer = settingsSerializer,
        scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        migrations = listOf()
    ) {
        context.dataStoreFile("settings.pb")
    }
}