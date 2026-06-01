package nart.simpleanki.auth

/** Platform-agnostic authenticated user, mapped from FirebaseUser. */
data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val isAnonymous: Boolean,
)
