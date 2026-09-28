package com.f3.workouttimer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f3.workouttimer.BuildConfig
import com.f3.workouttimer.audio.WorkoutSounds
import com.f3.workouttimer.data.BanterRepository
import com.f3.workouttimer.data.PaxPhotoStore
import com.f3.workouttimer.data.TimerRepository
import com.f3.workouttimer.data.TimerShare
import com.f3.workouttimer.data.UpdateChecker
import com.f3.workouttimer.model.AppUpdate
import com.f3.workouttimer.model.VoiceCommand
import com.f3.workouttimer.model.matchCustomReply
import com.f3.workouttimer.model.parseVoiceCommand
import com.f3.workouttimer.model.updateAvailable
import com.f3.workouttimer.model.WorkoutTimer
import com.f3.workouttimer.model.formatDuration
import com.f3.workouttimer.timer.TimerService
import com.f3.workouttimer.voice.VoiceCommands
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3Gray
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onRun: (String) -> Unit,
    onSchedule: () -> Unit = {},
    onBanter: () -> Unit = {},
    importText: String? = null,
    onImportHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { TimerRepository.get(context) }
    val timers by repo.timers.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<WorkoutTimer?>(null) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    var importPrefill by remember { mutableStateOf<String?>(null) }

    // The lights are useful without a workout too — counting down sprints, a
    // race, anything. No run means no shared voice, so keep a local one.
    var lightsVisible by remember { mutableStateOf(false) }
    var goSignal by remember { mutableIntStateOf(0) }
    val localSounds = remember { mutableStateOf<WorkoutSounds?>(null) }
    DisposableEffect(Unit) { onDispose { localSounds.value?.release() } }
    fun sounds(): WorkoutSounds =
        localSounds.value ?: WorkoutSounds(context).also { localSounds.value = it }

    // Voice works here too, for the countdown and the custom replies. The rest
    // of the commands steer a run, and there is none on this screen.
    val banterRepo = remember { BanterRepository.get(context) }
    val replies by banterRepo.replies.collectAsStateWithLifecycle(initialValue = emptyList())
    val latestReplies = rememberUpdatedState(replies)

    val voice = remember {
        VoiceCommands(
            context = context,
            isAppSpeaking = { localSounds.value?.isSpeaking == true },
            onHeard = { candidates ->
                val banter = candidates.firstNotNullOfOrNull {
                    matchCustomReply(it, latestReplies.value)
                }
                when {
                    banter != null -> {
                        sounds().speak(banter.reply)
                        banter.trigger
                    }
                    candidates.any { parseVoiceCommand(it) == VoiceCommand.COUNTDOWN } -> {
                        lightsVisible = true
                        "count me down"
                    }
                    candidates.any { parseVoiceCommand(it) == VoiceCommand.GO } -> {
                        // Works whether the lights are already waiting or not.
                        lightsVisible = true
                        goSignal++
                        "here we go"
                    }
                    else -> null
                }
            },
        )
    }
    DisposableEffect(voice) { onDispose { voice.release() } }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) voice.start() }

    val toggleListening = {
        if (voice.isListening) {
            voice.stop()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) voice.start() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var micOpenedForLights by remember { mutableStateOf(false) }
    LaunchedEffect(lightsVisible) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (lightsVisible && granted && !voice.isListening) {
            voice.start()
            micOpenedForLights = true
        } else if (!lightsVisible && micOpenedForLights) {
            voice.stop()
            micOpenedForLights = false
        }
    }

    // A newer published build, if there is one and we could reach it.
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    var showUpdateNotes by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val latest = UpdateChecker.fetch()
        if (updateAvailable(latest, BuildConfig.VERSION_CODE)) update = latest
    }

    // A shared link opened the app: bring up the import sheet with it filled in.
    LaunchedEffect(importText) {
        if (importText != null) {
            importPrefill = importText
            onImportHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = F3White,
                contentColor = MaterialTheme.colorScheme.background,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New timer")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                F3Header(
                    onPhotos = { showPhotoDialog = true },
                    onImport = { importPrefill = "" },
                    onSchedule = onSchedule,
                    onBanter = onBanter,
                )
            }
            item {
                QuickActions(
                    listening = voice.isListening,
                    note = when {
                        voice.problem.isNotBlank() -> voice.problem
                        voice.isListening && voice.lastHeard.isNotBlank() ->
                            "Heard: ${voice.lastHeard}"
                        voice.isListening -> "Listening — say \"count me down\""
                        else -> ""
                    },
                    onCountdown = { lightsVisible = true },
                    onToggleListening = toggleListening,
                )
            }
            update?.takeIf { !updateDismissed }?.let { latest ->
                item {
                    Card(
                        onClick = { showUpdateNotes = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = LightGreen,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "UPDATE AVAILABLE",
                                    color = F3White,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                )
                                Text(
                                    "Version ${latest.versionName} — tap to see what's new",
                                    color = F3Gray,
                                    fontSize = 12.sp,
                                )
                            }
                            IconButton(onClick = { updateDismissed = true }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = F3Gray,
                                )
                            }
                        }
                    }
                }
            }
            TimerService.activeTimerId?.let { activeId ->
                item {
                    Card(
                        onClick = { onRun(activeId) },
                        colors = CardDefaults.cardColors(containerColor = F3White),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = F3Black,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "WORKOUT IN PROGRESS",
                                    color = F3Black,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp,
                                )
                                Text("Tap to jump back in", color = F3Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (timers.isEmpty()) {
                item {
                    Text(
                        text = "No timers yet.\nHit + to build your first beatdown.",
                        color = F3Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }
            }
            items(timers, key = { it.id }) { timer ->
                TimerCard(
                    timer = timer,
                    onRun = { onRun(timer.id) },
                    onEdit = { onEdit(timer.id) },
                    onDelete = { pendingDelete = timer },
                    onShare = { shareTimer(context, timer) },
                )
            }
        }
    }

    if (showUpdateNotes) {
        update?.let { latest ->
            UpdateDialog(
                update = latest,
                onDismiss = { showUpdateNotes = false },
                onDownload = {
                    showUpdateNotes = false
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(latest.downloadUrl))
                        )
                    }
                },
            )
        }
    }

    StartingLightsOverlay(
        visible = lightsVisible,
        armed = true,
        goSignal = goSignal,
        listening = voice.isListening,
        onLight = { sounds().countdownBeep() },
        onGo = { sounds().speak("Go") },
        onDismiss = { lightsVisible = false },
    )

    if (showPhotoDialog) {
        SplashPhotoDialog(onDismiss = { showPhotoDialog = false })
    }

    importPrefill?.let { prefill ->
        ImportDialog(
            initialText = prefill,
            onDismiss = { importPrefill = null },
            onImport = { timer ->
                scope.launch { repo.save(timer) }
                importPrefill = null
            },
        )
    }

    pendingDelete?.let { timer ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${timer.name}\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.delete(timer.id) }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun F3Header(
    onPhotos: () -> Unit,
    onImport: () -> Unit,
    onSchedule: () -> Unit,
    onBanter: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            F3Mark()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "WORKOUT TIMER",
                style = MaterialTheme.typography.headlineMedium,
                color = F3White,
            )
            Text(
                text = "FITNESS · FELLOWSHIP · FAITH",
                color = F3Gray,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
            )
        }
        Row(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = onSchedule) {
                Icon(Icons.Default.Alarm, contentDescription = "Schedule", tint = F3Gray)
            }
            // The rest would crowd the header, so they live behind the overflow.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = F3Gray)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Import a workout") },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onImport() },
                    )
                    DropdownMenuItem(
                        text = { Text("Voice replies") },
                        leadingIcon = {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onBanter() },
                    )
                    DropdownMenuItem(
                        text = { Text("Splash photos") },
                        leadingIcon = { Icon(Icons.Default.AddAPhoto, contentDescription = null) },
                        onClick = { menuOpen = false; onPhotos() },
                    )
                }
            }
        }
    }
}

