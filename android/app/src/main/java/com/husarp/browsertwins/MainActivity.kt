package com.husarp.browsertwins

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

// What the screens share: the settings, and a tick that goes up whenever something changed so every
// screen re-reads what is true now. Same idea as LinkPilot's Model, smaller.
class Model(val store: Store) {
    var tick by mutableIntStateOf(0)
    fun changed() { tick++ }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = Model(Store(applicationContext))
        setContent { App(model) }
    }
}

@Composable
fun App(m: Model) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Android's own colours: from the wallpaper on Android 12+ (Material You), else default Material.
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        var setup by rememberSaveable { mutableStateOf(!m.store.setupDone) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val tabs = listOf(
            "Profiles" to Icons.Default.Apps,
            "Menu" to Icons.Default.TouchApp,
            "Log" to Icons.Default.History,
            "Settings" to Icons.Default.Settings,
        )
        if (setup) Scaffold { pad ->
            Column(Modifier.padding(pad)) {
                SetupScreen(m) { m.store.setupDone = true; setup = false; m.changed() }
            }
        } else Scaffold(bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, null) }, label = { Text(label) },
                    )
                }
            }
        }) { pad ->
            Column(Modifier.padding(pad)) {
                when (tab) {
                    0 -> ProfilesTab(m)
                    1 -> MenuTab(m)
                    2 -> LogTab(m)
                    else -> SettingsTab(m) { setup = true }
                }
            }
        }
    }
}
