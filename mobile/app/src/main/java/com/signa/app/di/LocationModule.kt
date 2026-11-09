package com.signa.app.di

import com.signa.app.data.location.FusedLocationRepository
import com.signa.app.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds @Singleton abstract fun bindLocation(implementation: FusedLocationRepository): LocationRepository
}
