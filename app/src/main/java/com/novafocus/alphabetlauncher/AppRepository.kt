package com.novafocus.alphabetlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: Bitmap
)

class AppRepository(private val context: Context) {
    suspend fun load(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                if (component.packageName == context.packageName) return@mapNotNull null
                val label = info.loadLabel(context.packageManager).toString().trim().ifEmpty { component.className }
                LaunchableApp(label, component.packageName, component, info.loadIcon(context.packageManager).toBitmap())
            }
            .distinctBy { it.component }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    fun launch(app: LaunchableApp): Result<Unit> = runCatching {
        context.startActivity(Intent(Intent.ACTION_MAIN).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Drawable.toBitmap(): Bitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        setBounds(0, 0, width, height)
        draw(Canvas(bitmap))
    }
}
