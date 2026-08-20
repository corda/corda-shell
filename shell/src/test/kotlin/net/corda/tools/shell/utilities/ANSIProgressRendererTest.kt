package net.corda.tools.shell.utilities

import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.Mockito.verifyNoInteractions
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.tools.shell.utlities.ANSIProgressRenderer
import net.corda.tools.shell.utlities.CRaSHANSIProgressRenderer
import org.apache.commons.lang3.SystemUtils
import org.assertj.core.api.Assertions.assertThat
import org.crsh.text.RenderPrintWriter
import org.fusesource.jansi.Ansi
import org.junit.Before
import org.junit.Test
import rx.Observable
import rx.subjects.PublishSubject

class ANSIProgressRendererTest {

    companion object {
        private const val INTENSITY_BOLD_ON_ASCII = "[1m"
        private const val INTENSITY_OFF_ASCII = "[22m"
        private const val INTENSITY_FAINT_ON_ASCII = "[2m"

        private const val STEP_1_LABEL = "Running step 1"
        private const val STEP_2_LABEL = "Running step 2"
        private const val STEP_3_LABEL = "Running step 3"
        private const val STEP_4_LABEL = "Running step 4"
        private const val STEP_5_LABEL = "Running step 5"

        fun stepSuccess(stepLabel: String): String {
            return if (SystemUtils.IS_OS_WINDOWS) """DONE: $stepLabel""" else """✓ $stepLabel"""
        }

        fun stepSkipped(stepLabel: String): String {
            return """  $INTENSITY_FAINT_ON_ASCII$stepLabel$INTENSITY_OFF_ASCII"""
        }

        fun stepActive(stepLabel: String): String {
            return if (SystemUtils.IS_OS_WINDOWS)
                """CURRENT: $INTENSITY_BOLD_ON_ASCII$stepLabel$INTENSITY_OFF_ASCII"""
            else
                """▶︎ $INTENSITY_BOLD_ON_ASCII$stepLabel$INTENSITY_OFF_ASCII"""
        }

        fun stepNotRun(stepLabel: String): String {
            return """    $stepLabel"""
        }
    }

    lateinit var printWriter: RenderPrintWriter
    lateinit var progressRenderer: ANSIProgressRenderer
    lateinit var indexSubject: PublishSubject<Int>
    lateinit var feedSubject: PublishSubject<List<Pair<Int, String>>>
    lateinit var flowProgressHandle: FlowProgressHandleImpl<*>

    @Before
    fun setup() {
        printWriter = mock()
        progressRenderer = CRaSHANSIProgressRenderer(printWriter)
        indexSubject = PublishSubject.create<Int>()
        feedSubject = PublishSubject.create<List<Pair<Int, String>>>()
        val stepsTreeIndexFeed = DataFeed<Int, Int>(0, indexSubject)
        val stepsTreeFeed = DataFeed<List<Pair<Int, String>>, List<Pair<Int, String>>>(listOf(), feedSubject)
        flowProgressHandle = FlowProgressHandleImpl(
            StateMachineRunId.createRandom(),
            openFuture<String>(),
            Observable.empty(),
            stepsTreeIndexFeed,
            stepsTreeFeed
        )
    }

    private fun checkTrackingState(captor: KArgumentCaptor<Ansi>, updates: Int, trackerState: List<String>) {
        verify(printWriter, times(updates)).print(captor.capture())
        assertThat(captor.lastValue.toString()).containsSubsequence(trackerState)
        verify(printWriter, times(updates)).flush()
    }

