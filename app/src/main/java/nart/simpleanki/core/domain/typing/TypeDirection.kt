package nart.simpleanki.core.domain.typing

/** Which side the user types in a Type-Practice session (session-only; not persisted). */
enum class TypeDirection {
    /** Prompt the front, type the back (the answer). Default — matches normal review. */
    TypeBack,
    /** Prompt the back, type the front. For decks whose target term sits on the front. */
    TypeFront,
}
