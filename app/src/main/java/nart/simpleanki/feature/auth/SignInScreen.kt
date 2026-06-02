package nart.simpleanki.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nart.simpleanki.R
import nart.simpleanki.ui.theme.AzriTheme

/** Stateless sign-in screen. Hosting layer wires the callbacks to [nart.simpleanki.auth.AuthViewModel]. */
@Composable
fun SignInScreen(
    onGoogleClick: () -> Unit,
    onGuestClick: () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // App mark
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onGoogleClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(stringResource(R.string.sign_in_with_google), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onGuestClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(R.string.continue_as_guest))
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Sign in", showBackground = true)
@Composable
private fun SignInScreenPreview() {
    AzriTheme {
        SignInScreen(onGoogleClick = {}, onGuestClick = {})
    }
}

@Preview(name = "Sign in · dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SignInScreenDarkPreview() {
    AzriTheme(darkTheme = true) {
        SignInScreen(onGoogleClick = {}, onGuestClick = {}, errorMessage = "Sign-in cancelled")
    }
}
