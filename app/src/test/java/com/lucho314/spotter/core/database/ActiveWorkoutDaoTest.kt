package com.lucho314.spotter.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.database.dao.ActiveWorkoutDao
import com.lucho314.spotter.core.database.entity.ActiveExerciseEntity
import com.lucho314.spotter.core.database.entity.ActiveSessionEntity
import com.lucho314.spotter.core.database.entity.ActiveSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActiveWorkoutDaoTest {

    private lateinit var database: SpotterDatabase
    private lateinit var dao: ActiveWorkoutDao

    private fun session(id: String = "s1", userId: String = "user-1") = ActiveSessionEntity(
        id = id, userId = userId, routineId = "r1", routineName = "Push", dayName = "Lunes",
        startedAtEpochMs = 1000L, weightUnit = "KG", currentExerciseIndex = 0,
        restEndsAtEpochMs = null, restTotalSeconds = null,
    )

    private fun exercise(sessionId: String = "s1", position: Int = 0) = ActiveExerciseEntity(
        sessionId = sessionId, position = position, exerciseId = 42, name = "Bench",
        equipment = "barbell", mediaUrl = null, imageUrl = null, targetSets = 3, targetReps = 10, restSeconds = 90,
    )

    private fun set(activeExerciseId: Long, id: String = "set1") = ActiveSetEntity(
        id = id, activeExerciseId = activeExerciseId, setNumber = 1, weightText = "80", repsText = "10",
        isWarmup = false, completedAtEpochMs = null,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).allowMainThreadQueries().build()
        dao = database.activeWorkoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getByUser returns null when there's no active session`() = runTest {
        assertThat(dao.getByUser("user-1")).isNull()
    }

    @Test
    fun `insertFull inserts the session, its exercises and their sets in one transaction`() = runTest {
        dao.insertFull(session(), listOf(exercise() to listOf(set(activeExerciseId = 0))))

        val loaded = dao.getByUser("user-1")

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.exercises).hasSize(1)
        assertThat(loaded.exercises.single().sets).hasSize(1)
        assertThat(loaded.exercises.single().sets.single().weightText).isEqualTo("80")
    }

    @Test
    fun `observeByUser emits null then the session once inserted`() = runTest {
        assertThat(dao.observeByUser("user-1").first()).isNull()

        dao.insertFull(session(), listOf(exercise() to emptyList()))

        assertThat(dao.observeByUser("user-1").first()?.session?.id).isEqualTo("s1")
    }

    @Test
    fun `deleting the session cascades to its exercises and sets`() = runTest {
        dao.insertFull(session(), listOf(exercise() to listOf(set(activeExerciseId = 0))))

        dao.deleteSession("s1")

        assertThat(dao.getByUser("user-1")).isNull()
    }

    @Test
    fun `nextSetNumber is one past the current max for that exercise`() = runTest {
        dao.insertSession(session())
        val exerciseId = dao.insertExercise(exercise())

        assertThat(dao.nextSetNumber(exerciseId)).isEqualTo(1)

        dao.insertSet(set(activeExerciseId = exerciseId, id = "set1"))
        dao.insertSet(set(activeExerciseId = exerciseId, id = "set2").copy(setNumber = 2))

        assertThat(dao.nextSetNumber(exerciseId)).isEqualTo(3)
    }

    @Test
    fun `insertNextSet assigns sequential set numbers`() = runTest {
        dao.insertSession(session())
        val exerciseId = dao.insertExercise(exercise())

        val first = dao.insertNextSet("set1", exerciseId, "80", "10")
        val second = dao.insertNextSet("set2", exerciseId, "80", "8")

        assertThat(first).isEqualTo(1)
        assertThat(second).isEqualTo(2)
    }

    @Test
    fun `insertNextSet is atomic under concurrent calls (no duplicate set numbers)`() = runTest {
        // A real (not allowMainThreadQueries) in-memory DB so the two calls below actually run on
        // different threads and can race, the way two fast double-taps on "Agregar serie" would.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val concurrentDb = Room.inMemoryDatabaseBuilder(context, SpotterDatabase::class.java).build()
        val concurrentDao = concurrentDb.activeWorkoutDao()
        try {
            concurrentDao.insertSession(session())
            val exerciseId = concurrentDao.insertExercise(exercise())

            val setNumbers = withContext(Dispatchers.IO) {
                coroutineScope {
                    val a = async { concurrentDao.insertNextSet("set1", exerciseId, "80", "10") }
                    val b = async { concurrentDao.insertNextSet("set2", exerciseId, "80", "8") }
                    listOf(a.await(), b.await())
                }
            }

            assertThat(setNumbers.sorted()).containsExactly(1, 2).inOrder()
        } finally {
            concurrentDb.close()
        }
    }

    @Test
    fun `user_id is unique - a second insertSession for the same user aborts, the first stays intact`() = runTest {
        dao.insertFull(session(id = "old-session"), listOf(exercise(sessionId = "old-session") to listOf(set(activeExerciseId = 0))))

        try {
            dao.insertSession(session(id = "new-session"))
            throw AssertionError("Expected the unique index on user_id to reject a second active session")
        } catch (e: SQLiteConstraintException) {
            // Expected: ABORT (not REPLACE) - see ActiveSessionEntity's KDoc.
        }

        val loaded = dao.getByUser("user-1")
        assertThat(loaded?.session?.id).isEqualTo("old-session")
        assertThat(loaded?.exercises).hasSize(1)
        assertThat(loaded?.exercises?.single()?.sets).hasSize(1)
    }

    @Test
    fun `insertFull also aborts (not replaces) when the user already has an active session`() = runTest {
        dao.insertFull(session(id = "old-session"), listOf(exercise(sessionId = "old-session") to listOf(set(activeExerciseId = 0))))

        try {
            dao.insertFull(session(id = "new-session"), listOf(exercise(sessionId = "new-session") to emptyList()))
            throw AssertionError("Expected insertFull to abort on a second active session for the same user")
        } catch (e: SQLiteConstraintException) {
            // Expected.
        }

        val loaded = dao.getByUser("user-1")
        assertThat(loaded?.session?.id).isEqualTo("old-session")
        assertThat(loaded?.exercises).hasSize(1)
    }

    @Test
    fun `startIfAbsent starts the session when the user has none yet`() = runTest {
        val existingId = dao.startIfAbsent("user-1", session(id = "s1"), listOf(exercise() to listOf(set(activeExerciseId = 0))))

        assertThat(existingId).isNull()
        val loaded = dao.getByUser("user-1")
        assertThat(loaded?.session?.id).isEqualTo("s1")
        assertThat(loaded?.exercises?.single()?.sets).hasSize(1)
    }

    @Test
    fun `startIfAbsent reports the existing session id and inserts nothing when one is already active`() = runTest {
        dao.insertFull(session(id = "old-session"), listOf(exercise(sessionId = "old-session") to listOf(set(activeExerciseId = 0))))

        val existingId = dao.startIfAbsent(
            "user-1",
            session(id = "new-session"),
            listOf(exercise(sessionId = "new-session") to emptyList()),
        )

        assertThat(existingId).isEqualTo("old-session")
        val loaded = dao.getByUser("user-1")
        assertThat(loaded?.session?.id).isEqualTo("old-session")
        assertThat(loaded?.exercises).hasSize(1)
    }

    @Test
    fun `replaceActive atomically discards the old session (cascading) and starts the new one`() = runTest {
        dao.insertFull(session(id = "old-session"), listOf(exercise(sessionId = "old-session") to listOf(set(activeExerciseId = 0))))

        dao.replaceActive(
            "old-session",
            session(id = "new-session"),
            listOf(exercise(sessionId = "new-session") to emptyList()),
        )

        val loaded = dao.getByUser("user-1")
        assertThat(loaded?.session?.id).isEqualTo("new-session")
        assertThat(loaded?.exercises).hasSize(1)
        assertThat(loaded?.exercises?.single()?.sets).isEmpty()
    }

    @Test
    fun `replaceActive rejects replacing a session that belongs to a different user`() = runTest {
        dao.insertFull(
            session(id = "old-session", userId = "user-1"),
            listOf(exercise(sessionId = "old-session") to emptyList()),
        )

        try {
            dao.replaceActive(
                "old-session",
                session(id = "new-session", userId = "user-2"),
                listOf(exercise(sessionId = "new-session") to emptyList()),
            )
            throw AssertionError("Expected replaceActive to reject a cross-user replacement")
        } catch (e: IllegalStateException) {
            // Expected - see ActiveWorkoutDao.replaceActive's KDoc.
        }

        // Nothing was touched: the transaction rolled back.
        assertThat(dao.getByUser("user-1")?.session?.id).isEqualTo("old-session")
        assertThat(dao.getByUser("user-2")).isNull()
    }

    @Test
    fun `replaceActive proceeds when the existing session id no longer exists`() = runTest {
        dao.replaceActive(
            "gone-session",
            session(id = "new-session"),
            listOf(exercise(sessionId = "new-session") to emptyList()),
        )

        assertThat(dao.getByUser("user-1")?.session?.id).isEqualTo("new-session")
    }

    @Test
    fun `setCompleted, setCurrentExercise and setRestTimer update in place`() = runTest {
        dao.insertSession(session())
        val exerciseId = dao.insertExercise(exercise())
        dao.insertSet(set(activeExerciseId = exerciseId))

        dao.setCompleted("set1", 5000L)
        dao.setCurrentExercise("s1", 2)
        dao.setRestTimer("s1", 6000L, 90)

        val loaded = dao.getByUser("user-1")!!
        assertThat(loaded.exercises.single().sets.single().completedAtEpochMs).isEqualTo(5000L)
        assertThat(loaded.session.currentExerciseIndex).isEqualTo(2)
        assertThat(loaded.session.restEndsAtEpochMs).isEqualTo(6000L)
        assertThat(loaded.session.restTotalSeconds).isEqualTo(90)
    }
}
