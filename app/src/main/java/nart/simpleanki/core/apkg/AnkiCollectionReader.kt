package nart.simpleanki.core.apkg

/** Reads note types + notes from an open Anki collection database. */
interface AnkiCollectionReader {
    fun read(db: AnkiSqlite): AnkiCollectionData
}
