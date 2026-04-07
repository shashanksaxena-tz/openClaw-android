package com.openclaw.android.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class PerChatSettingsDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PerChatSettingsDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.perChatSettingsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveSettings() = runBlocking {
        val settings = PerChatSettingsEntity(
            conversationId = "test-conv-1",
            temperature = 0.8f,
            preferredModelId = "gemini-flash"
        )

        dao.upsertSettings(settings)

        val retrieved = dao.getSettings("test-conv-1")

        assertEquals(settings.temperature, retrieved?.temperature)
        assertEquals("gemini-flash", retrieved?.preferredModelId)
        assertEquals(0.95f, retrieved?.topP)  // default value
    }

    @Test
    fun testDeleteSettings() = runBlocking {
        val settings = PerChatSettingsEntity(
            conversationId = "test-conv-2",
            temperature = 0.9f
        )

        dao.upsertSettings(settings)
        dao.deleteSettings("test-conv-2")

        val retrieved = dao.getSettings("test-conv-2")
        assertNull(retrieved)
    }

    @Test
    fun testUpsertOverwrites() = runBlocking {
        val settings1 = PerChatSettingsEntity(
            conversationId = "test-conv-3",
            temperature = 0.5f
        )
        val settings2 = PerChatSettingsEntity(
            conversationId = "test-conv-3",
            temperature = 0.9f
        )

        dao.upsertSettings(settings1)
        dao.upsertSettings(settings2)

        val retrieved = dao.getSettings("test-conv-3")
        assertEquals(0.9f, retrieved?.temperature)
    }
}
