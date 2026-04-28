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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
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
private val Blue    = Color(0xFF4FC3F7)
private val Red     = Color(0xFFFF5252)
private val Dark1   = Color(0xFF0A0A1A)
private val Dark2   = Color(0xFF13132A)
private val Dark3   = Color(0xFF1A1A3A)

@Composable
fun HomeScreen(
    onVideoSelected: (Uri) -> Unit,
    onLiveCamera: () -> Unit,
    onProtectLiveCalls: () -> Unit,
) {
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onVideoSelected(it) } }

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

            // Logo
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
                "AI-powered deepfake detection",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(48.dp))

            Text(
                "CHOOSE MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.35f),
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(14.dp))

            // Mode 1: Video upload
            ModeCard(
                icon = Icons.Filled.VideoFile,
                title = "Analyze Video",
                subtitle = "Upload an MP4 and scan every frame for deepfake artifacts",
                gradient = Brush.linearGradient(listOf(Color(0xFF3D2FA0), Color(0xFF6C63FF))),
                onClick = { videoPicker.launch("video/*") },
            )

            Spacer(Modifier.height(14.dp))

            // Mode 2: Live camera
            ModeCard(
                icon = Icons.Filled.CameraAlt,
                title = "Live Camera",
                subtitle = "Real-time analysis on your front camera — instant verdict",
                gradient = Brush.linearGradient(listOf(Color(0xFF0D3B5E), Color(0xFF0277BD))),
                onClick = onLiveCamera,
            )

            Spacer(Modifier.height(14.dp))

            // Mode 3: Protect live calls (MediaProjection overlay)
            ModeCard(
                icon = Icons.Filled.Visibility,
                title = "Protect Live Calls",
                subtitle = "Monitors WhatsApp, Meet & Zoom. Floating overlay appears on top of your call.",
                gradient = Brush.linearGradient(listOf(Color(0xFF7F0000), Color(0xFFD32F2F))),
                badge = "NEW",
                onClick = onProtectLiveCalls,
            )

            Spacer(Modifier.height(36.dp))

            // Footer badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(bottom = 36.dp),
            ) {
                FooterBadge("On-device")
                FooterBadge("Private")
                FooterBadge("Real-time")
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
