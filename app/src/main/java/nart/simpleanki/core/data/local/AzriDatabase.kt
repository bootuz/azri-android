package nart.simpleanki.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import nart.simpleanki.core.data.local.dao.CardDao
import nart.simpleanki.core.data.local.dao.DeckDao
import nart.simpleanki.core.data.local.dao.FolderDao

@Database(
    entities = [CardEntity::class, DeckEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AzriDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun deckDao(): DeckDao
    abstract fun folderDao(): FolderDao
}
