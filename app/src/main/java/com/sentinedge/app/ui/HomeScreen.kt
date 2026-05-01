package com.sentinedge.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Purple  = Color(0xFF7C6FFF)
private val Dark1   = Color(0xFF0A0A1A)
private val Dark2   = Color(0xFF13132A)
private val Dark3   = Color(0xFF1A1A3A)

@Composable
fun HomeScreen(
    onMediaSelected: (Uri) -> Unit,
    onLiveCamera: () -> Unit,
    onToggleBackgroundProtection: (Boolean) -> Unit,
    isBackgroundProtectionActive: Boolean = false,
    downloadProgress: Int? = null,
) {
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onMediaSelected(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Dark1, Dark2, Dark3))),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(64.dp))
            
            // ... (Logo and Title text remains same)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Purple.copy(alpha = 0.4f), Color.Transparent)))
                    .border(2.dp, Purple.copy(alpha = 0.6f), CircleShape),
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Purple, modifier = Modifier.size(40.dp))
            }

            Spacer(Modifier.height(20.dp))

            Text("SentinEdge", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "Multi-modal Deepfake Detection",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            // Download Progress (New)
            if (downloadProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI Reasoning Model", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("$downloadProgress%", color = Purple, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = Purple,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Downloading 2.8GB model for smart analysis...",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            Text(
                "SELECT PROTECTION MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.35f),
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(14.dp))

            // Mode 1: Media Upload (Image or Video)
            ModeCard(
                icon = Icons.Filled.FileUpload,
                title = "Upload Image/Video",
                subtitle = "Analyze static images or uploaded video files for deepfake artifacts.",
                gradient = Brush.linearGradient(listOf(Color(0xFF3D2FA0), Color(0xFF6C63FF))),
                onClick = { mediaPicker.launch("*/*") },
            )

            Spacer(Modifier.height(14.dp))

            // Mode 2: Video Call / Live Camera
            ModeCard(
                icon = Icons.Filled.CameraAlt,
                title = "Video Call Protection",
                subtitle = "Real-time liveness check: Analyzes blinking and natural lip movements.",
                gradient = Brush.linearGradient(listOf(Color(0xFF0D3B5E), Color(0xFF0277BD))),
                onClick = onLiveCamera,
            )

            Spacer(Modifier.height(14.dp))

            // Mode 3: NEW Background Protection
            ModeCard(
                icon = Icons.Filled.Shield,
                title = "Background Protection",
                subtitle = "Run SentinEdge in the background while using Zoom or WhatsApp.",
                gradient = if (isBackgroundProtectionActive) 
                    Brush.linearGradient(listOf(Color(0xFF1B5E20), Color(0xFF4CAF50))) 
                    else Brush.linearGradient(listOf(Color(0xFF424242), Color(0xFF212121))),
                onClick = { onToggleBackgroundProtection(!isBackgroundProtectionActive) },
                badge = if (isBackgroundProtectionActive) "ACTIVE" else "START"
            )

            Spacer(Modifier.height(36.dp))

            // Footer badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(bottom = 36.dp),
            ) {
                FooterBadge("Qualcomm NPU")
                FooterBadge("Liveness Check")
                FooterBadge("Metadata Analysis")
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: Brush,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (badge != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(badge, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 1.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun FooterBadge(label: String) {
    Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
}
