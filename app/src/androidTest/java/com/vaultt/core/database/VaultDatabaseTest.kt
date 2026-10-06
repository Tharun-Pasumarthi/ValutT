package com.vaultt.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultt.core.database.dao.VaultObjectDao
import com.vaultt.core.database.entity.VaultObjectEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultDatabaseTest {

    private lateinit var database: VaultDatabase
    private lateinit var dao: VaultObjectDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.vaultObjectDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndReadVaultObject() = runBlocking {
        val entity = VaultObjectEntity(
            id = "test-id",
            encryptedName = "test-name".toByteArray(),
            type = "PHOTO",
            size = 1024,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            iv = byteArrayOf(1, 2, 3)
        )

        dao.insert(entity)
        val objects = dao.getAllObjects().first()

        assertEquals(1, objects.size)
        assertEquals("test-id", objects[0].id)
    }
}
