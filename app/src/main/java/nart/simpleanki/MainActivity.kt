package nart.simpleanki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import nart.simpleanki.ui.AzriRoot
import nart.simpleanki.ui.theme.AzriTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AzriTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AzriRoot()
                }
            }
        }
    }
}
