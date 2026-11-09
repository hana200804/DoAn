package com.signa.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.room.Room
import com.signa.app.data.local.database.MIGRATION_1_2
import com.signa.app.data.local.database.SignaDatabase
import com.signa.app.data.local.database.dao.SignDao
import com.signa.app.data.local.database.dao.SessionDao
import com.signa.app.domain.model.SessionSource
import com.signa.app.BuildConfig
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

private val Context.settingsStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SignaDatabase =
        Room.databaseBuilder(context, SignaDatabase::class.java, "signa.db").addMigrations(MIGRATION_1_2).build()

    @Provides fun provideSignDao(database: SignaDatabase): SignDao = database.signs()
    @Provides fun provideSessionDao(database: SignaDatabase): SessionDao = database.sessions()
    @Provides fun provideClock(): Clock = Clock.systemUTC()
    @Provides fun provideSessionSource(): SessionSource = if (BuildConfig.USE_FAKE_BACKEND) SessionSource.FAKE else SessionSource.REMOTE

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsStore
}
