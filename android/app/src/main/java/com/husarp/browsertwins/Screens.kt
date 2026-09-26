package com.husarp.browsertwins

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import java.util.UUID

// The look follows LinkPilot: every section is a card with a title, one short line, and a longer
// explanation behind an (i). Building blocks first, then the tabs.

@Composable
fun SectionCard(title: String, line: String? = null, info: List<String>? = null, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (info != null) Icon(Icons.Outlined.Info, if (open) "Less" else "More about this",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp).clip(CircleShape).clickable { open = !open }.padding(4.dp).size(20.dp))
            }
            if (line != null) Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (open && info != null) InfoBox(info)
            content()
        }
    }
}

@Composable
fun InfoBox(points: List<String>) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(14.dp),
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { point ->
            Row {
                Text("•", color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.width(14.dp))
                Text(point, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, text: String?, on: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (text != null) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = change)
    }
}

@Composable
private fun NumberedSteps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(step, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun appBitmap(ctx: android.content.Context, pkg: String): Bitmap? =
    Apps.icon(ctx, pkg)?.toBitmap(96, 96)

// ---- setup ---------------------------------------------------------------------------------------

@Composable
fun SetupScreen(m: Model, done: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Browser Twins", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SectionCard("How it works",
            "A profile is a copy of an app with its own data.",
            listOf(
                "Each copy is a separate app on the phone, with its own logins, cookies, tabs and history.",
                "Mainly for browsers, which have no profiles on Android - but any app works, e.g. a second Messenger.",
                "Works offline: no internet permission. New versions come from the app already on the phone.",
            )) {}
        SectionCard("Allow installing apps",
            "Needed so Browser Twins can install the profiles it makes.") {
            OutlinedButton(onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))) }
                catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }) { Text("Open the setting") }
        }
        Button(onClick = done, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
    }
}

// ---- Profiles ------------------------------------------------------------------------------------

@Composable
fun ProfilesTab(m: Model) {
    m.tick
    var making by rememberSaveable { mutableStateOf(false) }
    if (making) { NewProfileFlow(m) { making = false }; return }

    val profiles = m.store.profiles
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profiles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (profiles.isEmpty()) {
            SectionCard("No profiles yet", "Make a copy of a browser or another app to get started.") {}
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles) { p -> ProfileRow(m, p) }
            }
        }
        Button(onClick = { making = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New profile")
        }
    }
}

