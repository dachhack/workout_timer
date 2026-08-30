package com.f3.workouttimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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

    val draft = initial.copy(
        name = name.ifBlank { "Beatdown" },
        rounds = rounds,
        work = work,
        rest = rest,
        transition = transition,
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
