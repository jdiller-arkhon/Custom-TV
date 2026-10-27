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
/** [tint] is the art's average colour, used to mood-light the room when the app is focused. */
data class AppArt(val image: ImageBitmap, val isBanner: Boolean, val tint: Int = 0)

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
                // The app's own published artwork: its TV banner (activity, then app level) or its launcher icon.
                val banner = runCatching { pm.getActivityBanner(app.component) }.getOrNull()
                    ?: runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull()
                val art = if (banner != null) {
                    banner.toBitmap(320, 180).let { AppArt(it.asImageBitmap(), true, averageColor(it)) }
                } else {
                    pm.getActivityIcon(app.component).toBitmap(192, 192).let { AppArt(it.asImageBitmap(), false, averageColor(it)) }
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
            val bmp = pm.getActivityIcon(app.component).toBitmap(96, 96)
            val bytes = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            ("data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                .also { iconUrls.put(app.packageName, it) }
        }.getOrNull()
    }

    /** Called after a successful launch (records recents). */
    var onLaunched: ((String) -> Unit)? = null

    fun launch(app: TvApp): Boolean = launch(app.packageName)

    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess.also { if (it) onLaunched?.invoke(packageName) }
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

/** Average of opaque, non-near-white/black pixels: the colour people perceive as the brand colour. */
private fun averageColor(b: Bitmap): Int {
    val s = Bitmap.createScaledBitmap(b, 16, 16, true)
    var r = 0L; var g = 0L; var bl = 0L; var n = 0
    for (y in 0 until 16) for (x in 0 until 16) {
        val p = s.getPixel(x, y)
        if ((p ushr 24) < 200) continue
        val pr = (p shr 16) and 255; val pg = (p shr 8) and 255; val pb = p and 255
        val mx = maxOf(pr, pg, pb); val mn = minOf(pr, pg, pb)
        if (mx - mn < 30) continue // skip greys/whites/blacks
        r += pr; g += pg; bl += pb; n++
    }
    if (n == 0) return 0
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
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
