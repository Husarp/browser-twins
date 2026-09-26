package com.husarp.browsertwins

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
        Text("Make copies of your apps, each with its own logins and data.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard("How it works",
            "A profile is a copy of an app, with its own data.",
            listOf(
                "Each copy is a separate app on the phone - its own logins, cookies, tabs and history.",
                "Made for browsers (Android gives them no profiles), but any app works - like a second Messenger.",
                "Copies stay up to date with the original app, keeping their data.",
                "Works offline: Browser Twins has no internet permission. Copies are built from the app already on your phone.",
            )) {}
        SectionCard("One thing to allow",
            "Turn on \"Install unknown apps\" for Browser Twins, so it can install the copies it makes.") {
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
    var editing by remember { mutableStateOf<Profile?>(null) }
    var updating by remember { mutableStateOf<Profile?>(null) }
    if (making) { NewProfileFlow(m) { making = false }; return }
    editing?.let { EditProfileFlow(m, it) { editing = null }; return }
    updating?.let { UpdateProfileFlow(m, it) { updating = null }; return }

    val profiles = m.store.profiles
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profiles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (profiles.isEmpty()) {
            SectionCard("No profiles yet", "Make a copy of a browser or another app to get started.") {}
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles) { p -> ProfileRow(m, p, onEdit = { editing = p }, onUpdate = { updating = p }) }
            }
        }
        Button(onClick = { making = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New profile")
        }
    }
}

