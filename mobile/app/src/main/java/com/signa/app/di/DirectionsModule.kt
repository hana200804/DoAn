package com.signa.app.di

import com.signa.app.BuildConfig
import com.signa.app.data.remote.vietmap.VietMapApiFactory
import com.signa.app.data.remote.vietmap.VietMapApiKeys
import com.signa.app.data.remote.vietmap.VietMapDirectionsApi
import com.signa.app.data.repository.VietMapDirectionsRepository
import com.signa.app.domain.repository.DirectionsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

@Module
@InstallIn(SingletonComponent::class)
abstract class DirectionsModule {
    @Binds @Singleton abstract fun bindDirections(implementation: VietMapDirectionsRepository): DirectionsRepository
    companion object {
        @Provides @Singleton fun keys() = VietMapApiKeys(BuildConfig.VIETMAP_SEARCH_API_KEY, BuildConfig.VIETMAP_ROUTE_API_KEY)
        @Provides @Singleton fun api(keys: VietMapApiKeys, json: Json): VietMapDirectionsApi =
            VietMapApiFactory.create("https://maps.vietmap.vn/api/".toHttpUrl(), keys, json)
    }
}
