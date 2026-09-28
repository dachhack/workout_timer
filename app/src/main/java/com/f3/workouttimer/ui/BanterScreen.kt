package com.f3.workouttimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f3.workouttimer.data.BanterRepository
import com.f3.workouttimer.model.CustomReply
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3Gray
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BanterRepository.get(context) }
    val replies by repo.replies.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<CustomReply?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("VOICE REPLIES", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
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
                onClick = { editing = CustomReply() },
                containerColor = F3White,
                contentColor = F3Black,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New reply")
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
                    "Ask something mid-workout and the app answers. Turn the mic on " +
                        "during a run, then say the trigger. It doesn't need the exact " +
                        "words — most of them is enough.",
                    color = F3Gray,
                    fontSize = 13.sp,
                )
            }
            if (replies.isEmpty()) {
                item {
                    Text(
                        text = "Nothing to say yet.\nHit + to teach it a line.",
                        color = F3Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    )
                }
            }
            items(replies, key = { it.id }) { reply ->
                Card(
                    onClick = { editing = reply },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "\"${reply.trigger}\"",
                                color = F3Gray,
                                fontSize = 13.sp,
                            )
                            Text(
                                reply.reply,
                                fontWeight = FontWeight.Bold,
                                color = F3White,
                            )
                        }
                        IconButton(onClick = { scope.launch { repo.delete(reply.id) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = F3Gray)
                        }
                    }
                }
            }
        }
    }

    editing?.let { reply ->
        ReplyEditor(
            reply = reply,
            onDismiss = { editing = null },
            onSave = {
                scope.launch { repo.save(it) }
                editing = null
            },
        )
    }
}

@Composable
private fun ReplyEditor(
    reply: CustomReply,
    onDismiss: () -> Unit,
    onSave: (CustomReply) -> Unit,
) {
    var draft by remember(reply.id) { mutableStateOf(reply) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Call and response") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.trigger,
                    onValueChange = { draft = draft.copy(trigger = it) },
                    label = { Text("When someone says") },
                    placeholder = {
                        Text("Who has the best pushup form?", color = F3Gray)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.reply,
                    onValueChange = { draft = draft.copy(reply = it) },
                    label = { Text("The app says") },
                    placeholder = { Text("It's Sprocket. Hands down.", color = F3Gray) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Keep the trigger to a few distinctive words — a long sentence is " +
                        "harder to hear over a parking lot.",
                    color = F3Gray,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(draft) },
                enabled = draft.isUsable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = F3White,
                    contentColor = F3Black,
                ),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
