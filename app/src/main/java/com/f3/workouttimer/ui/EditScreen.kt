package com.f3.workouttimer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f3.workouttimer.audio.WorkoutSounds
import com.f3.workouttimer.audio.voiceLabel
import com.f3.workouttimer.data.TimerRepository
import com.f3.workouttimer.model.Stage
import com.f3.workouttimer.model.WorkoutTimer
import com.f3.workouttimer.model.formatDuration
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3Gray
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(timerId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TimerRepository.get(context) }

    val initial by produceState<WorkoutTimer?>(initialValue = null) {
        val list = repo.timers.first()
        value = timerId?.let { id -> list.find { it.id == id } } ?: WorkoutTimer()
    }

    val loaded = initial ?: return
    EditForm(
        initial = loaded,
        isNew = timerId == null,
        repo = repo,
        onDone = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditForm(
    initial: WorkoutTimer,
    isNew: Boolean,
    repo: TimerRepository,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var rounds by remember(initial.id) { mutableStateOf(initial.rounds) }
    var work by remember(initial.id) { mutableStateOf(initial.work) }
    var rest by remember(initial.id) { mutableStateOf(initial.rest) }
    var transition by remember(initial.id) { mutableStateOf(initial.transition) }
    var announceHalfway by remember(initial.id) { mutableStateOf(initial.announceHalfway) }
    var exercisesText by remember(initial.id) { mutableStateOf(initial.exercises.joinToString("\n")) }
    var voiceName by remember(initial.id) { mutableStateOf(initial.voiceName) }
    var voiceEngine by remember(initial.id) { mutableStateOf(initial.voiceEngine) }

    val draft = initial.copy(
        name = name.ifBlank { "Beatdown" },
        rounds = rounds,
        work = work,
        rest = rest,
        transition = transition,
        announceHalfway = announceHalfway,
        exercises = exercisesText.lines().map { it.trim() }.filter { it.isNotEmpty() },
        voiceName = voiceName,
        voiceEngine = voiceEngine,
    )
    val totalSeconds = draft.totalSeconds()
    val valid = totalSeconds > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "NEW TIMER" else "EDIT TIMER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Timer name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            RoundsPicker(rounds = rounds, onChange = { rounds = it })

            StageEditor(
                title = "WORK",
                subtitle = "The pain station",
                stage = work,
                defaultMessage = "Work",
                onChange = { work = it },
            )
            StageEditor(
                title = "REST",
                subtitle = "Catch your breath",
                stage = rest,
                defaultMessage = "Rest",
                onChange = { rest = it },
            )
            StageEditor(
                title = "TRANSITION",
                subtitle = "Move to the next station",
                stage = transition,
                defaultMessage = "Transition",
                onChange = { transition = it },
            )

            ExercisesEditor(text = exercisesText, onChange = { exercisesText = it })

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("HALFWAY CALL-OUT", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text(
                            "Speaks at the midpoint of the workout",
                            color = F3Gray,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = announceHalfway,
                        onCheckedChange = { announceHalfway = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = F3Black,
                            checkedTrackColor = F3White,
                        ),
                    )
                }
            }

            VoicePicker(
                selectedEngine = voiceEngine,
                selectedVoiceName = voiceName,
                // Voices are engine-specific, so switching engines resets the voice.
                onSelectEngine = { voiceEngine = it; voiceName = "" },
                onSelectVoice = { voiceName = it },
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("TOTAL WORKOUT", color = F3Gray, fontSize = 12.sp, letterSpacing = 2.sp)
                    Text(
                        text = formatDuration(totalSeconds),
                        color = F3White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                    )
                    if (!valid) {
                        Text(
                            "Enable at least one stage with time on it",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        repo.save(draft)
                        onDone()
                    }
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = F3White,
                    contentColor = F3Black,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("SAVE", fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoundsPicker(rounds: Int, onChange: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "ROUNDS",
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { if (rounds > 1) onChange(rounds - 1) }) { Text("−", fontSize = 20.sp) }
            Text(
                text = "$rounds",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedButton(onClick = { if (rounds < 99) onChange(rounds + 1) }) { Text("+", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun ExercisesEditor(text: String, onChange: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("EXERCISES", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(
                "One per line — round 1 gets line 1, and the list repeats if there are " +
                    "more rounds than lines. Shown on screen and spoken when each work stage starts.",
                color = F3Gray,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = text,
                onValueChange = onChange,
                label = { Text("Exercise list") },
                placeholder = { Text("Merkins\nSquats\nBurpees", color = F3Gray) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(
    selectedEngine: String,
    selectedVoiceName: String,
    onSelectEngine: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
) {
    val context = LocalContext.current
    // A TTS instance is bound to one engine, so switching engines rebuilds it.
    val sounds = remember(selectedEngine) { WorkoutSounds(context, selectedEngine) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }

    var showDialog by remember { mutableStateOf(false) }
    val voices = if (sounds.isReady) sounds.availableVoices() else emptyList()
    val engines = sounds.availableEngines()

    val engineLabel = when {
        selectedEngine.isBlank() ->
            engines.firstOrNull { it.name == sounds.defaultEngineName() }
                ?.let { "${it.label} (default)" } ?: "Default engine"
        else -> engines.firstOrNull { it.name == selectedEngine }?.label ?: selectedEngine
    }
    val voiceText = when {
        selectedVoiceName.isBlank() -> "Engine default voice"
        else -> voices.firstOrNull { it.name == selectedVoiceName }?.let { voiceLabel(it) }
            ?: selectedVoiceName
    }

    Card(
        onClick = { showDialog = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("VOICE", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(engineLabel, color = F3Gray, fontSize = 12.sp)
                Text(voiceText, color = F3Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = F3Gray)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Voice") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                    if (engines.size > 1) {
                        item { SectionLabel("ENGINE") }
                        item {
                            VoiceRow(
                                label = "Device default",
                                selected = selectedEngine.isBlank(),
                                onClick = { onSelectEngine("") },
                            )
                        }
                        items(engines, key = { it.name }) { eng ->
                            VoiceRow(
                                label = eng.label,
                                selected = eng.name == selectedEngine,
                                onClick = { onSelectEngine(eng.name) },
                            )
                        }
                        item { SectionLabel("VOICE") }
                    }
                    if (!sounds.isReady) {
                        item { Text("Loading voices…", color = F3Gray) }
                    } else {
                        item {
                            VoiceRow(
                                label = "Engine default voice",
                                selected = selectedVoiceName.isBlank(),
                                onClick = {
                                    onSelectVoice("")
                                    sounds.setVoiceByName("")
                                    sounds.speak("Ready to work")
                                },
                            )
                        }
                        items(voices, key = { it.name }) { voice ->
                            VoiceRow(
                                label = voiceLabel(voice),
                                selected = voice.name == selectedVoiceName,
                                onClick = {
                                    onSelectVoice(voice.name)
                                    sounds.setVoiceByName(voice.name)
                                    sounds.speak("Ready to work")
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = F3Gray,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun VoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) F3White else F3Gray,
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = F3White)
        }
    }
}

@Composable
private fun StageEditor(
    title: String,
    subtitle: String,
    stage: Stage,
    defaultMessage: String,
    onChange: (Stage) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text(subtitle, color = F3Gray, fontSize = 12.sp)
                }
                Switch(
                    checked = stage.enabled,
                    onCheckedChange = { onChange(stage.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = F3Black,
                        checkedTrackColor = F3White,
                    ),
                )
            }
            if (stage.enabled) {
                OutlinedTextField(
                    value = if (stage.seconds == 0) "" else stage.seconds.toString(),
                    onValueChange = { text ->
                        val digits = text.filter { it.isDigit() }.take(4)
                        onChange(stage.copy(seconds = digits.toIntOrNull() ?: 0))
                    },
                    label = { Text("Seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = stage.message,
                    onValueChange = { onChange(stage.copy(message = it)) },
                    label = { Text("Spoken message (optional)") },
                    placeholder = { Text("Default: \"$defaultMessage\"", color = F3Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
