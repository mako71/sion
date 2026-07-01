package com.fuyi.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuyi.app.service.ConsentActivity
import com.fuyi.app.service.FloatingWindowService
import com.fuyi.app.service.TranslationService
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FuYiTheme { SetupScreen() }
        }
    }
}

private val Surface0 = Color(0xFF0A0A0A)
private val Surface1 = Color(0xFF141414)
private val Accent = Color(0xFF00D2A0)
private val TextWhite = Color(0xFFF5F5F5)
private val TextGray = Color(0xFF8A8A8A)
private val Danger = Color(0xFFFF5C5C)

@Composable
fun FuYiTheme(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Surface0) { content() }
}

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(canDrawOverlays()) }
    var isRunning by remember { mutableStateOf(false) }
    var hasModel by remember { mutableStateOf(checkModel()) }

    LaunchedEffect(Unit) {
        hasOverlayPermission = canDrawOverlays()
        hasModel = checkModel()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, Color(0xFF007A5E)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Translate, null, tint = Surface0, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("浮译", color = TextWhite, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("日语 → 中文 · 悬浮实时翻译", color = TextGray, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("看日语视频时，翻译自动浮现在屏幕上方", color = TextGray.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(48.dp))

        StatusRow("悬浮窗权限", status = hasOverlayPermission, statusText = if (hasOverlayPermission) "已开启" else "未开启",
            onAction = if (!hasOverlayPermission) {
                {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")))
                }
            } else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusRow("Vosk 日语模型", status = hasModel, statusText = if (hasModel) "已就绪" else "未安装",
            onAction = if (!hasModel) {
                {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://alphacephei.com/vosk/models")))
                }
            } else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusRow("翻译引擎", statusText = "ML Kit 离线日→中", status = true)
        Spacer(modifier = Modifier.height(52.dp))

        val enabled = hasOverlayPermission && hasModel
        Button(
            onClick = {
                if (isRunning) {
                    TranslationService.stop(context)
                    FloatingWindowService.hide(context)
                    isRunning = false
                } else {
                    if (!hasOverlayPermission) return@Button
                    ConsentActivity.request(context)
                    isRunning = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Danger else Accent),
            enabled = enabled
        ) {
            Icon(if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(if (isRunning) "停止翻译" else "开始翻译", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!hasModel) {
            Text(
                "请先下载日语模型 (vosk-model-small-ja-0.22)\n解压后放入 /sdcard/fuyi/model/",
                color = TextGray.copy(alpha = 0.7f), fontSize = 12.sp,
                textAlign = TextAlign.Center, lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun StatusRow(label: String, statusText: String? = null, status: Boolean, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface1)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.CheckCircle, null,
                tint = if (status) Accent else TextGray, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(label, color = TextWhite, fontSize = 15.sp)
                if (statusText != null) Text(statusText, color = TextGray, fontSize = 12.sp)
            }
        }
        if (onAction != null) {
            Icon(Icons.Rounded.OpenInNew, "操作", tint = Accent, modifier = Modifier.size(20.dp))
        }
    }
}

fun canDrawOverlays(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this as? android.app.Activity) else true
}

fun checkModel(): Boolean {
    val extDir = File("/sdcard/fuyi/model")
    return extDir.exists() && File(extDir, "am").exists()
}
