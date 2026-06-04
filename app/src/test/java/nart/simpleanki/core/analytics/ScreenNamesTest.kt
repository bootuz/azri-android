package nart.simpleanki.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenNamesTest {
    @Test fun plainRoutePassesThrough() = assertEquals("library", screenName("library"))
    @Test fun stripsSingleArgPlaceholder() = assertEquals("deck", screenName("deck/{deckId}"))
    @Test fun stripsTrailingArgsKeepsBase() = assertEquals("study", screenName("study/{deckId}/{folderId}"))
    @Test fun nullRouteBecomesUnknown() = assertEquals("unknown", screenName(null))
}
