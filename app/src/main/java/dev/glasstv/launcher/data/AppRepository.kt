package dev.glasstv.launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TvApp(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val isLeanback: Boolean,
)

/** Real, system-provided artwork: the app's TV banner if it ships one, otherwise its launcher icon. */
data class AppArt(val image: ImageBitmap, val isBanner: Boolean)

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps

    private val artCache = object : LruCache<String, AppArt>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppArt) = value.image.width * value.image.height * 4
    }

    init {
        refresh()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                i.data?.schemeSpecificPart?.let { artCache.remove(it) }
                refresh()
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) { _apps.value = query() }
    }

    private fun query(): List<TvApp> {
        val self = context.packageName
        val byPkg = LinkedHashMap<String, TvApp>()
        fun collect(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == self || byPkg.containsKey(ai.packageName)) continue
                byPkg[ai.packageName] = TvApp(
                    packageName = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    component = ComponentName(ai.packageName, ai.name),
                    isLeanback = leanback,
                )
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, true)
        // Phone-only apps are still launchable on TV; list them after TV-native ones.
        collect(Intent.CATEGORY_LAUNCHER, false)
        return byPkg.values.sortedWith(compareByDescending<TvApp> { it.isLeanback }.thenBy { it.label.lowercase() })
    }

    suspend fun art(app: TvApp): AppArt? {
        artCache.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val info = pm.getActivityInfo(app.component, 0)
                val banner = info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm)
                val art = if (banner != null) {
                    AppArt(banner.toBitmap(320, 180).asImageBitmap(), true)
                } else {
                    AppArt(info.loadIcon(pm).toBitmap(192, 192).asImageBitmap(), false)
                }
                artCache.put(app.packageName, art)
                art
            }.getOrNull()
        }
    }

    /** Small PNG of the launcher icon (not the banner) as a data URL, for the phone's shortcut grid. */
    private val iconUrls = LruCache<String, String>(64)
    fun iconDataUrl(app: TvApp): String? {
        iconUrls.get(app.packageName)?.let { return it }
        return runCatching {
            val bmp = pm.getActivityInfo(app.component, 0).loadIcon(pm).toBitmap(96, 96)
            val bytes = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            ("data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                .also { iconUrls.put(app.packageName, it) }
        }.getOrNull()
    }

    fun launch(app: TvApp): Boolean = launch(app.packageName)

    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun isInstalled(pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    /** First-run favourites: whichever of the big streaming apps are installed, in this order. */
    fun defaultPins(): List<String> = DEFAULT_PINS.mapNotNull { candidates -> candidates.firstOrNull(::isInstalled) }

    companion object {
        val DEFAULT_PINS = listOf(
            listOf("com.netflix.ninja"),
            listOf("com.google.android.youtube.tv", "com.google.android.youtube.tvunplugged"),
            listOf("com.disney.disneyplus"),
            listOf("com.wbd.stream", "com.hbo.hbonow", "com.hbo.max.android.tv"),
            listOf("com.amazon.amazonvideo.livingroom"),
            listOf("com.spotify.tv.android"),
            listOf("com.plexapp.android"),
            listOf("tv.twitch.android.viewer", "tv.twitch.android.app"),
        )
    }
}

private fun Drawable.toBitmap(w: Int, h: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null && bitmap.width <= w * 2) return bitmap
    val iw = intrinsicWidth.takeIf { it > 0 } ?: w
    val ih = intrinsicHeight.takeIf { it > 0 } ?: h
    val scale = minOf(w.toFloat() / iw, h.toFloat() / ih, 2f)
    val bw = (iw * scale).toInt().coerceAtLeast(1)
    val bh = (ih * scale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, bw, bh)
    draw(Canvas(bmp))
    return bmp
}
