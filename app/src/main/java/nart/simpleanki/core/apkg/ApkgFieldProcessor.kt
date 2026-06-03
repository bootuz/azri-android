package nart.simpleanki.core.apkg

/**
 * Pure projection of an Anki field's HTML onto Azri's plain-text card.
 * Extracts the first <img src> and first [sound:…], maps each to a saved local
 * filename, removes those tags, strips all remaining HTML, decodes common
 * entities, and trims. LaTeX delimiters are left as literal text (no MathJax on Android).
 */
object ApkgFieldProcessor {
    data class Result(val text: String, val image: String?, val audio: String?)

    private val IMG = Regex("""<img[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val SOUND = Regex("""\[sound:([^]]+)]""")
    private val TAG = Regex("""<[^>]+>""")

    fun process(content: String, mediaMap: Map<String, String>): Result {
        var text = content
        var image: String? = null
        var audio: String? = null

        IMG.find(text)?.let { m ->
            image = mediaMap[m.groupValues[1]]
            text = IMG.replaceFirst(text, "")
        }
        SOUND.find(text)?.let { m ->
            audio = mediaMap[m.groupValues[1]]
            text = SOUND.replaceFirst(text, "")
        }

        text = TAG.replace(text, "")
        text = decodeEntities(text)
        text = text.replace(' ', ' ').trim()
        return Result(text, image, audio)
    }

    private fun decodeEntities(s: String): String =
        s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
}
