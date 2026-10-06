package com.vaultt.core.di

import android.content.Context
import androidx.room.Room
import com.vaultt.core.database.VaultDatabase
import com.vaultt.core.database.dao.AlbumDao
import com.vaultt.core.database.dao.VaultObjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaultDatabase {
        return Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault_t_database"
        )
        .fallbackToDestructiveMigration() // Use during development when schema changes
        .build()
    }

    @Provides
    fun provideVaultObjectDao(database: VaultDatabase): VaultObjectDao {
        return database.vaultObjectDao()
    }

    @Provides
    fun provideAlbumDao(database: VaultDatabase): AlbumDao {
        return database.albumDao()
    }
}
