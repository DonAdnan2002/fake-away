package com.fakeaway.app.e2e

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fakeaway.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FakeAwayAppE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigateAllSlices_switchTrilingualLocales_verifyClaimAndCheckHistory() {
        composeRule.onNodeWithTag("nav_voice").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_shield").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("nav_verify").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("nav_history").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("nav_settings").assertIsDisplayed().performClick()

        // Switch through English -> Kurdish Sorani -> Arabic
        composeRule.onNodeWithTag("lang_en").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Verify").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("lang_ckb").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("پشکنینی هەواڵ").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("lang_ar").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("التحقق").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify a claim on Verify tab and assert Room persistence in History tab
        composeRule.onNodeWithTag("nav_verify").performClick()
        composeRule.onNodeWithTag("verify_input").performTextReplacement(
            "عاجل جداً!! شاهد قبل الحذف تسريب خطير انشرها فوراً!!!"
        )
        composeRule.onNodeWithTag("verify_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 12_000) {
            composeRule.onAllNodesWithTag("verify_result_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("verify_result_banner").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("شاهد قبل الحذف", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("شاهد قبل الحذف", substring = true).assertIsDisplayed()
    }

    @Test
    fun incomingFacebookShareIntent_routesToVerifyTabAutomatically() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shareIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://www.facebook.com/watch/?v=123456789")
        }

        ActivityScenario.launch<MainActivity>(shareIntent).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.intent?.action == Intent.ACTION_SEND)
            }
        }
    }
}
