package app.karala

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Gerçek MainActivity ile uçtan uca: alt menüde gezinme ve ekran kalemi başlatma akışı. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun tabsNavigate() {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/30-app-home.png")
        // Seçili olmayan sekmeler ikonlarının açıklamasıyla bulunur; seçilince adı görünür.
        rule.onNodeWithContentDescription("Araçlar").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Periyodik tablo").assertExists()
        rule.onRoot().captureRoboImage("build/shots/31-app-tools.png")
        rule.onNodeWithContentDescription("Plan").performClick(); rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/33-app-plan.png")
        rule.onNodeWithText("Ders programı").assertExists()
        rule.onNodeWithContentDescription("Tekrar").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Bilgi kartları").assertExists()
        rule.onNodeWithContentDescription("Defter").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Defterler").assertExists()
        rule.onRoot().captureRoboImage("build/shots/32-app-notes.png")
        rule.onNodeWithContentDescription("Bugün").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Ekran kalemi").assertExists()
    }

    @Test fun startWithoutPermissionShowsGuide() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
        rule.waitForIdle()
        rule.onNodeWithText("İzin ver").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Bir izin gerekiyor").assertIsDisplayed()
        rule.onNodeWithText("Uygulama bilgilerini aç").assertIsDisplayed()
    }

    @Test fun startAsksHandOrStylusThenStartsService() {
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
        App.instance.prefs.askInputMode = true; App.instance.prefs.stylusOnly = false
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithText("Başlat").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Nasıl çizeceksin?").assertIsDisplayed()
        rule.onNodeWithText("Kalem").performClick()
        rule.onNodeWithText("Ekran kalemini başlat").performClick(); rule.waitForIdle()
        org.junit.Assert.assertTrue(App.instance.prefs.stylusOnly)
        val started = org.robolectric.Shadows.shadowOf(App.instance).nextStartedService
        org.junit.Assert.assertEquals(app.karala.overlay.OverlayService::class.java.name, started.component?.className)
        org.junit.Assert.assertEquals(app.karala.overlay.OverlayService.INPUT_STYLUS, started.getStringExtra(app.karala.overlay.OverlayService.EXTRA_INPUT))
    }
}
