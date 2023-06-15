package exchange.dydx.carteraexample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

object ModalTransitionDialog {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    fun ModalTransitionDialog(
        onDismissRequest: () -> Unit,
        dismissOnBackPress: Boolean = true,
        content: @Composable (ModalTransitionDialogHelper) -> Unit
    ) {

        val onCloseSharedFlow: MutableSharedFlow<Unit> = remember { MutableSharedFlow() }
        val coroutineScope: CoroutineScope = rememberCoroutineScope()
        val animateContentBackTrigger = remember { mutableStateOf(false) }
        val mutex = remember { Mutex(true) }

        LaunchedEffect(key1 = Unit) {
            launch {
                //delay(DIALOG_BUILD_TIME)
                animateContentBackTrigger.value = true
            }
            launch {
                onCloseSharedFlow.asSharedFlow().collectLatest { startDismissWithExitAnimation(animateContentBackTrigger, onDismissRequest) }
            }
        }

        Dialog(
            onDismissRequest = { coroutineScope.launch { startDismissWithExitAnimation(animateContentBackTrigger, onDismissRequest) } },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissOnBackPress, dismissOnClickOutside = false)
        ) {
            LaunchedEffect(key1 = Unit) {
                if (mutex.isLocked) mutex.unlock()
            }

            AnimatedModalBottomSheetTransition(visible = animateContentBackTrigger.value) {
                content(ModalTransitionDialogHelper(coroutineScope, onCloseSharedFlow))
            }
        }
    }

    private suspend fun startDismissWithExitAnimation(
        animateContentBackTrigger: MutableState<Boolean>,
        onDismissRequest: () -> Unit
    ) {
        animateContentBackTrigger.value = false
        delay(ANIMATION_TIME)
        onDismissRequest()
    }

    /**
     * Helper class that can be used inside the content scope from
     * composables that implement the [ModalTransitionDialog] to hide
     * the [Dialog] with a modal transition animation
     */
    class ModalTransitionDialogHelper(
        private val coroutineScope: CoroutineScope,
        private val onCloseFlow: MutableSharedFlow<Unit>
    ) {
        fun triggerAnimatedClose() {
            coroutineScope.launch {
                onCloseFlow.emit(Unit)
            }
        }
    }

    internal const val ANIMATION_TIME = 500L

    @Composable
    internal fun AnimatedModalBottomSheetTransition(
        visible: Boolean,
        content: @Composable AnimatedVisibilityScope.() -> Unit
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = tween(ANIMATION_TIME.toInt()),
                initialOffsetY = { fullHeight -> fullHeight }
            ),
            exit = slideOutVertically(
                animationSpec = tween(ANIMATION_TIME.toInt()),
                targetOffsetY = { fullHeight -> fullHeight }
            ),
            content = content
        )
    }
}

