package nart.simpleanki.ui.theme

import androidx.compose.ui.graphics.Color
import nart.simpleanki.core.domain.model.ColorOption

// Brand tokens mirroring the iOS SA palette (exact values).
val SAPrimary = Color(0xFF8299E6)        // periwinkle (Color.main)
val SAOnPrimary = Color(0xFFFFFFFF)

// Screen background (SABackgroundDark / "bgDark")
val SAScreenLight = Color(0xFFFFFFFF)
val SAScreenDark = Color(0xFF1A1A1A)

// Card / row surface (SABackground / "bg")
val SASurfaceLight = Color(0xFFFFFFFF)
val SASurfaceDark = Color(0xFF333333)

// Elevated surface (SABackgroundLight)
val SASurfaceVariantLight = Color(0xFFF2F2F4)
val SASurfaceVariantDark = Color(0xFF4D4D4D)

// Text
val SAPrimaryTextLight = Color(0xFF262626)
val SAPrimaryTextDark = Color(0xFFF5F5F5)
val SASecondaryTextLight = Color(0xFF666666)
val SASecondaryTextDark = Color(0xFFC2C2C2)

// Soft periwinkle container for selected chips / accents
val SAPrimaryContainerLight = Color(0xFFE6EAFB)
val SAPrimaryContainerDark = Color(0xFF3A3F5C)

// Hairline dividers / outlines (~12% ink)
val SAOutlineLight = Color(0x1F262626)
val SAOutlineDark = Color(0x1FF5F5F5)

/** Deck color palette (matches the AzriUI Deck asset values). */
fun ColorOption.toColor(): Color = when (this) {
    ColorOption.Red -> Color(0xFFA02A2A)
    ColorOption.Green -> Color(0xFF1F8A47)
    ColorOption.Blue -> Color(0xFF1F60B8)
    ColorOption.Yellow -> Color(0xFFB58A1F)
    ColorOption.Purple -> Color(0xFF6E3DA8)
    ColorOption.Orange -> Color(0xFFC25E1D)
    ColorOption.Mint -> Color(0xFF1F8A8C)
    ColorOption.Indigo -> Color(0xFF4051E0)
    ColorOption.Default -> Color(0xFF4B5563)
}
