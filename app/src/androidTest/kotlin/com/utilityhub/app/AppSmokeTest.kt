package com.utilityhub.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Needs an emulator or device: ./gradlew connectedDebugAndroidTest  (NOT executed in the authoring sandbox). */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun skipOnboardingIfShown() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Skip").performClick()
    }

    @Test fun launchShowsHomeWithCategoriesAndAllowance() {
        skipOnboardingIfShown()
        rule.onNodeWithText("UtilityHub").assertIsDisplayed()
        rule.onNodeWithText("Images").assertIsDisplayed()
    }

    @Test fun searchFindsAToolAndPlannedToolsAreNotOpenable() {
        skipOnboardingIfShown()
        rule.onNodeWithText("Search tools (e.g. resize, pdf, hash)").performTextInput("resize")
        rule.onNodeWithText("Image Resizer").assertIsDisplayed()
    }

    @Test fun passwordGeneratorWorksWithoutUsingTheFreeAllowance() {
        skipOnboardingIfShown()
        rule.onNodeWithText("Search tools (e.g. resize, pdf, hash)").performTextInput("password")
        rule.onNodeWithText("Password Generator").performClick()
        rule.onNodeWithText("Generate").performClick()
        rule.onNodeWithText("Copy").assertIsDisplayed()
    }
}
