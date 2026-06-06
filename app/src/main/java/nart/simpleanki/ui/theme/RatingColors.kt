package nart.simpleanki.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS-derived spaced-repetition rating colors, shared across study modes so "a correct typed answer"
 * and an "Easy" review read as the same outcome. Single source of truth (previously inline literals
 * in StudyScreen).
 */
object RatingColors {
    val Again = Color(0xFFFF2D55)   // wrong / incorrect
    val Hard = Color(0xFFFF9500)
    val Good = Color(0xFF5856D6)
    val Easy = Color(0xFF00C7BE)    // correct / success
}
