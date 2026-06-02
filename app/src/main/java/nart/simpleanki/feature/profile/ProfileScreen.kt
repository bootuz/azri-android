package nart.simpleanki.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.core.data.settings.ThemeMode
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import nart.simpleanki.ui.components.AzriCard
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
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    fun openUrl(url: String) =
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    ProfileContent(
        state = state,
        onOpenFsrsSettings = onOpenFsrsSettings,
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
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onRate: () -> Unit = {},
    onSupport: () -> Unit = {},
    onShare: () -> Unit = {},
    onReddit: () -> Unit = {},
    onTerms: () -> Unit = {},
    onPrivacy: () -> Unit = {},
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isAnonymous) "Account" else "Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete account", color = MaterialTheme.colorScheme.error) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("Account")
            SectionCard {
                ProfileRow(Icons.Default.Email, "Email", value = state.email ?: "Not provided")
                Divider()
                ProfileRow(Icons.Default.Person, "Account", value = if (state.isAnonymous) "Guest" else "Synced")
            }

            SectionHeader("Settings")
            SectionCard {
                ProfileRow(
                    Icons.Default.Tune, "Spaced repetition",
                    value = state.preset.name, onClick = onOpenFsrsSettings,
                )
                Divider()
                ProfileRow(
                    Icons.Default.DarkMode, "Theme",
                    value = state.themeMode.label(), onClick = { showThemeDialog = true },
                )
            }

            SectionHeader("Feedback")
            SectionCard {
                ProfileRow(Icons.Default.Star, "Rate Azri", onClick = onRate)
                Divider()
                ProfileRow(Icons.Default.SupportAgent, "Contact support", onClick = onSupport)
                Divider()
                ProfileRow(Icons.Default.Share, "Share Azri", onClick = onShare)
                Divider()
                ProfileRow(Icons.Default.Forum, "Community (Reddit)", onClick = onReddit)
            }

            SectionHeader("Legal")
            SectionCard {
                ProfileRow(Icons.Default.Description, "Terms of Service", onClick = onTerms)
                Divider()
                ProfileRow(Icons.Default.Shield, "Privacy Policy", onClick = onPrivacy)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("Sign out", Modifier.padding(start = 8.dp))
            }
            Text(
                "Made with ❤ by Astemir Boziev",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            current = state.themeMode,
            onSelect = { onThemeChange(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
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
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> "System"
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
}

@Composable
private fun ThemePickerDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Text(mode.label(), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    AzriCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, value: String? = null, onClick: (() -> Unit)? = null) {
    val rowModifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(
        rowModifier.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
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

@Preview(name = "Profile · guest dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileGuestPreview() {
    AzriTheme(darkTheme = true) {
        ProfileContent(
            state = ProfileUiState(email = null, isAnonymous = true, themeMode = ThemeMode.Dark),
            onOpenFsrsSettings = {}, onThemeChange = {}, onSignOut = {}, onDeleteAccount = {},
        )
    }
}
