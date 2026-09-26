package com.novafocus.alphabetlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Immutable
data class LaunchableApp(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap
)

@Immutable
data class LauncherPersonalization(
    val favourites: Set<String> = emptySet(),
    val recent: List<String> = emptyList()
)

class AppRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("launcher_personalization", Context.MODE_PRIVATE)

    suspend fun load(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val iconSize = (48f * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        context.packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { info ->
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                if (component.packageName == context.packageName) return@mapNotNull null
                val label = info.loadLabel(context.packageManager).toString().trim().ifEmpty { component.className }
                LaunchableApp(label, component.packageName, component, info.loadIcon(context.packageManager).toBitmap(iconSize).asImageBitmap())
            }
            .distinctBy { it.component }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    fun launch(app: LaunchableApp): Result<Unit> = runCatching {
        context.startActivity(Intent(Intent.ACTION_MAIN).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun personalization(): LauncherPersonalization = LauncherPersonalization(
        favourites = preferences.getStringSet(FavouritesKey, emptySet()).orEmpty().toSet(),
        recent = preferences.getString(RecentKey, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(MaxRecentApps)
            ?.toList()
            .orEmpty()
    )

    fun setFavourite(app: LaunchableApp, favourite: Boolean): LauncherPersonalization {
        val key = app.component.flattenToString()
        val updated = personalization().favourites.toMutableSet().apply {
            if (favourite) add(key) else remove(key)
        }
        preferences.edit { putStringSet(FavouritesKey, updated) }
        return personalization().copy(favourites = updated.toSet())
    }

    fun recordLaunch(app: LaunchableApp): LauncherPersonalization {
        val key = app.component.flattenToString()
        val updated = buildList {
            add(key)
            addAll(personalization().recent.filterNot { it == key })
        }.take(MaxRecentApps)
        preferences.edit { putString(RecentKey, updated.joinToString("\n")) }
        return personalization().copy(recent = updated)
    }

    private companion object {
        const val FavouritesKey = "favourite_components"
        const val RecentKey = "recent_components"
        const val MaxRecentApps = 20
    }
}

private fun Drawable.toBitmap(size: Int): Bitmap {
    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: size
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: size
    val scale = minOf(size.toFloat() / sourceWidth, size.toFloat() / sourceHeight)
    val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    val left = (size - width) / 2
    val top = (size - height) / 2
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        setBounds(left, top, left + width, top + height)
        draw(Canvas(bitmap))
    }
}
