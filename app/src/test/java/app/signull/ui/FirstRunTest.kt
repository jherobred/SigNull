package app.signull.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.signull.data.AppSettings
import app.signull.data.MapRepository
import app.signull.data.ThemeMode
import app.signull.data.db.SigNullDatabase
import app.signull.ui.navigation.SigNullApp
import app.signull.ui.onboarding.OnboardingScreen
import app.signull.ui.onboarding.SetupState
import app.signull.ui.theme.SigNullTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A new install starts blank: setup shows first, and nothing from an earlier install comes back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-xhdpi")
class FirstRunTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun freshSettingsOpenOnTheFirstSetupPage() {
        compose.setContent {
            SigNullTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                SigNullApp(settings = AppSettings(), onOnboardingDone = {})
            }
        }
        compose.onNodeWithText("See your exact signal").assertIsDisplayed()
    }

    @Test
    fun appStaysOutOfBackupSoReinstallsDoNotRestoreOldData() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun newDatabaseHasNoBuildingsFloorsOrSpots() = runTest {
        val db = SigNullDatabase.create(ApplicationProvider.getApplicationContext())
        try {
            val snapshot = MapRepository(db.mapDao()).snapshot.first()
            assertTrue(snapshot.buildings.isEmpty())
            assertTrue(snapshot.floors.isEmpty())
            assertTrue(snapshot.areas.isEmpty())
            assertTrue(snapshot.spots.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun allSetTextIsReadableInDarkMode() {
        val contrast = showCelebration(ThemeMode.DARK, shot = "17_celebration_dark")
        assertTrue("\"You're all set\" has contrast $contrast, which is unreadable", contrast >= 4.5)
    }

    @Test
    fun allSetTextIsReadableInLightMode() {
        val contrast = showCelebration(ThemeMode.LIGHT, shot = null)
        assertTrue("\"You're all set\" has contrast $contrast, which is unreadable", contrast >= 4.5)
    }

    /**
     * Finishes setup and returns the contrast between the "You're all set" text and its background.
     * The screen is hosted without a Surface around it, the way MainActivity hosts the app.
     */
    private fun showCelebration(mode: ThemeMode, shot: String?): Double {
        compose.setContent {
            SigNullTheme(themeMode = mode, dynamicColor = false) {
                OnboardingScreen(
                    setup = SetupState(location = true, notifications = true),
                    onRequestLocation = {},
                    onRequestNotifications = {},
                    onDone = {},
                    startPage = 3,
                )
            }
        }
        compose.onNodeWithText("Start exploring").performClick()
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        if (shot != null) compose.onRoot().captureRoboImage("src/test/screenshots/$shot.png")

        val bitmap = compose.onNodeWithText("You're all set").captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val background = pixels.groupBy { it }.maxByOrNull { it.value.size }!!.key
        val text = pixels.maxByOrNull { abs(luminance(it) - luminance(background)) }!!
        return contrastRatio(luminance(background), luminance(text))
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrastRatio(a: Double, b: Double): Double = (max(a, b) + 0.05) / (min(a, b) + 0.05)
}