/** Lets the PAX put their own photos behind the splash message. */
@Composable
private fun SplashPhotoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(PaxPhotoStore.count(context)) }
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            withContext(Dispatchers.IO) { uris.forEach { PaxPhotoStore.add(context, it) } }
            count = PaxPhotoStore.count(context)
            working = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Splash photos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Pictures of the PAX show behind the message when the app opens. " +
                        "One is picked at random each time.",
                    color = F3Gray,
                    fontSize = 13.sp,
                )
                Text(
                    when {
                        working -> "Adding photos…"
                        count == 0 -> "No photos yet."
                        count == 1 -> "1 photo saved."
                        else -> "$count photos saved."
                    },
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !working,
                ) { Text("Add photos") }
                if (count > 0) {
                    TextButton(
                        onClick = {
                            PaxPhotoStore.clear(context)
                            count = 0
                        },
                        enabled = !working,
                    ) { Text("Remove all") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun TimerCard(
    timer: WorkoutTimer,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        onClick = onRun,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(timer.name, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("${timer.blocks.size} block")
                        if (timer.blocks.size != 1) append("s")
                        val names = timer.blocks.mapNotNull { it.name.ifBlank { null } }
                        if (names.isNotEmpty()) {
                            append(" · ")
                            append(names.take(3).joinToString(", "))
                            if (names.size > 3) append("…")
                        }
                    },
                    color = F3Gray,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Total ${formatDuration(timer.totalSeconds())}",
                    color = F3White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            // Four controls would crowd the row, so everything but Start lives
            // behind the overflow.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = F3Gray)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { menuOpen = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(F3White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    tint = MaterialTheme.colorScheme.background,
                )
            }
        }
    }
}