    @Test(timeout = 300_000)
    fun `test that steps are rendered appropriately depending on their status`() {
        progressRenderer.render(flowProgressHandle)
        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL), Pair(0, STEP_2_LABEL), Pair(0, STEP_3_LABEL)))
        // The flow is currently at step 3, while step 1 has been completed and step 2 has been skipped.
        indexSubject.onNext(0)
        indexSubject.onNext(2)

        val captor = argumentCaptor<Ansi>()
        checkTrackingState(captor, 2, listOf(stepSuccess(STEP_1_LABEL), stepSkipped(STEP_2_LABEL), stepActive(STEP_3_LABEL)))
    }

    @Test(timeout = 300_000)
    fun `changing tree causes correct steps to be marked as done`() {
        progressRenderer.render(flowProgressHandle)
        feedSubject.onNext(
            listOf(
                Pair(0, STEP_1_LABEL),
                Pair(1, STEP_2_LABEL),
                Pair(1, STEP_3_LABEL),
                Pair(0, STEP_4_LABEL),
                Pair(0, STEP_5_LABEL)
            )
        )
        indexSubject.onNext(0)
        indexSubject.onNext(1)
        indexSubject.onNext(2)

        val captor = argumentCaptor<Ansi>()
        checkTrackingState(captor, 3, listOf(stepSuccess(STEP_1_LABEL), stepSuccess(STEP_2_LABEL), stepActive(STEP_3_LABEL)))

        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL), Pair(0, STEP_4_LABEL), Pair(0, STEP_5_LABEL)))
        checkTrackingState(captor, 4, listOf(stepActive(STEP_1_LABEL), stepNotRun(STEP_4_LABEL), stepNotRun(STEP_5_LABEL)))
    }

    @Test(timeout = 300_000)
    fun `duplicate steps in different children handled correctly`() {
        val captor = argumentCaptor<Ansi>()
        progressRenderer.render(flowProgressHandle)
        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL), Pair(0, STEP_2_LABEL)))
        indexSubject.onNext(0)

        checkTrackingState(captor, 1, listOf(stepActive(STEP_1_LABEL), stepNotRun(STEP_2_LABEL)))

        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL), Pair(1, STEP_3_LABEL), Pair(0, STEP_2_LABEL), Pair(1, STEP_3_LABEL)))
        indexSubject.onNext(1)
        indexSubject.onNext(2)
        indexSubject.onNext(3)

        checkTrackingState(
            captor,
            5,
            listOf(stepSuccess(STEP_1_LABEL), stepSuccess(STEP_3_LABEL), stepSuccess(STEP_2_LABEL), stepActive(STEP_3_LABEL))
        )

        feedSubject.onNext(
            listOf(
                Pair(0, STEP_1_LABEL),
                Pair(1, STEP_3_LABEL),
                Pair(0, STEP_2_LABEL),
                Pair(1, STEP_3_LABEL),
                Pair(2, STEP_4_LABEL)
            )
        )

        checkTrackingState(
            captor,
            6,
            listOf(
                stepSuccess(STEP_1_LABEL),
                stepSuccess(STEP_3_LABEL),
                stepSuccess(STEP_2_LABEL),
                stepActive(STEP_3_LABEL),
                stepNotRun(STEP_4_LABEL)
            )
        )
    }

    @Test(timeout = 300_000)
    fun `onDone is invoked when the progress feeds complete`() {
        // Control for the test below: with both feeds present, completing them must complete the
        // renderer, because InteractiveShell.runFlowByNameFragment blocks on a latch that only the
        // onDone callback releases.
        var done = false
        progressRenderer.render(flowProgressHandle) { done = true }
        feedSubject.onNext(listOf(Pair(0, STEP_1_LABEL)))
        indexSubject.onNext(0)

        feedSubject.onCompleted()
        indexSubject.onCompleted()

        assertThat(done).isTrue()
    }

    @Test(timeout = 300_000)
    fun `flow without progress feeds never completes the renderer and prints nothing - reproduces the flow start hang`() {
        // A flow declaring `progressTracker = null` produces a FlowProgressHandle whose
        // stepsTreeFeed and stepsTreeIndexFeed are null (CordaRPCOpsImpl.startTrackedFlowDynamic).
        // renderInternal then takes its "required data is missing" branch, which:
        //   - never subscribes to anything, so done() - and with it the onDone callback - is never
        //     invoked. InteractiveShell.runFlowByNameFragment waits forever on its CountDownLatch,
        //     so `flow start` hangs in both the standalone and the embedded/SSH shell;
        //   - builds its warning into a fresh Ansi() that is never passed to printAnsi, so the
        //     hang is completely silent.
        //
        // This test documents that behaviour, so it PASSES while the bug exists. The fix (call
        // done(null) and actually print the warning in the missing-data branch) should flip it to
        // assert that onDone was invoked and the warning was printed.
        val handleWithoutFeeds = FlowProgressHandleImpl(
            StateMachineRunId.createRandom(),
            openFuture<String>(),
            Observable.empty()
        )

        var done = false
        progressRenderer.render(handleWithoutFeeds) { done = true }

        // If this assertion fails, the hang has been fixed: flip this test as described above.
        assertThat(done).isFalse()
        verifyNoInteractions(printWriter)
    }
}
