package org.hdhmc.saki.presentation.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerChromeAdaptiveLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stackedHeaderPreservesMoreTouchTargetAtOneHundredSixtyDp() {
        composeRule.setContent {
            Box(Modifier.width(160.dp)) {
                AdaptiveNowPlayingHeaderLayout(
                    stacked = true,
                    modifier = Modifier.fillMaxWidth(),
                    title = { modifier ->
                        Box(
                            modifier
                                .height(24.dp)
                                .testTag(TITLE_TAG),
                        )
                    },
                    status = { modifier ->
                        Box(
                            modifier
                                .size(width = 120.dp, height = 32.dp)
                                .testTag(STATUS_TAG),
                        )
                    },
                    more = { modifier ->
                        Box(
                            modifier
                                .size(48.dp)
                                .clickable {}
                                .testTag(MORE_TAG),
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag(TITLE_TAG).assertWidthIsEqualTo(112.dp)
        composeRule.onNodeWithTag(MORE_TAG)
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .assertHasClickAction()
        composeRule.onNodeWithTag(STATUS_TAG).assertWidthIsEqualTo(120.dp)
    }

    @Test
    fun stackedIdentityControlsPreserveAllTouchTargetsAtOneHundredSixtyDp() {
        composeRule.setContent {
            Box(Modifier.width(160.dp)) {
                AdaptiveNowPlayingIdentityControlsLayout(
                    stacked = true,
                    modifier = Modifier.fillMaxWidth(),
                    identity = { modifier ->
                        Box(
                            modifier
                                .height(40.dp)
                                .testTag(IDENTITY_TAG),
                        )
                    },
                    controls = { modifier ->
                        Row(
                            modifier = modifier,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            repeat(3) { index ->
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clickable {}
                                        .testTag("$CONTROL_TAG_PREFIX$index"),
                                )
                            }
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag(IDENTITY_TAG).assertWidthIsEqualTo(160.dp)
        repeat(3) { index ->
            composeRule.onNodeWithTag("$CONTROL_TAG_PREFIX$index")
                .assertWidthIsEqualTo(48.dp)
                .assertHeightIsEqualTo(48.dp)
                .assertHasClickAction()
        }
    }

    private companion object {
        const val TITLE_TAG = "narrow-header-title"
        const val STATUS_TAG = "narrow-header-status"
        const val MORE_TAG = "narrow-header-more"
        const val IDENTITY_TAG = "narrow-track-identity"
        const val CONTROL_TAG_PREFIX = "narrow-secondary-control-"
    }
}