@Composable
private fun ProfileRow(m: Model, p: Profile) {
    val ctx = LocalContext.current
    val icon = remember(p.sourcePkg, p.hue, p.strength, p.brightness) {
        appBitmap(ctx, p.sourcePkg)?.let { Recolour.apply(it, p.hue, p.strength, p.brightness).asImageBitmap() }
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            val installed = remember(p.clonePkg, m.tick) { Apps.isInstalled(ctx, p.clonePkg) }
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(if (installed) "${Apps.label(ctx, p.sourcePkg)} ${p.madeFromVersion}" else "Not installed - tap Remove",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (installed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (installed) TextButton(onClick = {
                ctx.packageManager.getLaunchIntentForPackage(p.clonePkg)?.let { ctx.startActivity(it) }
            }) { Text("Open") }
            TextButton(onClick = {
                // Uninstall the clone (Android asks its own "Uninstall?"), then drop the record.
                if (installed) ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${p.clonePkg}")))
                m.store.profiles = m.store.profiles.filterNot { it.id == p.id }
                m.store.addLog("${p.name} removed")
                m.changed()
            }) { Text("Remove") }
        }
    }
}

// ---- New profile (3 steps) -----------------------------------------------------------------------

@Composable
private fun NewProfileFlow(m: Model, close: () -> Unit) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableStateOf(1) }
    var chosen by remember { mutableStateOf<AppInfo?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var hue by rememberSaveable { mutableStateOf(0f) }
    var strength by rememberSaveable { mutableStateOf(100f) }
    var brightness by rememberSaveable { mutableStateOf(100f) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Text("Step $step of 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when (step) {
            1 -> {
                Text("Pick an app", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
                LaunchedEffect(Unit) { apps = Apps.all(ctx) }
                val list = apps
                if (list == null) Text("Reading the apps on the phone…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(list) { app ->
                        Row(Modifier.fillMaxWidth().clickable {
                            chosen = app
                            name = suggestName(app, m.store.profiles)
                            step = 2
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val bmp = remember(app.pkg) { appBitmap(ctx, app.pkg)?.asImageBitmap() }
                            if (bmp != null) Image(bmp, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.label + if (app.isBrowser) "  ·  browser" else "")
                                Text(app.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            2 -> {
                val app = chosen!!
                Text("Name and icon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val preview = remember(app.pkg, hue, strength, brightness) {
                    appBitmap(ctx, app.pkg)?.let { Recolour.apply(it, hue.toInt(), strength.toInt(), brightness.toInt()).asImageBitmap() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (preview != null) Image(preview, null, Modifier.size(56.dp))
                    Text(name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                SliderRow("Colour", hue, -180f, 180f) { hue = it }
                SliderRow("Strength", strength, 0f, 200f) { strength = it }
                SliderRow("Brightness", brightness, 50f, 150f) { brightness = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { hue = 0f; strength = 100f; brightness = 100f }) { Text("Reset") }
                    Button(onClick = { step = 3; result = null }, enabled = name.isNotBlank()) { Text("Next") }
                }
            }
            3 -> {
                val app = chosen!!
                Text("Make it", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SectionCard("Building ${name}", "From ${app.label} ${app.version}") {
                    var busy by remember { mutableStateOf(false) }
                    val msg = result
                    when {
                        busy -> Text("Working… Android will ask to install.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        msg == null -> Button(onClick = {
                            busy = true
                            val clonePkg = Cloner.clonePackageName(app.pkg, m.store.profiles)
                            Thread {
                                try {
                                    val parts = Cloner.build(ctx, app, clonePkg, name, hue.toInt(), strength.toInt(), brightness.toInt())
                                    Installer.install(ctx, parts) { ok, m2 ->
                                        if (ok) {
                                            m.store.profiles = m.store.profiles + Profile(
                                                java.util.UUID.randomUUID().toString(), name, app.pkg, clonePkg, app.version,
                                                hue.toInt(), strength.toInt(), brightness.toInt(),
                                            )
                                            m.store.addLog("Made $name from ${app.label} ${app.version}")
                                        } else m.store.addLog("Couldn't make $name", m2)
                                        m.changed()
                                        result = if (ok) "Done. $name is installed." else "Install failed: $m2"
                                        busy = false
                                    }
                                } catch (e: Exception) {
                                    m.store.addLog("Couldn't make $name", e.message ?: "")
                                    m.changed(); result = "Failed: ${e.message}"; busy = false
                                }
                            }.start()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Build and install") }
                        else -> {
                            Text(msg)
                            Spacer(Modifier.size(8.dp))
                            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Back to profiles") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, from: Float, to: Float, change: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(value, change, valueRange = from..to, modifier = Modifier.weight(1f))
        Text("${value.toInt()}", Modifier.width(44.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun suggestName(app: AppInfo, existing: List<Profile>): String {
    val n = existing.count { it.sourcePkg == app.pkg } + 2
    return "${app.label} $n"
}

// ---- Menu ----------------------------------------------------------------------------------------

@Composable
fun MenuTab(m: Model) {
    m.tick
    val profiles = m.store.profiles
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Long-press menu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SectionCard("What shows when you hold the icon",
            "Choose which profiles appear. Most launchers show the first 4-5.",
            listOf("The menu is on Browser Twins' own icon. Each profile shows its own icon there.")) {
            if (profiles.isEmpty()) Text("No profiles yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            profiles.forEach { p ->
                SwitchRow(p.name, null, p.inMenu) { on ->
                    m.store.profiles = m.store.profiles.map { if (it.id == p.id) it.copy(inMenu = on) else it }
                    m.changed()
                }
            }
        }
    }
}

// ---- Log -----------------------------------------------------------------------------------------

@Composable
fun LogTab(m: Model) {
    m.tick
    val log = m.store.readLog()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Log", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (log.isEmpty()) Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(log) { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(e.text, style = MaterialTheme.typography.bodyLarge)
                    Text(e.time + if (e.detail.isNotBlank()) "  ·  ${e.detail}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- Settings ------------------------------------------------------------------------------------

@Composable
fun SettingsTab(m: Model, showSetup: () -> Unit) {
    m.tick
    val ctx = LocalContext.current
    val store = m.store
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SectionCard("Updates", "Keep profiles up to date with the original app.",
            listOf("On Android 12+ updates can be silent. On older Android each one needs a tap.")) {
            SwitchRow("Update automatically", "No taps on Android 12+", store.autoUpdate) { store.autoUpdate = it; m.changed() }
            SwitchRow("Only while charging", "Rebuilding uses some battery", store.onlyWhenCharging) { store.onlyWhenCharging = it; m.changed() }
            SwitchRow("Notify me", null, store.notify) { store.notify = it; m.changed() }
        }

        SectionCard("Signing key", "Needed to update profiles without losing their data.",
            listOf("Every clone is signed with this key. Lose it and clones can't be updated without losing data. Back it up.")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { /* TODO: back up key */ }) { Text("Back up key") }
                OutlinedButton(onClick = { /* TODO: restore key */ }) { Text("Restore key") }
            }
        }

        SectionCard("Log", null) {
            SwitchRow("Keep a log", "Newest 300, on this phone only", store.logOn) { store.logOn = it; m.changed() }
            TextButton(onClick = { store.clearLog(); m.changed() }) { Text("Clear the log") }
        }

        SectionCard("About", null) {
            Text("Works offline - no internet permission.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = showSetup) { Text("Show the setup again") }
        }
    }
}
