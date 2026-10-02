package com.lucho314.spotter.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `MigrationTestHelper`'s asset-based schema lookup (`AssetManager.open`) never resolves under
 * Robolectric on this AGP/Room combination - the Room Gradle plugin's `schemaDirectory()` only
 * wires schema JSON into *androidTest* assets, not local ("test") unit test assets (see
 * `copyRoomSchemasToAndroidTestAssetsDebugAndroidTest` vs. the absence of an equivalent unit-test
 * task). These tests build v1/v2 databases from the exported schemas' SQL via a raw
 * [SupportSQLiteOpenHelper], then open them with the real [SpotterDatabase] (v4). All automatic
 * migrations are registered on the `@Database` annotation, so no extra wiring is needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpotterDatabaseMigrationTest {

    // Fetched in @Before (not a property initializer): Robolectric only finishes wiring this
    // test's own sandboxed Android environment (including its data directories) right before the
    // test method runs, not yet at instance construction time - grabbing it too early was
    // intermittently returning a context whose `databases/` directory never got created, but only
    // when this test ran as part of the full suite (never in isolation).
    private lateinit var context: Context
    private lateinit var dbName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Short on purpose - see the class-level path-length note above.
        dbName = "mig-${System.nanoTime().toString(36)}.db"
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    // Kept short on purpose: Robolectric names each test's sandboxed temp directory after the full
    // test method name, and a long one can push the resulting path (with `databases/<dbName>`
    // appended) past Windows' 260-char MAX_PATH, making native SQLite fail to open/create the file.
    @Test
    fun `v1 to v3 migration keeps rows and adds indexed garmin_upload`() = runTest {
        createV1Database()

        val database = Room.databaseBuilder(context, SpotterDatabase::class.java, dbName).build()
        try {
            val pending = database.pendingWorkoutDao().getPending("user-1")
            assertThat(pending.map { it.workout.id }).containsExactly("pw-1")

            val cursor = database.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='garmin_upload'")
            assertThat(cursor.count).isEqualTo(1)
            cursor.close()
            val indexes = database.openHelper.readableDatabase.query("PRAGMA index_list('garmin_upload')")
            val names = mutableListOf<String>()
            while (indexes.moveToNext()) names += indexes.getString(indexes.getColumnIndexOrThrow("name"))
            indexes.close()
            assertThat(names).contains("index_garmin_upload_user_id_status_created_at_epoch_ms")
        } finally {
            database.close()
        }
    }

    @Test
    fun `v2 to v3 keeps Garmin rows and index`() = runTest {
        createLegacyDatabase(2)

        val database = Room.databaseBuilder(context, SpotterDatabase::class.java, dbName).build()
        try {
            assertThat(database.garminUploadDao().getPending("user-1").map { it.workoutId })
                .containsExactly("garmin-1")

            val indexes = database.openHelper.readableDatabase.query("PRAGMA index_list('garmin_upload')")
            val names = mutableListOf<String>()
            while (indexes.moveToNext()) names += indexes.getString(indexes.getColumnIndexOrThrow("name"))
            indexes.close()
            assertThat(names).contains("index_garmin_upload_user_id_status_created_at_epoch_ms")
            assertThat(names).doesNotContain("index_garmin_upload_user_id")

            val plan = database.openHelper.readableDatabase.query(
                "EXPLAIN QUERY PLAN SELECT * FROM garmin_upload " +
                    "WHERE user_id = 'user-1' AND status = 'PENDING' ORDER BY created_at_epoch_ms",
            )
            val details = mutableListOf<String>()
            while (plan.moveToNext()) details += plan.getString(plan.getColumnIndexOrThrow("detail"))
            plan.close()
            assertThat(details.joinToString(" ")).contains("index_garmin_upload_user_id_status_created_at_epoch_ms")
        } finally {
            database.close()
        }
    }

    @Test
    fun `v3 to v4 adds exercise note storage`() = runTest {
        createLegacyDatabase(3)

        val database = Room.databaseBuilder(context, SpotterDatabase::class.java, dbName).build()
        try {
            assertThat(database.pendingWorkoutDao().getPending("user-1").single().exerciseNotes).isEmpty()

            val columns = database.openHelper.readableDatabase.query("PRAGMA table_info('active_exercise')")
            val names = mutableListOf<String>()
            while (columns.moveToNext()) names += columns.getString(columns.getColumnIndexOrThrow("name"))
            columns.close()
            assertThat(names).contains("note")

            val table = database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='pending_workout_exercise_note'",
            )
            assertThat(table.count).isEqualTo(1)
            table.close()
        } finally {
            database.close()
        }
    }

    private fun createV1Database() {
        createLegacyDatabase(1)
    }

    private fun createLegacyDatabase(version: Int) {
        // Uses the platform's own directory-creation logic (more reliable under Robolectric than a
        // manual `getDatabasePath(...).parentFile.mkdirs()`) to make sure `databases/` exists
        // before a raw SupportSQLiteOpenHelper tries to open a file in it. The empty db this
        // creates still has `user_version = 0`, so the callback below still runs `onCreate`.
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).close()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V1_SCHEMA_SQL.forEach(db::execSQL)
                        when (version) {
                            2 -> V2_EXTRA_SCHEMA_SQL.forEach(db::execSQL)
                            3 -> V3_EXTRA_SCHEMA_SQL.forEach(db::execSQL)
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        helper.writableDatabase.execSQL(
            "INSERT INTO pending_workout (id, user_id, routine_id, started_at, completed_at, notes, status, attempts, last_error, created_at_epoch_ms) " +
                "VALUES ('pw-1', 'user-1', NULL, '2026-01-15T10:00:00Z', '2026-01-15T11:00:00Z', NULL, 'PENDING', 0, NULL, 1000)",
        )
        if (version >= 2) {
            helper.writableDatabase.execSQL(
                "INSERT INTO garmin_upload (workout_id, user_id, status, attempts, last_error, garmin_activity_id, garmin_upload_id, payload_json, created_at_epoch_ms, updated_at_epoch_ms) " +
                    "VALUES ('garmin-1', 'user-1', 'PENDING', 0, NULL, NULL, NULL, '{}', 1000, 1000)",
            )
        }
        helper.close()
    }

    /** Copied verbatim (createSql / index createSql / setupQueries) from `app/schemas/.../1.json`. */
    private companion object {
        val V1_SCHEMA_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `cached_payload` (`key` TEXT NOT NULL, `user_id` TEXT NOT NULL, `json` TEXT NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `active_session` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `routine_id` TEXT, `routine_name` TEXT NOT NULL, `day_name` TEXT, `started_at_epoch_ms` INTEGER NOT NULL, `weight_unit` TEXT NOT NULL, `current_exercise_index` INTEGER NOT NULL, `rest_ends_at_epoch_ms` INTEGER, `rest_total_seconds` INTEGER, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_active_session_user_id` ON `active_session` (`user_id`)",
            "CREATE TABLE IF NOT EXISTS `active_exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `exercise_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `equipment` TEXT NOT NULL, `media_url` TEXT, `image_url` TEXT, `target_sets` INTEGER NOT NULL, `target_reps` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, FOREIGN KEY(`session_id`) REFERENCES `active_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_active_exercise_session_id` ON `active_exercise` (`session_id`)",
            "CREATE TABLE IF NOT EXISTS `active_set` (`id` TEXT NOT NULL, `active_exercise_id` INTEGER NOT NULL, `set_number` INTEGER NOT NULL, `weight_text` TEXT NOT NULL, `reps_text` TEXT NOT NULL, `is_warmup` INTEGER NOT NULL, `completed_at_epoch_ms` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`active_exercise_id`) REFERENCES `active_exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_active_set_active_exercise_id` ON `active_set` (`active_exercise_id`)",
            "CREATE TABLE IF NOT EXISTS `pending_workout` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `routine_id` TEXT, `started_at` TEXT NOT NULL, `completed_at` TEXT NOT NULL, `notes` TEXT, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `last_error` TEXT, `created_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_pending_workout_user_id` ON `pending_workout` (`user_id`)",
            "CREATE TABLE IF NOT EXISTS `pending_workout_set` (`id` TEXT NOT NULL, `workout_id` TEXT NOT NULL, `exercise_id` INTEGER NOT NULL, `set_number` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, `reps` INTEGER NOT NULL, `is_warmup` INTEGER NOT NULL, `completed_at` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`workout_id`) REFERENCES `pending_workout`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_pending_workout_set_workout_id` ON `pending_workout_set` (`workout_id`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c1d1af8814d96c43152b5b6caf486850')",
        )
        val V2_EXTRA_SCHEMA_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `garmin_upload` (`workout_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `last_error` TEXT, `garmin_activity_id` INTEGER, `garmin_upload_id` INTEGER, `payload_json` TEXT, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`workout_id`))",
            "CREATE INDEX IF NOT EXISTS `index_garmin_upload_user_id` ON `garmin_upload` (`user_id`)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8995cda9cfaf6939c3ab3aaae530b2ff')",
        )

        /** From `app/schemas/.../3.json`: v2's table with the composite lookup index instead. */
        val V3_EXTRA_SCHEMA_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `garmin_upload` (`workout_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `last_error` TEXT, `garmin_activity_id` INTEGER, `garmin_upload_id` INTEGER, `payload_json` TEXT, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`workout_id`))",
            "CREATE INDEX IF NOT EXISTS `index_garmin_upload_user_id_status_created_at_epoch_ms` ON `garmin_upload` (`user_id`, `status`, `created_at_epoch_ms`)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd07fbd0f8098f0f4b8ae4b0a0b3f12da')",
        )
    }
}
