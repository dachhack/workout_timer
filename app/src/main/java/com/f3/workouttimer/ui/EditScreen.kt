package com.f3.workouttimer.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f3.workouttimer.audio.WorkoutSounds
import com.f3.workouttimer.audio.voiceLabel
import com.f3.workouttimer.data.TimerRepository
import com.f3.workouttimer.model.Block
import com.f3.workouttimer.model.Stage
import com.f3.workouttimer.model.WorkoutTimer
import com.f3.workouttimer.model.formatDuration
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3Gray
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A new timer opens with one block already laid out, ready to edit. */
private fun newTimer() = WorkoutTimer(blocks = listOf(Block(rounds = 5)))

@Composable
fun EditScreen(timerId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { TimerRepository.get(context) }

    val initial by produceState<WorkoutTimer?>(initialValue = null) {
        val list = repo.timers.first()
        value = timerId?.let { id -> list.find { it.id == id } } ?: newTimer()
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
    var blocks by remember(initial.id) { mutableStateOf(initial.blocks) }
    var announceNextExercise by remember(initial.id) {
        mutableStateOf(initial.announceNextExercise)
    }
    var voiceName by remember(initial.id) { mutableStateOf(initial.voiceName) }
    var voiceEngine by remember(initial.id) { mutableStateOf(initial.voiceEngine) }

    // Only one block is expanded at a time by default, so the list stays scannable.
    val expanded = remember(initial.id) {
        mutableStateMapOf<String, Boolean>().apply {
            if (isNew) initial.blocks.firstOrNull()?.let { put(it.id, true) }
        }
    }

    val draft = initial.copy(
        name = name.ifBlank { "Beatdown" },
        blocks = blocks,
        announceNextExercise = announceNextExercise,
        voiceName = voiceName,
        voiceEngine = voiceEngine,
    )
    val totalSeconds = draft.totalSeconds()
    val valid = totalSeconds > 0

    fun updateBlock(index: Int, block: Block) {
        blocks = blocks.toMutableList().apply { set(index, block) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "NEW TIMER" else "EDIT TIMER",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                },
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

            Column {
                Text("BLOCKS", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(
                    "The workout runs these top to bottom. Each block is its own " +
                        "circuit: exercises, rounds, and timings.",
                    color = F3Gray,
                    fontSize = 12.sp,
                )
            }

            blocks.forEachIndexed { index, block ->
                BlockCard(
                    index = index,
                    count = blocks.size,
                    block = block,
                    expanded = expanded[block.id] == true,
                    onToggleExpanded = { expanded[block.id] = expanded[block.id] != true },
                    onChange = { updateBlock(index, it) },
                    onMove = { delta ->
                        val target = index + delta
                        if (target in blocks.indices) {
                            blocks = blocks.toMutableList().apply { add(target, removeAt(index)) }
                        }
                    },
                    onDelete = {
                        blocks = blocks.toMutableList().apply { removeAt(index) }
                    },
                )
            }

            OutlinedButton(
                onClick = {
                    val fresh = Block()
                    blocks = blocks + fresh
                    expanded[fresh.id] = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("+ ADD BLOCK", letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("CALL OUT NEXT EXERCISE", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text(
                            "Announces what's coming during rest and transition",
                            color = F3Gray,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = announceNextExercise,
                        onCheckedChange = { announceNextExercise = it },
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
                    Modifier.fillMaxWidth().padding(16.dp),
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
                            "Add a block with time on it",
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("SAVE", fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BlockCard(
    index: Int,
    count: Int,
    block: Block,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChange: (Block) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var exercisesText by remember(block.id) { mutableStateOf(block.exercises.joinToString("\n")) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = block.name.ifBlank { "BLOCK ${index + 1}" }.uppercase(),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Text(blockSummary(block), color = F3Gray, fontSize = 12.sp)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = F3Gray,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BLOCK ${index + 1} OF $count",
                    color = F3Gray,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                if (count > 1) {
                    IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = if (index > 0) F3Gray else MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = if (index < count - 1) F3Gray else MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove block", tint = F3Gray)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    OutlinedTextField(
                        value = block.name,
                        onValueChange = { onChange(block.copy(name = it)) },
                        label = { Text("Block name") },
                        placeholder = { Text("Warm-up, Cardio, Weights…", color = F3Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    RoundsRow(
                        rounds = block.rounds,
                        onChange = { onChange(block.copy(rounds = it)) },
                    )

                    OutlinedTextField(
                        value = exercisesText,
                        onValueChange = {
                            exercisesText = it
                            onChange(
                                block.copy(
                                    exercises = it.lines().map(String::trim).filter(String::isNotEmpty)
                                )
                            )
                        },
                        label = { Text("Exercises — one per line") },
                        placeholder = { Text("Merkins\nSquats\nBurpees", color = F3Gray) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Every round runs the whole list in order. Leave empty for a " +
                            "single timed block.",
                        color = F3Gray,
                        fontSize = 12.sp,
                    )

                    StageEditor(
                        title = "WORK",
                        subtitle = "Time at each exercise",
                        stage = block.work,
                        defaultMessage = "Work",
                        onChange = { onChange(block.copy(work = it)) },
                    )
                    StageEditor(
                        title = "REST",
                        subtitle = "Between exercises",
                        stage = block.rest,
                        defaultMessage = "Rest",
                        onChange = { onChange(block.copy(rest = it)) },
                    )
                    StageEditor(
                        title = "TRANSITION",
                        subtitle = "Move to the next station",
                        stage = block.transition,
                        defaultMessage = "Transition",
                        onChange = { onChange(block.copy(transition = it)) },
                    )

                    Text(
                        text = "Block total ${formatDuration(block.totalSeconds())}",
                        color = F3White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

private fun blockSummary(block: Block): String = buildString {
    if (block.exercises.isNotEmpty()) {
        append("${block.exercises.size} exercise")
        if (block.exercises.size > 1) append("s")
        append(" · ")
    }
    if (block.rounds > 1) append("${block.rounds} rounds · ")
    append(formatDuration(block.totalSeconds()))
}

@Composable
private fun RoundsRow(rounds: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "ROUNDS",
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { if (rounds > 1) onChange(rounds - 1) }) {
            Text("−", fontSize = 20.sp)
        }
        Text(
            text = "$rounds",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = { if (rounds < 99) onChange(rounds + 1) }) {
            Text("+", fontSize = 20.sp)
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = if (stage.seconds == 0) "" else stage.seconds.toString(),
                    onValueChange = { text ->
                        val digits = text.filter { it.isDigit() }.take(4)
                        onChange(stage.copy(seconds = digits.toIntOrNull() ?: 0))
                    },
                    label = { Text("Seconds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                )
                OutlinedTextField(
                    value = stage.message,
                    onValueChange = { onChange(stage.copy(message = it)) },
                    label = { Text("Spoken message") },
                    placeholder = { Text(defaultMessage, color = F3Gray) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
