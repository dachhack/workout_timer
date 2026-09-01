package com.f3.workouttimer.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f3.workouttimer.data.PaxPhotoStore
import com.f3.workouttimer.data.TimerRepository
import com.f3.workouttimer.data.TimerShare
import com.f3.workouttimer.model.WorkoutTimer
import com.f3.workouttimer.model.formatDuration
import com.f3.workouttimer.timer.TimerService
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
                )
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
            IconButton(onClick = onImport) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = "Import a shared workout",
                    tint = F3Gray,
                )
            }
            IconButton(onClick = onPhotos) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Splash photos", tint = F3Gray)
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
