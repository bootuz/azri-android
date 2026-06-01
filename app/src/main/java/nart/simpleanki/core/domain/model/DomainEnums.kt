package nart.simpleanki.core.domain.model

/**
 * Card grade. Int raw values match the iOS FSRS `Rating` (and the `rating` field
 * stored in Firestore review logs), so review history is consistent cross-platform.
 */
enum class Rating(val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4);

    companion object {
        fun fromValue(value: Int): Rating = entries.firstOrNull { it.value == value } ?: Again
    }
}

/** FSRS scheduler state. Int raw values match iOS `fsrs_state` and FSRS `State`. */
enum class CardState(val value: Int) {
    New(0),
    Learning(1),
    Review(2),
    Relearning(3);

    companion object {
        fun fromValue(value: Int?): CardState? = value?.let { v -> entries.firstOrNull { it.value == v } }
    }
}

/** Deprecated deck layout, retained for stored-data back-compat. Wire value = name. */
enum class DeckLayout(val wire: String) {
    FrontToBack("frontToBack"),
    BackToFront("backToFront"),
    All("all");

    companion object {
        fun fromWire(wire: String?): DeckLayout = entries.firstOrNull { it.wire == wire } ?: FrontToBack
    }
}

/** Which card directions to include in a manual Review session. Wire value = name. */
enum class ReviewCardFilter(val wire: String) {
    All("all"),
    OriginalsOnly("originalsOnly"),
    ReversesOnly("reversesOnly");

    companion object {
        fun fromWire(wire: String?): ReviewCardFilter = entries.firstOrNull { it.wire == wire } ?: All
    }
}

/** Deck color option. Wire value = name (note `default`). */
enum class ColorOption(val wire: String) {
    Default("default"),
    Red("red"),
    Green("green"),
    Blue("blue"),
    Yellow("yellow"),
    Purple("purple"),
    Orange("orange"),
    Mint("mint"),
    Indigo("indigo");

    companion object {
        fun fromWire(wire: String?): ColorOption = entries.firstOrNull { it.wire == wire } ?: Default
    }
}
