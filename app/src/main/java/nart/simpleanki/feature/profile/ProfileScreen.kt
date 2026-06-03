package nart.simpleanki.feature.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.billing.PurchaseResult
import nart.simpleanki.core.data.settings.ThemeMode
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import nart.simpleanki.ui.theme.AzriTheme
import org.koin.androidx.compose.koinViewModel

private const val PLAY_URL = "https://play.google.com/store/apps/details?id=nart.simpleanki"
private const val SUPPORT_EMAIL = "mailto:astemirboziy@gmail.com"
private const val REDDIT_URL = "https://www.reddit.com/r/AzriApp"
private const val TERMS_URL = "https://azri.app/terms"
private const val PRIVACY_URL = "https://azri.app/privacy"

@Composable
fun ProfileScreen(
    onOpenFsrsSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPaywall: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    fun openUrl(url: String) =
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    ProfileContent(
        state = state,
        onOpenFsrsSettings = onOpenFsrsSettings,
        onOpenNotifications = onOpenNotifications,
        onOpenPaywall = onOpenPaywall,
        onRestorePurchases = {
            viewModel.restorePurchases { result ->
                val msg = if (result == PurchaseResult.Success) "Purchases restored" else "No purchases to restore"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        onThemeChange = viewModel::setThemeMode,
        onSignOut = viewModel::signOut,
        onDeleteAccount = viewModel::deleteAccount,
        onRate = { openUrl(PLAY_URL) },
        onSupport = { openUrl(SUPPORT_EMAIL) },
        onShare = {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Study smarter with Azri Flashcards: $PLAY_URL")
            runCatching { context.startActivity(Intent.createChooser(send, "Share Azri")) }
        },
        onReddit = { openUrl(REDDIT_URL) },
        onTerms = { openUrl(TERMS_URL) },
        onPrivacy = { openUrl(PRIVACY_URL) },
    )
}

/** Stateless profile UI, decoupled from the ViewModel for testing. Link actions are callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    state: ProfileUiState,
    onOpenFsrsSettings: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onRate: () -> Unit = {},
    onSupport: () -> Unit = {},
    onShare: () -> Unit = {},
    onReddit: () -> Unit = {},
    onTerms: () -> Unit = {},
    onPrivacy: () -> Unit = {},
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete account",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { showMenu = false; showDeleteDialog = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            CategoryHeader("Account")
            ListItem(
                headlineContent = { Text("Email") },
                supportingContent = {
                    Text(state.email?.takeIf { it.isNotBlank() } ?: "Not signed in")
                },
                leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            )
            ListItem(
                headlineContent = { Text("Cloud sync") },
                supportingContent = {
                    Text(
                        when {
                            state.isPremium && !state.isAnonymous -> "Synced"
                            state.isPremium && state.isAnonymous -> "Sign in to start syncing"
                            else -> "Off — tap to sync your cards"
                        }
                    )
                },
                leadingContent = {
                    Icon(
                        if (state.isPremium && !state.isAnonymous) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = if (state.isPremium) Modifier else Modifier.clickable(onClick = onOpenPaywall),
            )
            ListItem(
                headlineContent = { Text("Restore purchases") },
                leadingContent = { Icon(Icons.Default.Restore, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable(onClick = onRestorePurchases),
            )

            CategoryHeader("Settings")
            ListItem(
                headlineContent = { Text("Spaced repetition") },
                supportingContent = { Text("${state.preset.displayName} preset") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable(onClick = onOpenFsrsSettings),
            )
            ListItem(
                headlineContent = { Text("Notifications") },
                supportingContent = { Text("Daily study & goal reminders") },
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickable(onClick = onOpenNotifications),
            )

            CategoryHeader("Appearance")
            ThemeSelector(
                current = state.themeMode,
                onSelect = onThemeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            CategoryHeader("Feedback")
            LinkItem(Icons.Default.Star, "Rate Azri", onRate)
            LinkItem(Icons.Default.SupportAgent, "Contact support", onSupport)
            LinkItem(Icons.Default.Share, "Share Azri", onShare)
            LinkItem(Icons.Default.Forum, "Community on Reddit", onReddit)

            CategoryHeader("Legal")
            LinkItem(Icons.Default.Description, "Terms of Service", onTerms)
            LinkItem(Icons.Default.Shield, "Privacy Policy", onPrivacy)

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    "Sign out",
                    Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                "Made with 💗 by Astemir Boziev",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your account and synced data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteAccount() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                }) { Text("Cancel") }
            },
        )
    }
}

/** Inline tri-state theme switch — the M3 segmented control replaces an iOS-style picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Triple(ThemeMode.System, "System", Icons.Default.Brightness6),
        Triple(ThemeMode.Light, "Light", Icons.Default.LightMode),
        Triple(ThemeMode.Dark, "Dark", Icons.Default.DarkMode),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (mode, label, icon) ->
            SegmentedButton(
                selected = mode == current,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun CategoryHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun LinkItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Preview(name = "Profile", showBackground = true)
@Composable
private fun ProfilePreview() {
    AzriTheme {
        ProfileContent(
            state = ProfileUiState(
                email = "grace@example.com", isAnonymous = false,
                preset = FsrsPreset.Optimal, themeMode = ThemeMode.System,
            ),
            onOpenFsrsSettings = {}, onThemeChange = {}, onSignOut = {}, onDeleteAccount = {},
        )
    }
}

@Preview(
    name = "Profile · guest dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun ProfileGuestPreview() {
    AzriTheme(darkTheme = true) {
        ProfileContent(
            state = ProfileUiState(email = null, isAnonymous = true, themeMode = ThemeMode.Dark),
            onOpenFsrsSettings = {}, onThemeChange = {}, onSignOut = {}, onDeleteAccount = {},
        )
    }
}
