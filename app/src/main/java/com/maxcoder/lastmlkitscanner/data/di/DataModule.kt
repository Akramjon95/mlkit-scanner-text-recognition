package com.maxcoder.lastmlkitscanner.data.di

import com.maxcoder.lastmlkitscanner.data.repository.CardScanRepositoryImpl
import com.maxcoder.lastmlkitscanner.domain.repository.CardScanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindCardScanRepository(impl: CardScanRepositoryImpl): CardScanRepository
}