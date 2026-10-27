package dev.glasstv.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.glasstv.launcher.data.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real launcher UI at TV resolution (960x540dp @ xhdpi = 1920x1080) off-device.
 * App icons are plain placeholders, not brand art; on the TV the real installed icons are used.
 * Run: ./gradlew :app:testDebugUnitTest -PtvScreens --tests '*TvScreensTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvScreensTest {
    private val fakeApps = listOf(
        Triple("com.netflix.ninja", "Netflix", 0xFFB81D24.toInt()),
        Triple("com.google.android.youtube.tv", "YouTube", 0xFFD93025.toInt()),
        Triple("com.disney.disneyplus", "Disney+", 0xFF1A3A8F.toInt()),
        Triple("com.wbd.stream", "Max", 0xFF2A35C9.toInt()),
        Triple("com.amazon.amazonvideo.livingroom", "Prime Video", 0xFF1E9BD7.toInt()),
        Triple("com.spotify.tv.android", "Spotify", 0xFF1DB954.toInt()),
        Triple("com.plexapp.android", "Plex", 0xFFE5A00D.toInt()),
        Triple("tv.twitch.android.viewer", "Twitch", 0xFF8A4FE8.toInt()),
        Triple("com.android.tv.settings", "Settings", 0xFF6B6B6B.toInt()),
        Triple("com.google.android.music", "Music", 0xFFEF6C00.toInt()),
    )

    init { installFakeApps() } // before the activity rule launches

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<GlassApp>()
    private val out get() = System.getProperty("tvscreens.out") ?: "build/tvscreens"

    @Before fun setUp() = runBlocking {
        app.settingsRepo.setOnboarded()
        app.settingsRepo.setName("Jess")
        app.settingsRepo.recordLaunch("com.disney.disneyplus")
        app.settingsRepo.recordLaunch("com.netflix.ninja")
        app.appRepo.refresh()
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun screens() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false) // TVs have no touch mode
        compose.runOnUiThread { compose.activity.ui.qrOpen = false }
        shot("tv-home-light")
        // Regression guard: the home screen must open with a tile focused (D-pad users need a starting point).
        compose.onAllNodes(androidx.compose.ui.test.isFocused()).assertCountEquals(1)
        compose.runOnUiThread { compose.activity.ui.aiOpen = true }
        shot("tv-assistant-light")
        compose.runOnUiThread { compose.activity.ui.aiOpen = false; compose.activity.ui.settingsOpen = true }
        shot("tv-settings-light")
        compose.runOnUiThread { compose.activity.ui.settingsOpen = false; compose.activity.ui.qrOpen = true }
        shot("tv-phone-remote-light")
        compose.runOnUiThread { compose.activity.ui.qrOpen = false }
        runBlocking { app.settingsRepo.setTheme(ThemeMode.DARK) }
        shot("tv-home-dark")
        compose.runOnUiThread { compose.activity.ui.ambient = true }
        shot("tv-ambient-dark")
    }

    private fun installFakeApps() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val spm = shadowOf(ctx.packageManager)
        for ((pkg, label, color) in fakeApps) {
            val ai = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label; flags = ApplicationInfo.FLAG_INSTALLED; enabled = true }
            spm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = ai })
            val comp = ComponentName(pkg, "$pkg.Main")
            spm.addActivityIfNotPresent(comp)
            spm.addIntentFilterForActivity(comp, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) })
            val act = ActivityInfo().apply { packageName = pkg; name = comp.className; applicationInfo = ai; nonLocalizedLabel = label; exported = true; enabled = true }
            spm.addOrUpdateActivity(act)
            val icon = BitmapDrawable(ctx.resources, placeholderIcon(label, color))
            spm.setApplicationIcon(pkg, icon)
            spm.addActivityIcon(comp, icon)
        }
    }

    private fun placeholderIcon(label: String, color: Int): Bitmap {
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawRoundRect(RectF(0f, 0f, 192f, 192f), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawText(label.take(1), 96f, 128f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt(); textSize = 100f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        })
        return b
    }
}
