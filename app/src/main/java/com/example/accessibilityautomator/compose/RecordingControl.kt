package com.example.accessibilityautomator.compose

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.accessibilityautomator.automation.AutomationAccessibilityService
import com.example.accessibilityautomator.automation.RecordingManager
import com.example.accessibilityautomator.automation.ReplayManager

@Composable
fun RecordingControls() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isServiceConnected by remember { mutableStateOf(AutomationAccessibilityService.isServiceConnected()) }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var savedFile by rememberSaveable { mutableStateOf<String?>(null) }

    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    // Observe lifecycle to update service connection status when user returns from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceConnected = AutomationAccessibilityService.isServiceConnected()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Button Clicked") },
            text = {
                Text(
                    if (firstName.isNotBlank() || lastName.isNotBlank()) {
                        "Login button was clicked successfully for $firstName $lastName!"
                    } else {
                        "Login button was clicked successfully!"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!isServiceConnected) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Accessibility Service Disabled",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To record and replay interactions, enable Accessibility Service in Android Settings.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Enable Service in Settings")
                    }
                }
            }
        } else {
            Text(
                text = "✓ Accessibility Service Connected",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                RecordingManager.startRecording()
                isRecording = true
                savedFile = null
            },
            enabled = isServiceConnected && !isRecording,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Start Recording")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val file = RecordingManager.stopAndSave(context)
                isRecording = false
                savedFile = file?.absolutePath
            },
            enabled = isRecording,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Stop and Save")
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            if (isRecording) "Status: Recording in progress..."
            else "Status: Recording stopped"
        )

        savedFile?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Saved: ${it.substringAfterLast('/')}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                savedFile?.let { filePath ->
                    ReplayManager.play(
                        context = context,
                        filePath = filePath
                    )
                }
            },
            enabled = isServiceConnected && savedFile != null && !isRecording,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Play Last Recording")
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "First Name") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Last Name") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }
    }
}