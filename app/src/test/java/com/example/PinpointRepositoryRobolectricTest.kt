package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.GuestPreferencesDataStore
import com.example.data.local.PinpointDatabase
import com.example.data.repository.ActionResult
import com.example.data.repository.PinpointRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PinpointRepositoryRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: PinpointDatabase
    private lateinit var prefs: GuestPreferencesDataStore
    private lateinit var repo: PinpointRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PinpointDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = GuestPreferencesDataStore(context)
        repo = PinpointRepository(db, prefs)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create crowd, chat with mention, update geofence, and delete crowd completely`() = runTest {
        // 1. Create crowd without Google sign-in
        val createRes = repo.createCrowd("Gagan", "Weekend Trip", 17.3850, 78.4867)
        assertTrue(createRes is ActionResult.Success)
        val crowd = (createRes as ActionResult.Success).data
        assertEquals("Weekend Trip", crowd.name)

        // 2. Verify Admin member & system message created
        val members = repo.observeMembers(crowd.crowdId).first()
        assertEquals(1, members.size)
        assertTrue(members.first().isAdmin)

        // 3. Send chat message mentioning @Gagan and verify mention notification
        val chatRes = repo.sendChatMessage(crowd.crowdId, "@Gagan reaching gate 2")
        assertTrue(chatRes is ActionResult.Success)
        val notifs = repo.notifications.first()
        assertTrue(notifs.any { it.type == "MENTION" && it.title.contains("@Gagan") })

        // 4. Update geofence as Admin (2500m) and reject invalid 5m
        val validGf = repo.updateGeofence(crowd.crowdId, 17.3850, 78.4867, 2500)
        assertTrue(validGf is ActionResult.Success)
        val invalidGf = repo.updateGeofence(crowd.crowdId, 17.3850, 78.4867, 5)
        assertTrue(invalidGf is ActionResult.Error)

        // 5. Permanently delete crowd and verify full cleanup
        val delRes = repo.deleteCrowdPermanently(crowd.crowdId)
        assertTrue(delRes is ActionResult.Success)
        assertNull(db.crowdDao().getCrowdById(crowd.crowdId))
        assertTrue(repo.observeMembers(crowd.crowdId).first().isEmpty())
        assertTrue(repo.observeMessages(crowd.crowdId).first().isEmpty())
    }
}
