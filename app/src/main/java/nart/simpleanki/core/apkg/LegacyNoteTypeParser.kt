package nart.simpleanki.core.apkg

import org.json.JSONObject

/** Parses Anki legacy `col.models` JSON into note types. The top-level keys are model ids. */
object LegacyNoteTypeParser {
    fun parse(modelsJson: String): List<AnkiNoteType> {
        val root = runCatching { JSONObject(modelsJson) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<AnkiNoteType>()
        for (key in root.keys()) {
            val model = root.optJSONObject(key) ?: continue
            val id = key.toLongOrNull() ?: continue
            val name = model.optString("name").takeIf { it.isNotEmpty() && it != "null" } ?: continue
            val flds = model.optJSONArray("flds") ?: continue
            val fields = (0 until flds.length()).mapNotNull { i ->
                flds.optJSONObject(i)?.optString("name")?.takeIf { it.isNotEmpty() && it != "null" }
            }
            result += AnkiNoteType(id = id, name = name, fields = fields, sortField = model.optInt("sortf", 0))
        }
        return result
    }
}
