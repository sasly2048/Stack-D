package app.stackd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.stackd.core.theme.StackdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Land on a real screen, not the still-placeholder Landing. A returning
        // signed-in user goes straight to the dashboard; everyone else to auth.
        // Landing/Philosophy are marketing surfaces for a later phase.
        val container = (application as StackdApplication).container
        val start = if (container.auth.currentUserId != null) {
            Dest.Dashboard.route
        } else {
            Dest.Auth.route
        }

        setContent {
            StackdTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StackdNavHost(startDestination = start)
                }
            }
        }
    }
}
