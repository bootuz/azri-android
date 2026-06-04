package nart.simpleanki.core.apkg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkgFieldProcessorTest {
    private val media = mapOf("pic.jpg" to "local-1.jpg", "snd.mp3" to "local-1.mp3")

    @Test fun stripsHtml_andTrims() {
        val r = ApkgFieldProcessor.process("<b>Hello</b>&nbsp;world ", media)
        assertEquals("Hello world", r.text)
        assertNull(r.image)
        assertNull(r.audio)
    }

    @Test fun extractsFirstImage_mapsToLocalName_andRemovesTag() {
        val r = ApkgFieldProcessor.process("see <img src=\"pic.jpg\"> here", media)
        assertEquals("see  here".trim(), r.text.trim())
        assertEquals("local-1.jpg", r.image)
    }

    @Test fun extractsSound_mapsToLocalName_andRemovesTag() {
        val r = ApkgFieldProcessor.process("listen [sound:snd.mp3]", media)
        assertEquals("listen", r.text)
        assertEquals("local-1.mp3", r.audio)
    }

    @Test fun unmappedMedia_yieldsNullName_butStillStripsTag() {
        val r = ApkgFieldProcessor.process("x <img src=\"missing.png\"> y", emptyMap())
        assertNull(r.image)
        assertEquals("x  y".trim(), r.text.trim())
    }

    @Test fun decodesCommonEntities() {
        assertEquals("a & b < c > d", ApkgFieldProcessor.process("a &amp; b &lt; c &gt; d", media).text)
    }

    @Test fun extractsImage_withAttributesBeforeSrc() {
        val r = ApkgFieldProcessor.process("""see <img style="height:150px" src="pic.jpg"> here""", media)
        assertEquals("local-1.jpg", r.image)
        assertEquals("see  here".trim(), r.text.trim())
    }

    @Test fun extractsImage_singleQuotedSrc() {
        val r = ApkgFieldProcessor.process("see <img src='pic.jpg'> here", media)
        assertEquals("local-1.jpg", r.image)
    }

    @Test fun decodesQuoteAndApostropheEntities() {
        assertEquals("\"a\" 'b'", ApkgFieldProcessor.process("&quot;a&quot; &#39;b&#39;", media).text)
    }
}
