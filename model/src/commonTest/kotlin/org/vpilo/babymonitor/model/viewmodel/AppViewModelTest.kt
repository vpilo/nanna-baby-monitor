package org.vpilo.babymonitor.model.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @Test
    fun subscriptionsStopWhenTheLastStateCollectorLeaves() =
        runTest {
            val viewModel = TestViewModel(backgroundScope)
            val first = backgroundScope.launch { viewModel.stateFlow.collect {} }
            val second = backgroundScope.launch { viewModel.stateFlow.collect {} }
            runCurrent()
            assertEquals(1, viewModel.subscribed.subscriptionCount.value)
            assertEquals(1, viewModel.collected.subscriptionCount.value)

            first.cancel()
            runCurrent()
            assertEquals(1, viewModel.subscribed.subscriptionCount.value)
            assertEquals(0, viewModel.unsubscriptions)

            second.cancel()
            runCurrent()
            assertEquals(0, viewModel.subscribed.subscriptionCount.value)
            assertEquals(0, viewModel.collected.subscriptionCount.value)
            assertEquals(1, viewModel.unsubscriptions)
            assertTrue(viewModel.collectorsStoppedBeforeUnsubscribed)

            var vmWorkCompleted = false
            backgroundScope.launch { vmWorkCompleted = true }
            runCurrent()
            assertTrue(vmWorkCompleted)
        }

    @Test
    fun resubscribingDoesNotRetainOldCollectors() =
        runTest {
            val viewModel = TestViewModel(backgroundScope)
            val first = backgroundScope.launch { viewModel.stateFlow.collect {} }
            runCurrent()
            first.cancel()
            runCurrent()

            val second = backgroundScope.launch { viewModel.stateFlow.collect {} }
            runCurrent()
            assertEquals(1, viewModel.subscribed.subscriptionCount.value)
            assertEquals(1, viewModel.collected.subscriptionCount.value)

            viewModel.subscribed.emit(1)
            viewModel.collected.emit(1)
            runCurrent()
            assertEquals(1, viewModel.subscriptionValues)
            assertEquals(1, viewModel.collectionValues)

            second.cancel()
            runCurrent()
            assertEquals(0, viewModel.subscribed.subscriptionCount.value)
            assertEquals(0, viewModel.collected.subscriptionCount.value)
        }

    private class TestViewModel(
        scope: CoroutineScope,
    ) : AppViewModel<Unit, Int, Unit>("TestViewModel", 0, scope) {
        val subscribed = MutableSharedFlow<Int>()
        val collected = MutableSharedFlow<Int>()
        var subscriptionValues = 0
        var collectionValues = 0
        var unsubscriptions = 0
        var collectorsStoppedBeforeUnsubscribed = false

        override fun SubscriptionScope.onSubscribed() {
            subscribed.subscribe { subscriptionValues++ }
            collected.subscribe { collectionValues++ }
        }

        override suspend fun onUnsubscribed() {
            unsubscriptions++
            collectorsStoppedBeforeUnsubscribed =
                subscribed.subscriptionCount.value == 0 && collected.subscriptionCount.value == 0
        }
    }
}