/** Hands the workout to the system share sheet as a summary plus its link. */
private fun shareTimer(context: Context, timer: WorkoutTimer) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, timer.name)
        putExtra(Intent.EXTRA_TEXT, TimerShare.shareText(timer))
    }
    context.startActivity(Intent.createChooser(send, "Share workout"))
}

/**
 * Takes a shared link — pasted, or handed over by tapping one — and shows what
 * it contains before adding it.
 */
@Composable
private fun ImportDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onImport: (WorkoutTimer) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(initialText) }
    val decoded = remember(text) { TimerShare.decode(text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste the link another PAX shared with you.",
                    color = F3Gray,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Shared link") },
                    placeholder = { Text("f3timer://import?d=…", color = F3Gray) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { clipboard.getText()?.text?.let { text = it } },
                ) { Text("Paste from clipboard") }

                when {
                    text.isBlank() -> Unit
                    decoded == null -> Text(
                        "That doesn't look like a shared workout.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                    else -> Column {
                        Text(
                            decoded.name,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            buildString {
                                append("${decoded.blocks.size} block")
                                if (decoded.blocks.size != 1) append("s")
                                append(" · ${formatDuration(decoded.totalSeconds())}")
                            },
                            color = F3Gray,
                            fontSize = 13.sp,
                        )
                        val names = decoded.blocks.mapNotNull { it.name.ifBlank { null } }
                        if (names.isNotEmpty()) {
                            Text(
                                names.joinToString(" → "),
                                color = F3Gray,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            "Voice settings stay yours.",
                            color = F3Gray,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { decoded?.let(onImport) },
                enabled = decoded != null,
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What changed in the published build, and where to get it. */
@Composable
private fun UpdateDialog(
    update: AppUpdate,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Version ${update.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (update.notes.isEmpty()) {
                    Text("A newer build is available.", color = F3Gray, fontSize = 13.sp)
                } else {
                    update.notes.forEach { note ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("•  ", color = F3Gray)
                            Text(note, color = F3White, fontSize = 14.sp)
                        }
                    }
                }
                Text(
                    "Downloading opens your browser. Unzip it if needed, then open " +
                        "the APK to install over this one — your timers are kept.",
                    color = F3Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = F3White,
                    contentColor = F3Black,
                ),
            ) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

/** The two things worth doing from here without opening a workout. */
@Composable
private fun QuickActions(
    listening: Boolean,
    note: String,
    onCountdown: () -> Unit,
    onToggleListening: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCountdown,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Default.Traffic, contentDescription = null, tint = LightGreen)
                Spacer(Modifier.width(8.dp))
                Text("COUNT DOWN", letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
            // Listening needs to be visible at a glance, which is why this is a
            // button here rather than another icon in the header.
            Button(
                onClick = onToggleListening,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (listening) F3White else MaterialTheme.colorScheme.surface,
                    contentColor = if (listening) F3Black else F3Gray,
                ),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(
                    if (listening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (listening) "LISTENING" else "LISTEN",
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (note.isNotBlank()) {
            Text(note, color = F3Gray, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