@Composable
private fun ProfileRow(m: Model, p: Profile, onEdit: () -> Unit, onUpdate: () -> Unit) {
    val ctx = LocalContext.current
    val icon = remember(p.sourcePkg, p.hue, p.strength, p.brightness) {
        appBitmap(ctx, p.sourcePkg)?.let { Recolour.apply(it, p.hue, p.strength, p.brightness).asImageBitmap() }
    }
    val installed = remember(p.clonePkg, m.tick) { Apps.isInstalled(ctx, p.clonePkg) }
    // The original was updated if its installed version differs from the one this clone was built from.
    val newVersion = remember(p.sourcePkg, m.tick) { if (Apps.isInstalled(ctx, p.sourcePkg)) Apps.version(ctx, p.sourcePkg) else "" }
    val updateReady = installed && newVersion.isNotEmpty() && newVersion != p.madeFromVersion
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    val (line, warn) = when {
                        !installed -> "Not installed - tap Remove" to true
                        updateReady -> "Update ready: ${Apps.label(ctx, p.sourcePkg)} $newVersion" to false
                        else -> "${Apps.label(ctx, p.sourcePkg)} ${p.madeFromVersion}" to false
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall,
                        color = when { warn -> MaterialTheme.colorScheme.error
                                       updateReady -> MaterialTheme.colorScheme.primary
                                       else -> MaterialTheme.colorScheme.onSurfaceVariant },
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (updateReady) TextButton(onClick = onUpdate) { Text("Update") }
                if (installed) TextButton(onClick = {
                    ctx.packageManager.getLaunchIntentForPackage(p.clonePkg)?.let { ctx.startActivity(it) }
                }) { Text("Open") }
                TextButton(onClick = onEdit) { Text("Edit") }
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
}

// ---- Edit an existing profile (name + colour); rebuild keeps the data --------------------------

@Composable
private fun EditProfileFlow(m: Model, p: Profile, close: () -> Unit) {
    val ctx = LocalContext.current
    var name by rememberSaveable { mutableStateOf(p.name) }
    var hue by rememberSaveable { mutableStateOf(p.hue.toFloat()) }
    var strength by rememberSaveable { mutableStateOf(p.strength.toFloat()) }
    var brightness by rememberSaveable { mutableStateOf(p.brightness.toFloat()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Text("Edit profile", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Name and icon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val preview = remember(p.sourcePkg, hue, strength, brightness) {
            appBitmap(ctx, p.sourcePkg)?.let { Recolour.apply(it, hue.toInt(), strength.toInt(), brightness.toInt()).asImageBitmap() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (preview != null) Image(preview, null, Modifier.size(56.dp))
            Text(name.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        SliderRow("Colour", hue, -180f, 180f) { hue = it }
        SliderRow("Strength", strength, 0f, 200f) { strength = it }
        SliderRow("Brightness", brightness, 50f, 150f) { brightness = it }
        when (val msg = result) {
            null -> if (busy) Text("Rebuilding… Android will ask to install. Your data is kept.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { hue = 0f; strength = 100f; brightness = 100f }) { Text("Reset") }
                Button(enabled = name.isNotBlank(), onClick = {
                    busy = true
                    val src = AppInfo(p.sourcePkg, Apps.label(ctx, p.sourcePkg), Apps.version(ctx, p.sourcePkg), false)
                    Thread {
                        try {
                            val parts = Cloner.build(ctx, src, p.clonePkg, name, hue.toInt(), strength.toInt(), brightness.toInt())
                            Installer.install(ctx, parts) { ok, m2 ->
                                if (ok) {
                                    m.store.profiles = m.store.profiles.map {
                                        if (it.id == p.id) it.copy(name = name, hue = hue.toInt(), strength = strength.toInt(), brightness = brightness.toInt()) else it
                                    }
                                    m.store.addLog("$name updated")
                                } else m.store.addLog("Couldn't update $name", m2)
                                m.changed()
                                result = if (ok) "Saved." else friendlyInstallError(m2)
                                busy = false
                            }
                        } catch (e: Exception) {
                            m.store.addLog("Couldn't update $name", e.message ?: ""); m.changed()
                            result = "Failed: ${e.message}"; busy = false
                        }
                    }.start()
                }) { Text("Save") }
            }
            else -> {
                Text(msg)
                Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Back to profiles") }
            }
        }
    }
}

// ---- Update a clone from the newer original; data is kept -----------------------------------------

@Composable
private fun UpdateProfileFlow(m: Model, p: Profile, close: () -> Unit) {
    val ctx = LocalContext.current
    val newVersion = remember(p.sourcePkg) { Apps.version(ctx, p.sourcePkg) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Back") }
        }
        Text("Update ${p.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard("From ${Apps.label(ctx, p.sourcePkg)}", "${p.madeFromVersion}  ->  $newVersion",
            listOf("The copy is rebuilt from the updated app and installed over itself. Your data in the copy is kept.")) {
            when (val msg = result) {
                null -> if (busy) Text("Rebuilding… Android may ask to install. Your data is kept.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true
                    val src = AppInfo(p.sourcePkg, Apps.label(ctx, p.sourcePkg), newVersion, false)
                    Thread {
                        try {
                            val parts = Cloner.build(ctx, src, p.clonePkg, p.name, p.hue, p.strength, p.brightness)
                            Installer.install(ctx, parts) { ok, m2 ->
                                if (ok) {
                                    m.store.profiles = m.store.profiles.map { if (it.id == p.id) it.copy(madeFromVersion = newVersion) else it }
                                    m.store.addLog("${p.name} updated to $newVersion (data kept)")
                                } else m.store.addLog("Couldn't update ${p.name}", m2)
                                m.changed()
                                result = if (ok) "Updated to $newVersion." else friendlyInstallError(m2)
                                busy = false
                            }
                        } catch (e: Exception) {
                            m.store.addLog("Couldn't update ${p.name}", e.message ?: ""); m.changed()
                            result = "Failed: ${e.message}"; busy = false
                        }
                    }.start()
                }) { Text("Update and install") }
                else -> {
                    Text(msg)
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Back to profiles") }
                }
            }
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
                                if (app.isSystem) Text("${app.version}  ·  system app - may not copy",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                else Text(app.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        result = if (ok) "Done. $name is installed." else friendlyInstallError(m2)
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
            "Choose which profiles appear, and their order. Most launchers show the first 4-5.",
            listOf("The menu is on Browser Twins' own icon. Each profile shows its own icon there.",
                   "Use the arrows to move a profile up or down.")) {
            if (profiles.isEmpty()) Text("No profiles yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            profiles.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { m.store.profiles = move(m.store.profiles, i, -1); m.changed() }, enabled = i > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move up")
                    }
                    IconButton(onClick = { m.store.profiles = move(m.store.profiles, i, 1); m.changed() }, enabled = i < profiles.size - 1) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move down")
                    }
                    Box(Modifier.weight(1f)) {
                        SwitchRow(p.name, null, p.inMenu) { on ->
                            m.store.profiles = m.store.profiles.map { if (it.id == p.id) it.copy(inMenu = on) else it }
                            m.changed()
                        }
                    }
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
            listOf("A daily background check rebuilds a clone when its app has a newer version.",
                   "On Android 12+ it's silent. On older Android a notification asks for one tap.")) {
            val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            SwitchRow("Update automatically", "Clones keep themselves up to date", store.autoUpdate) {
                store.autoUpdate = it
                if (it && Build.VERSION.SDK_INT >= 33) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                AutoUpdate.apply(ctx, store); m.changed()
            }
            SwitchRow("Only while charging", "Rebuilding uses some battery", store.onlyWhenCharging) {
                store.onlyWhenCharging = it; AutoUpdate.apply(ctx, store); m.changed()
            }
            SwitchRow("Notify me", null, store.notify) { store.notify = it; m.changed() }
            var checkNote by remember { mutableStateOf<String?>(null) }
            OutlinedButton(onClick = { AutoUpdate.checkNow(ctx); checkNote = "Checking… any updates install in the background." }) {
                Text("Check for updates now")
            }
            checkNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        SectionCard("Signing key", "Needed to update profiles without losing their data.",
            listOf("Every clone is signed with this key. Lose it and clones can't be updated without losing data. Back it up.")) {
            var note by remember { mutableStateOf<String?>(null) }
            val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                note = if (uri == null) null else try {
                    ctx.contentResolver.openOutputStream(uri)!!.use { Keys(ctx).exportTo(it) }; "Key backed up."
                } catch (e: Exception) { "Backup failed: ${e.message}" }
            }
            val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                note = if (uri == null) null else try {
                    ctx.contentResolver.openInputStream(uri)!!.use { Keys(ctx).importFrom(it) }
                    "Key restored. New clones and updates will use it."
                } catch (e: Exception) { "Restore failed: ${e.message}" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { backup.launch("browsertwins-key.p12") }) { Text("Back up key") }
                OutlinedButton(onClick = { restore.launch(arrayOf("*/*")) }) { Text("Restore key") }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        SectionCard("Log", null) {
            SwitchRow("Keep a log", "Newest 300, on this phone only", store.logOn) { store.logOn = it; m.changed() }
            TextButton(onClick = { store.clearLog(); m.changed() }) { Text("Clear the log") }
        }

        SectionCard("About", null) {
            val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { null } }
            Text("Browser Twins${version?.let { " $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
            Text("Works offline - no internet permission.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = showSetup) { Text("Show the setup again") }
        }
    }
}

// Turn Android's raw install error into something a person can act on.
private fun friendlyInstallError(raw: String): String = when {
    raw.contains("PARSE_FAILED", true) || raw.contains("load asset path", true) ->
        "This app can't be copied. Android wouldn't accept the copy's files - some apps, often the phone's built-in ones, can't be cloned this way."
    raw.contains("DUPLICATE_PERMISSION", true) || raw.contains("CONFLICTING_PROVIDER", true) ->
        "This app can't be copied - the copy clashes with the original."
    raw.contains("INSUFFICIENT_STORAGE", true) ->
        "Not enough free space to install the copy."
    raw.contains("cancel", true) || raw.contains("ABORTED", true) ->
        "Install cancelled."
    else -> "Couldn't install the copy: $raw"
}

// Move item i by delta (-1 up, +1 down), returning a new list.
private fun <T> move(list: List<T>, i: Int, delta: Int): List<T> {
    val j = i + delta
    if (j !in list.indices) return list
    return list.toMutableList().apply { add(j, removeAt(i)) }
}
