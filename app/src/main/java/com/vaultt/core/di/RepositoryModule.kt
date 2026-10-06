package com.vaultt.core.di

import com.vaultt.data.repository.VaultRepositoryImpl
import com.vaultt.domain.repository.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVaultRepository(
        vaultRepositoryImpl: VaultRepositoryImpl
    ): VaultRepository
}
