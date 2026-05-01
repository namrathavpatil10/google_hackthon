package com.sentinedge.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinedge.app.AnalysisState

private val BgGradient = Brush.verticalGradient(listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E)))

@Composable
fun MainScreen(
    onMediaSelected: (Uri) -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    isBackgroundActive: Boolean,
) {
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onMediaSelected(it) } }

    Box(
        modifier = Modifier.fillMaxSize().background(BgGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        ) {
            Text("SentinEdge", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("AI Forensic Analysis", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))

            Spacer(Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DNA SCAN", color = Color(0xFF7C6FFF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Deep Pixel Analysis", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Upload content to perform Error Level Analysis (ELA) and scan for generative signatures.",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { mediaPicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
                    ) {
                        Text("SELECT FILE", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = if (isBackgroundActive) Color(0xFF1B5E20) else Color(0xFF2A2A4A)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Background Guard", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("YouTube & Video Call Protection", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = isBackgroundActive,
                        onCheckedChange = onToggleBackground,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
                    )
                }
            }
        }
    }
}
