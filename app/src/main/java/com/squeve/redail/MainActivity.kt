package com.squeve.redail

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.squeve.redail.model.RedialJob
import com.squeve.redail.service.RedialService

/** Bare-bones screen to prove the loop works. Real UI comes after the engine is verified on a device. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var numbers by remember { mutableStateOf("") }
                var attempts by remember { mutableStateOf("3") }
                var gapSec by remember { mutableStateOf("30") }
                var ringSec by remember { mutableStateOf("25") }

                val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

                Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Squeve Redail", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(numbers, { numbers = it }, label = { Text("Numbers (one per line)") },
                        modifier = Modifier.fillMaxWidth().height(160.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(attempts, { attempts = it }, label = { Text("Attempts") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(gapSec, { gapSec = it }, label = { Text("Gap (s)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(ringSec, { ringSec = it }, label = { Text("Ring (s)") }, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = {
                        perms.launch(arrayOf(
                            Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ))
                    }) { Text("Grant permissions") }
                    Button(onClick = {
                        RedialService.pendingQueue = numbers.lines().map { it.trim() }.filter { it.isNotEmpty() }.map {
                            RedialJob(
                                number = it,
                                attempts = attempts.toIntOrNull() ?: 3,
                                gapBetweenAttemptsMs = (gapSec.toLongOrNull() ?: 30) * 1000,
                                ringTimeoutMs = (ringSec.toLongOrNull() ?: 25) * 1000,
                            )
                        }
                        startForegroundService(Intent(this@MainActivity, RedialService::class.java).setAction(RedialService.ACTION_START))
                    }) { Text("Start") }
                }
            }
        }
    }
}
