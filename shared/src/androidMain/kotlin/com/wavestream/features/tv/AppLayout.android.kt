package com.wavestream.features.tv

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private var cachedLayout: AppLayout? = null

actual fun detectLayout(): AppLayout {
    return cachedLayout ?: AppLayout.PHONE
}

fun detectLayout(context: Context): AppLayout {
    cachedLayout?.let { return it }
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val model = Build.MODEL.lowercase()
    val isAutoTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        || Build.MODEL.contains("AFT")
        || model.contains("firestick")
        || model.contains("fire tv")
        || model.contains("chromecast")
    val layout = if (isAutoTv) AppLayout.TV else AppLayout.PHONE
    cachedLayout = layout
    return layout
}

@Composable
fun rememberLayout(): AppLayout {
    val context = LocalContext.current
    return remember { detectLayout(context) }
}
