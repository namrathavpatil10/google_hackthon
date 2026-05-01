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

private val Purple = Color(0xFF7C6FFF)
private val Dark1  = Color(0xFF0A0A1A)
private val Dark2  = Color(0xFF13132A)
private val Dark3  = Color(0xFF1A1A3A)

@Composable
fun HomeScreen(
    onMediaSelected: (Uri) -> Unit,
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

            // App shield logo
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

            // Gemma AI model download progress banner (shown while model is downloading)
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
                        "Downloading Gemma 4 2B model (~2GB) for AI reasoning...",
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

            // Active: upload images or videos for deepfake analysis
            ModeCard(
                icon = Icons.Filled.FileUpload,
                title = "Upload Image/Video",
                subtitle = "Analyze static images or uploaded video files for deepfake artifacts.",
                gradient = Brush.linearGradient(listOf(Color(0xFF3D2FA0), Color(0xFF6C63FF))),
                onClick = { mediaPicker.launch("*/*") },
            )

            Spacer(Modifier.height(14.dp))

            // Coming soon: real-time video call liveness analysis
            ComingSoonCard(
                icon = Icons.Filled.CameraAlt,
                title = "Video Call Protection",
                subtitle = "Real-time liveness check during Zoom, Meet & WhatsApp calls.",
            )

            Spacer(Modifier.height(14.dp))

            // Coming soon: silent background scanning while using other apps
            ComingSoonCard(
                icon = Icons.Filled.Shield,
                title = "Background Protection",
                subtitle = "Run SentinEdge silently in the background while using other apps.",
            )

            Spacer(Modifier.height(36.dp))

            // Capability footer badges
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

// Active tappable mode card with gradient background
@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: Brush,
    onClick: () -> Unit,
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
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), lineHeight = 17.sp)
            }
        }
    }
}

// Dimmed non-interactive card for features not yet implemented
@Composable
private fun ComingSoonCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f))
                    // Purple "SOON" pill badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Purple.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text("SOON", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Purple, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f), lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun FooterBadge(label: String) {
    Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f), fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
}
