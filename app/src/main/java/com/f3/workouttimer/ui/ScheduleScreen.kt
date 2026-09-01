package com.f3.workouttimer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f3.workouttimer.alarm.CueScheduler
import com.f3.workouttimer.data.ScheduleRepository
import com.f3.workouttimer.data.TimerRepository
import com.f3.workouttimer.model.ScheduledCue
import com.f3.workouttimer.model.WEEKDAYS
import com.f3.workouttimer.model.WorkoutTimer
import com.f3.workouttimer.model.shortDayName
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3Gray
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ScheduleRepository.get(context) }
    val timerRepo = remember { TimerRepository.get(context) }
    val cues by repo.cues.collectAsStateWithLifecycle(initialValue = emptyList())
    val timers by timerRepo.timers.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<ScheduledCue?>(null) }
    var pendingDelete by remember { mutableStateOf<ScheduledCue?>(null) }
    var exactAllowed by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { exactAllowed = CueScheduler.canScheduleExact(context) }

    // A cue is easier to notice with its notification, so ask here too — the
    // run screen is not necessarily somewhere this user has been.
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun persist(cue: ScheduledCue) {
        scope.launch {
            repo.save(cue)
            CueScheduler.schedule(context, cue)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("SCHEDULE", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = F3White,
                    navigationIconContentColor = F3White,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = ScheduledCue() },
                containerColor = F3White,
                contentColor = F3Black,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New scheduled cue")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Cues fire at a set time whether or not the app is open — sound " +
                        "an alert, say something, start a workout, or all three.",
                    color = F3Gray,
                    fontSize = 13.sp,
                )
            }
            if (!exactAllowed) {
                item { ExactAlarmWarning() }
            }
            if (cues.isEmpty()) {
                item {
                    Text(
                        text = "Nothing scheduled.\nHit + to set a time.",
                        color = F3Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    )
                }
            }
            items(cues, key = { it.id }) { cue ->
                CueCard(
                    cue = cue,
                    timers = timers,
                    onToggle = { enabled -> persist(cue.copy(enabled = enabled)) },
                    onEdit = { editing = cue },
                    onDelete = { pendingDelete = cue },
                )
            }
        }
    }

    editing?.let { cue ->
        CueEditor(
            cue = cue,
            timers = timers,
            onDismiss = { editing = null },
            onSave = {
                persist(it)
                editing = null
            },
        )
    }

    pendingDelete?.let { cue ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this cue?") },
            text = { Text("${cue.timeLabel()} · ${cue.daysLabel()}") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        CueScheduler.cancel(context, cue)
                        repo.delete(cue.id)
                    }
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
private fun ExactAlarmWarning() {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "ALARMS NOT EXACT",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Text(
                "Android hasn't granted this app exact alarms, so cues can drift by " +
                    "a few minutes. Turn on \"Alarms & reminders\" to fix that.",
                color = F3Gray,
                fontSize = 13.sp,
            )
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 31) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        )
                    }
                }
            }) { Text("Open settings") }
        }
    }
}

@Composable
private fun CueCard(
    cue: ScheduledCue,
    timers: List<WorkoutTimer>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val timer = timers.find { it.id == cue.timerId }
    val block = timer?.blocks?.find { it.id == cue.blockId }
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (cue.label.isNotBlank()) {
                    Text(
                        cue.label.uppercase(),
                        color = F3Gray,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    cue.timeLabel(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if (cue.enabled) F3White else F3Gray,
                )
                Text(cue.daysLabel(), color = F3Gray, fontSize = 13.sp)
                Text(
                    cue.actionLabel(
                        timerName = timer?.name,
                        blockName = block?.name?.ifBlank { null },
                    ),
                    color = F3Gray,
                    fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = cue.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = F3Black,
                        checkedTrackColor = F3White,
                    ),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = F3Gray)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CueEditor(
    cue: ScheduledCue,
    timers: List<WorkoutTimer>,
    onDismiss: () -> Unit,
    onSave: (ScheduledCue) -> Unit,
) {
    var draft by remember(cue.id) { mutableStateOf(cue) }
    val timer = timers.find { it.id == draft.timerId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scheduled cue") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = draft.hour,
                        label = "Hour (0-23)",
                        max = 23,
                        onChange = { draft = draft.copy(hour = it) },
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = draft.minute,
                        label = "Minute",
                        max = 59,
                        onChange = { draft = draft.copy(minute = it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(draft.timeLabel(), fontSize = 24.sp, fontWeight = FontWeight.Black)

                Text("REPEAT", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = day in draft.days,
                            onClick = {
                                val days = draft.days.toMutableSet()
                                if (!days.add(day)) days.remove(day)
                                draft = draft.copy(days = days)
                            },
                            label = { Text(shortDayName(day)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = F3White,
                                selectedLabelColor = F3Black,
                            ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { draft = draft.copy(days = WEEKDAYS) }) {
                        Text("Weekdays")
                    }
                    TextButton(onClick = { draft = draft.copy(days = emptySet()) }) {
                        Text("Just once")
                    }
                }

                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { draft = draft.copy(label = it) },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("AO start", color = F3Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound an alert", Modifier.weight(1f))
                    Switch(
                        checked = draft.alert,
                        onCheckedChange = { draft = draft.copy(alert = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = F3Black,
                            checkedTrackColor = F3White,
                        ),
                    )
                }
                OutlinedTextField(
                    value = draft.message,
                    onValueChange = { draft = draft.copy(message = it) },
                    label = { Text("Say this (optional)") },
                    placeholder = { Text("Circle up, gentlemen", color = F3Gray) },
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Text(
                    "START A WORKOUT",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 12.sp,
                )
                PickerRow(
                    label = "None",
                    selected = draft.timerId.isBlank(),
                    onClick = { draft = draft.copy(timerId = "", blockId = "") },
                )
                timers.forEach { t ->
                    PickerRow(
                        label = t.name,
                        selected = t.id == draft.timerId,
                        onClick = { draft = draft.copy(timerId = t.id, blockId = "") },
                    )
                }
                if (timer != null && timer.blocks.size > 1) {
                    Text(
                        "Which part",
                        color = F3Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    PickerRow(
                        label = "The whole workout",
                        selected = draft.blockId.isBlank(),
                        onClick = { draft = draft.copy(blockId = "") },
                    )
                    timer.blocks.forEachIndexed { index, block ->
                        PickerRow(
                            label = block.name.ifBlank { "Block ${index + 1}" },
                            selected = block.id == draft.blockId,
                            onClick = { draft = draft.copy(blockId = block.id) },
                        )
                    }
                }

                if (draft.isSilent) {
                    Text(
                        "Pick at least one thing for this cue to do.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(draft.copy(enabled = true)) },
                enabled = !draft.isSilent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = F3White,
                    contentColor = F3Black,
                ),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(
    value: Int,
    label: String,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            val digits = text.filter { it.isDigit() }.take(2)
            onChange((digits.toIntOrNull() ?: 0).coerceIn(0, max))
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    label,
                    color = if (selected) F3White else F3Gray,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = F3White,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}
