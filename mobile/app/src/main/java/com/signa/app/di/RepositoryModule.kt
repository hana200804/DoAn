package com.signa.app.di

import com.signa.app.data.fake.FakeAuthRepository
import com.signa.app.data.fake.FakeContributionRepository
import com.signa.app.data.fake.FakeSignRepository
import com.signa.app.data.repository.SettingsRepositoryImpl
import com.signa.app.domain.repository.AuthRepository
import com.signa.app.domain.repository.ContributionRepository
import com.signa.app.domain.repository.SettingsRepository
import com.signa.app.domain.repository.SignRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Provider
import com.signa.app.BuildConfig
import com.signa.app.data.local.session.AndroidSessionCipher
import com.signa.app.data.local.session.SessionCipher
import com.signa.app.data.local.session.RoomSessionStore
import com.signa.app.data.local.session.SessionStore
import com.signa.app.data.repository.CachedSignRepository
import com.signa.app.data.repository.RemoteAuthRepository
import dagger.Provides

/** Replace fake bindings here when backend integration is implemented. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSettings(implementation: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSigns(implementation: CachedSignRepository): SignRepository

    @Binds
    @Singleton
    abstract fun bindSessionCipher(implementation: AndroidSessionCipher): SessionCipher

    @Binds
    @Singleton
    abstract fun bindSessionStore(implementation: RoomSessionStore): SessionStore

    @Binds
    @Singleton
    abstract fun bindContributions(implementation: FakeContributionRepository): ContributionRepository

    companion object {
        @Provides
        @Singleton
        fun provideAuth(fake: Provider<FakeAuthRepository>, remote: Provider<RemoteAuthRepository>): AuthRepository =
            if (BuildConfig.USE_FAKE_BACKEND) fake.get() else remote.get()
    }
}
