package nart.simpleanki.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import nart.simpleanki.core.data.local.dao.CardDao
import nart.simpleanki.core.data.local.dao.DeckDao
import nart.simpleanki.core.data.local.dao.FolderDao
import nart.simpleanki.core.data.local.dao.ReviewLogDao
import nart.simpleanki.core.data.local.dao.StreakStateDao
import nart.simpleanki.core.data.local.dao.TypingLogDao

@Database(
    entities = [CardEntity::class, DeckEntity::class, FolderEntity::class, ReviewLogEntity::class, StreakStateEntity::class, TypingLogEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AzriDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun deckDao(): DeckDao
    abstract fun folderDao(): FolderDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun streakStateDao(): StreakStateDao
    abstract fun typingLogDao(): TypingLogDao
}

/**
 * v1 -> v2: add the immutable `review_logs` table. Additive (preserves local data) — the column
 * types, nullability, and the two index names below MUST match [ReviewLogEntity] exactly, because
 * Room validates the live schema against the entity on open. Do NOT add a SQL DEFAULT (the entity
 * has no @ColumnInfo defaultValue; a SQL default would be a schema mismatch).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_logs` (" +
                "`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `rating` INTEGER NOT NULL, " +
                "`state` INTEGER, `due` INTEGER, `stability` REAL, `difficulty` REAL, " +
                "`elapsedDays` REAL NOT NULL, `lastElapsedDays` REAL NOT NULL, `scheduledDays` REAL NOT NULL, " +
                "`review` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_cardId` ON `review_logs` (`cardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_review` ON `review_logs` (`review`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `streak_state` (" +
                "`id` TEXT NOT NULL, `freezeTokens` INTEGER NOT NULL, `frozenDays` TEXT NOT NULL, " +
                "`freezesAwardedForRun` INTEGER NOT NULL, `lastReconciledDay` INTEGER NOT NULL, " +
                "`lastRepairDay` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, " +
                "`dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `typing_logs` (" +
                "`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, `deckId` TEXT NOT NULL, " +
                "`correct` INTEGER NOT NULL, `typedText` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_typing_logs_cardId` ON `typing_logs` (`cardId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_typing_logs_deckId` ON `typing_logs` (`deckId`)")
    }
}
