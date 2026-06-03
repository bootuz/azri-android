package nart.simpleanki.feature.paywall

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.util.Locale
import nart.simpleanki.R
import nart.simpleanki.core.billing.BillingProducts
import nart.simpleanki.core.billing.PlanOption
import nart.simpleanki.core.billing.PlanPricing
import nart.simpleanki.core.billing.PremiumTier
import nart.simpleanki.core.billing.PurchaseResult
import org.koin.androidx.compose.koinViewModel

// Dark Luxe palette
private val Bg = Color(0xFF0E1020)
private val Ink = Color(0xFFEDEEF7)
private val Muted = Color(0xFF9A9CB5)
private val AccentStart = Color(0xFF7C5CFF)
private val AccentEnd = Color(0xFFFF5BA6)
private val accentBrush = Brush.linearGradient(listOf(AccentStart, AccentEnd))

@Composable
fun PaywallScreen(onClose: () -> Unit, viewModel: PaywallViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? Activity
    // The paywall is always dark, so force light system-bar icons while it's shown (the clock /
    // battery would otherwise be dark-on-navy). Restore the app's setting when it closes.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val prevStatus = controller?.isAppearanceLightStatusBars
        val prevNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.let {
                if (prevStatus != null) it.isAppearanceLightStatusBars = prevStatus
                if (prevNav != null) it.isAppearanceLightNavigationBars = prevNav
            }
        }
    }
    // Close automatically once premium is unlocked (side effect, not during composition).
    LaunchedEffect(state.isPremium, state.result) {
        if (state.isPremium && state.result == PurchaseResult.Success) onClose()
    }
    PaywallContent(
        state = state,
        onClose = onClose,
        onSelect = viewModel::select,
        onPurchase = { activity?.let(viewModel::purchase) },
        onRestore = viewModel::restore,
        onRetry = viewModel::retry,
    )
}

/** Stateless Dark Luxe paywall, always dark regardless of app theme. */
@Composable
fun PaywallContent(
    state: PaywallUiState,
    onClose: () -> Unit = {},
    onSelect: (PremiumTier) -> Unit = {},
    onPurchase: () -> Unit = {},
    onRestore: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            // Background (the Box) bleeds behind the status/nav bars; the content inset-pads so it
            // clears them.
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Muted)
            }
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text("Azri Premium", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("Unlock cloud sync & backup", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            FeatureRow("☁️", "Continuous cloud sync")
            FeatureRow("📱", "Every device, always current")
            FeatureRow("🔒", "Safe, encrypted backup")
            Spacer(Modifier.height(20.dp))

            when {
                state.loading -> {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentStart)
                    }
                }
                state.plansUnavailable -> {
                    PlansUnavailable(onRetry = onRetry)
                }
                else -> {
                    val monthly = state.plans.firstOrNull { it.tier == PremiumTier.Monthly }
                    state.plans.forEach { plan ->
                        PlanRow(
                            plan = plan,
                            selected = plan.tier == state.selected,
                            monthlyMicros = monthly?.priceAmountMicros ?: 0L,
                            onClick = { onSelect(plan.tier) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (!state.plansUnavailable) {
                Button(
                    onClick = onPurchase,
                    enabled = !state.purchasing && state.plans.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = Color.White),
                ) { Text(if (state.purchasing) "Processing…" else "Continue", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onRestore) { Text("Restore purchase", color = Muted) }
            }
            Text(
                "Subscriptions renew automatically until cancelled. Terms · Privacy.",
                color = Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FeatureRow(emoji: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Ink, fontSize = 14.sp)
    }
}

@Composable
private fun PlansUnavailable(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Plans unavailable", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Couldn't reach the store. Make sure you're online and signed in to Google Play, then try again.",
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = Color.White),
        ) { Text("Try again", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PlanRow(plan: PlanOption, selected: Boolean, monthlyMicros: Long, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, AccentStart) else BorderStroke(1.dp, Color(0x22FFFFFF))
    val perMonth = PlanPricing.perMonthMicros(plan.tier, plan.priceAmountMicros)
    val savings = if (plan.tier == PremiumTier.Annual) PlanPricing.annualSavingsPercent(monthlyMicros, plan.priceAmountMicros) else 0
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x267C5CFF) else Color(0x0DFFFFFF))
            .border(border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(planLabel(plan.tier), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(planSubtitle(plan.tier, plan.formattedPrice), color = Muted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (savings > 0) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(accentBrush).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text("BEST VALUE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(3.dp))
            }
            Text(perMonth?.let { "${formatMicros(it)}/mo" } ?: plan.formattedPrice, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun planLabel(t: PremiumTier) = when (t) {
    PremiumTier.Monthly -> "Monthly"; PremiumTier.Annual -> "Annual"
    PremiumTier.Lifetime -> "Lifetime"; PremiumTier.None -> ""
}
private fun planSubtitle(t: PremiumTier, price: String) = when (t) {
    PremiumTier.Annual -> "$price / year"; PremiumTier.Monthly -> "Billed monthly"
    PremiumTier.Lifetime -> "$price · pay once"; PremiumTier.None -> ""
}
private fun formatMicros(micros: Long): String = "$" + String.format(Locale.US, "%.2f", micros / 1_000_000.0)

// --- @Preview ---
// Sample plans for previews (real prices come from Play at runtime).
private val previewPlans = listOf(
    PlanOption(PremiumTier.Annual, BillingProducts.SUBSCRIPTION_ID, BillingProducts.BASE_PLAN_ANNUAL, "tokA", "$19.99", 19_990_000L),
    PlanOption(PremiumTier.Monthly, BillingProducts.SUBSCRIPTION_ID, BillingProducts.BASE_PLAN_MONTHLY, "tokM", "$2.99", 2_990_000L),
    PlanOption(PremiumTier.Lifetime, BillingProducts.LIFETIME_ID, null, null, "$49.99", 49_990_000L),
)

@Preview(name = "Paywall · plans")
@Composable
private fun PaywallPlansPreview() {
    PaywallContent(state = PaywallUiState(plans = previewPlans, selected = PremiumTier.Annual, loading = false))
}

@Preview(name = "Paywall · loading")
@Composable
private fun PaywallLoadingPreview() {
    PaywallContent(state = PaywallUiState(loading = true))
}

@Preview(name = "Paywall · unavailable")
@Composable
private fun PaywallUnavailablePreview() {
    PaywallContent(state = PaywallUiState(loading = false, plansUnavailable = true))
}
