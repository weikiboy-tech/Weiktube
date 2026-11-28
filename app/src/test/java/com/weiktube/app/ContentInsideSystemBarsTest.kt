package com.weiktube.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContentInsideSystemBarsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun foregroundReturnReservesBarsBeforeVisibilityCatchesUp() {
        var pip by mutableStateOf(false)
        var landscapeMotion by mutableStateOf(false)
        var width by mutableStateOf(120.dp)
        var top by mutableIntStateOf(10)
        var bottom by mutableIntStateOf(20)
        var measured = emptyList<Int>()
        compose.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(width, 160.dp)) {
                ContentInsideSystemBars(pip, landscapeMotion, WindowInsets(0, top, 0, bottom), WindowInsets(0, 10, 0, 20)) {
                    Spacer(Modifier.fillMaxSize().onGloballyPositioned {
                        val offset = it.positionInRoot()
                        measured = listOf(offset.x.toInt(), offset.y.toInt(), it.size.width,
                            with(density) { 160.dp.roundToPx() } - it.size.height - offset.y.toInt())
                    })
                }
            }
        }
        fun check(expectedWidth: Dp, upper: Int, lower: Int) = compose.runOnIdle {
            assertEquals(listOf(0,upper,with(compose.density) { expectedWidth.roundToPx() },lower),measured)
        }
        check(120.dp, 10, 20)
        compose.runOnIdle { top = 0; bottom = 0; landscapeMotion = true }
        check(120.dp, 10, 20)
        compose.runOnIdle { width = 200.dp }
        check(200.dp, 0, 0)
        compose.runOnIdle { width = 120.dp; pip = true }
        check(120.dp, 0, 0)
        compose.runOnIdle { pip = false; landscapeMotion = false; bottom = 60 }
        check(120.dp, 10, 60)
        compose.runOnIdle { top = 10; bottom = 20 }
        check(120.dp, 10, 20)
    }
}
