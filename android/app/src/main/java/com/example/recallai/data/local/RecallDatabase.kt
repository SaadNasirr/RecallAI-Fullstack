package com.example.recallai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryEntity::class,
        MemoryMediaEntity::class,
        ReminderEntity::class,
        PatientAlarmEntity::class,
        MoodCheckInEntity::class,
        SavedFaceEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(RecallTypeConverters::class)
abstract class RecallDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun patientAlarmDao(): PatientAlarmDao
    abstract fun moodCheckInDao(): MoodCheckInDao
    abstract fun savedFaceDao(): SavedFaceDao

    companion object {
        @Volatile
        private var INSTANCE: RecallDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        datetime INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        source TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        warn10Min INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN preset TEXT")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS patient_alarms (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        repeatMode TEXT NOT NULL,
                        daysOfWeekMask INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        nextTriggerAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        pendingAckSinceMs INTEGER
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS mood_checkins (
                        dayKey TEXT PRIMARY KEY NOT NULL,
                        mood TEXT NOT NULL,
                        loggedAt INTEGER NOT NULL,
                        syncedAt INTEGER
                    )"""
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS saved_faces (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        embeddingJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN repeatMode TEXT NOT NULL DEFAULT 'NONE'"
                )
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN daysOfWeekMask INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Repair rows if a device SQLite build mishandled NOT NULL on ALTER (avoids Room read crashes).
                db.execSQL("UPDATE reminders SET repeatMode = 'NONE' WHERE repeatMode IS NULL OR repeatMode = ''")
                db.execSQL("UPDATE reminders SET daysOfWeekMask = 0 WHERE daysOfWeekMask IS NULL")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                rebuildMemoriesTable(
                    db,
                    """
                    INSERT INTO memories_new (
                        id, serverId, userId, createdAt, title, text, type, mood, tags, isImportant, syncStatus
                    )
                    SELECT
                        id, NULL, userId, createdAt, title, text, type, mood, tags, isImportant, 'LEGACY'
                    FROM memories
                    """.trimIndent()
                )
            }
        }

        /** Fixes v7 devices where ALTER added syncStatus with a SQL DEFAULT Room does not expect. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                rebuildMemoriesTable(
                    db,
                    """
                    INSERT INTO memories_new (
                        id, serverId, userId, createdAt, title, text, type, mood, tags, isImportant, syncStatus
                    )
                    SELECT
                        id, serverId, userId, createdAt, title, text, type, mood, tags, isImportant, syncStatus
                    FROM memories
                    """.trimIndent()
                )
            }
        }

        /**
         * Recreates [memories] to match [MemoryEntity] (no SQL DEFAULT on syncStatus; unique serverId index).
         */
        private fun rebuildMemoriesTable(db: SupportSQLiteDatabase, insertFromMemories: String) {
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memories_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    serverId TEXT,
                    userId TEXT,
                    createdAt INTEGER NOT NULL,
                    title TEXT,
                    text TEXT NOT NULL,
                    type TEXT NOT NULL,
                    mood TEXT,
                    tags TEXT,
                    isImportant INTEGER NOT NULL,
                    syncStatus TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(insertFromMemories)
            db.execSQL("DROP TABLE memories")
            db.execSQL("ALTER TABLE memories_new RENAME TO memories")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_memories_serverId ON memories(serverId)"
            )
            db.execSQL("PRAGMA foreign_keys=ON")
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN clientId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE patient_alarms ADD COLUMN clientId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "UPDATE reminders SET clientId = 'reminder-' || id WHERE clientId = '' OR clientId IS NULL"
                )
                db.execSQL(
                    "UPDATE patient_alarms SET clientId = 'alarm-' || id WHERE clientId = '' OR clientId IS NULL"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_reminders_clientId ON reminders(clientId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_patient_alarms_clientId ON patient_alarms(clientId)"
                )
            }
        }

        fun getInstance(context: Context): RecallDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecallDatabase::class.java,
                    "recallai.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}