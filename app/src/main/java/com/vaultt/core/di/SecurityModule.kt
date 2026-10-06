package com.vaultt.core.di

import android.content.Context
import com.vaultt.core.security.CryptoManager
import com.vaultt.core.security.SecurityManager
import com.vaultt.core.security.VaultPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideCryptoManager(@ApplicationContext context: Context): CryptoManager {
        return CryptoManager(context)
    }

    @Provides
    @Singleton
    fun provideSecurityManager(vaultPrefs: VaultPrefs): SecurityManager {
        return SecurityManager(vaultPrefs)
    }
}
