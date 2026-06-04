package nart.simpleanki.core.apkg

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Minimal read-only SQLite query seam so reader mapping logic is unit-testable. */
interface AnkiSqlite {
    /** Returns each row as a column-name → value map. */
    fun query(sql: String): List<Map<String, Any?>>
    fun close()
}

/** Thin Android implementation over a SQLite file. Verified via instrumentation/manual, not JVM tests. */
class AndroidAnkiSqlite(private val db: SQLiteDatabase) : AnkiSqlite {
    override fun query(sql: String): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val row = HashMap<String, Any?>(c.columnCount)
                for (i in 0 until c.columnCount) {
                    row[c.getColumnName(i)] = when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        else -> c.getString(i)
                    }
                }
                rows += row
            }
        }
        return rows
    }

    override fun close() = db.close()

    companion object {
        /** Opens a SQLite db file read-only. */
        fun open(file: File): AnkiSqlite =
            AndroidAnkiSqlite(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
    }
}
